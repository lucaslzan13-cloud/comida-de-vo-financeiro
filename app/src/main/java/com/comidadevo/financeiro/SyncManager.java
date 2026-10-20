package com.comidadevo.financeiro;

import android.content.Context;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public class SyncManager {
    private static final String FOLDER_NAME="Comida de Vó - Financeiro";
    private static final String FILE_NAME="comida-de-vo-financeiro.json";
    private static final String FOLDER_MIME="application/vnd.google-apps.folder";
    private final Context context;
    private final FinanceDb db;

    public SyncManager(Context c){context=c.getApplicationContext();db=new FinanceDb(context);}

    public void sync(String accessToken) throws Exception {
        if(accessToken==null || accessToken.trim().isEmpty())
            throw new IllegalStateException("Token Google inválido");

        GoogleCredential credential=new GoogleCredential().setAccessToken(accessToken);
        Drive drive=new Drive.Builder(
                AndroidHttp.newCompatibleTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Comida de Vó - Financeiro")
                .build();

        File folder=findFolder(drive);
        if(folder==null){
            File meta=new File().setName(FOLDER_NAME).setMimeType(FOLDER_MIME)
                    .setParents(Collections.singletonList("root"));
            folder=drive.files().create(meta).setFields("id,name,parents").execute();
        }

        File remote=findBackupFile(drive,folder.getId());
        Map<String,FinanceDb.Entry> merged=new HashMap<>();
        for(FinanceDb.Entry e:db.all()) merged.put(e.id,e);

        if(remote!=null){
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            drive.files().get(remote.getId()).executeMediaAndDownloadTo(out);
            JSONArray arr=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8))
                    .optJSONArray("entries");
            if(arr!=null){
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.getJSONObject(i);
                    FinanceDb.Entry e=fromJson(o);
                    FinanceDb.Entry local=merged.get(e.id);
                    if(local==null || e.updatedAt>local.updatedAt) merged.put(e.id,e);
                }
            }
        }

        for(FinanceDb.Entry e:merged.values()){
            db.upsert(e.id,e.type,e.amount,e.category,e.date,e.description,e.updatedAt,e.deleted);
        }

        JSONArray arr=new JSONArray();
        for(FinanceDb.Entry e:db.all()) arr.put(toJson(e));
        JSONObject root=new JSONObject();
        root.put("schema",2);
        root.put("updatedAt",System.currentTimeMillis());
        root.put("entries",arr);

        ByteArrayContent media=new ByteArrayContent(
                "application/json",root.toString().getBytes(StandardCharsets.UTF_8));

        if(remote==null){
            File meta=new File().setName(FILE_NAME)
                    .setMimeType("application/json")
                    .setParents(Collections.singletonList(folder.getId()));
            drive.files().create(meta,media).setFields("id,name,parents").execute();
        }else{
            drive.files().update(remote.getId(),null,media).setFields("id,name,parents").execute();
        }
    }

    private File findFolder(Drive drive)throws Exception{
        String q="name = '"+FOLDER_NAME.replace("'","\\'")+"' and mimeType = '"+FOLDER_MIME+"' and trashed = false";
        FileList list=drive.files().list()
                .setQ(q)
                .setSpaces("drive")
                .setPageSize(10)
                .setFields("files(id,name,mimeType,parents)")
                .execute();
        if(list.getFiles()==null || list.getFiles().isEmpty()) return null;
        for(File f:list.getFiles()){
            if(f.getParents()==null || f.getParents().contains("root")) return f;
        }
        return list.getFiles().get(0);
    }

    private File findBackupFile(Drive drive,String folderId)throws Exception{
        String q="name = '"+FILE_NAME.replace("'","\\'")+"' and '"+folderId+"' in parents and trashed = false";
        FileList list=drive.files().list()
                .setQ(q)
                .setSpaces("drive")
                .setPageSize(1)
                .setFields("files(id,name,parents)")
                .execute();
        return list.getFiles()==null || list.getFiles().isEmpty()?null:list.getFiles().get(0);
    }

    private static JSONObject toJson(FinanceDb.Entry e)throws Exception{
        JSONObject o=new JSONObject();
        o.put("id",e.id);o.put("type",e.type);o.put("amount",e.amount);
        o.put("category",e.category);o.put("date",e.date);
        o.put("description",e.description==null?"":e.description);o.put("updatedAt",e.updatedAt);
        o.put("deleted",e.deleted);
        return o;
    }

    private static FinanceDb.Entry fromJson(JSONObject o)throws Exception{
        return new FinanceDb.Entry(
                o.getString("id"),o.getString("type"),o.getDouble("amount"),
                o.getString("category"),o.getString("date"),
                o.optString("description",""),o.getLong("updatedAt"),
                o.optBoolean("deleted",false));
    }
}
