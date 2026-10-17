package com.comidadevo.financeiro;

import android.content.Context;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

public class SyncManager {
    private static final String FILE_NAME="comida-de-vo-financeiro.json";
    private final Context context; private final FinanceDb db;
    public SyncManager(Context c){context=c.getApplicationContext();db=new FinanceDb(context);}

    public void sync(GoogleSignInAccount account) throws Exception {
        if(account==null) throw new IllegalStateException("Conta Google não conectada");
        GoogleAccountCredential credential=GoogleAccountCredential.usingOAuth2(context,Collections.singleton(com.google.api.services.drive.DriveScopes.DRIVE_APPDATA));
        credential.setSelectedAccount(account.getAccount());
        Drive drive=new Drive.Builder(AndroidHttp.newCompatibleTransport(), GsonFactory.getDefaultInstance(), credential).setApplicationName("Comida de Vó - Financeiro").build();
        File remote=find(drive);
        Map<String,FinanceDb.Entry> merged=new HashMap<>();
        for(FinanceDb.Entry e:db.all()) merged.put(e.id,e);
        if(remote!=null){
            ByteArrayOutputStream out=new ByteArrayOutputStream(); drive.files().get(remote.getId()).executeMediaAndDownloadTo(out);
            JSONArray arr=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8)).optJSONArray("entries");
            if(arr!=null) for(int i=0;i<arr.length();i++){JSONObject o=arr.getJSONObject(i); FinanceDb.Entry e=fromJson(o); FinanceDb.Entry local=merged.get(e.id); if(local==null || e.updatedAt>local.updatedAt) merged.put(e.id,e);}
        }
        for(FinanceDb.Entry e:merged.values()) db.upsert(e.id,e.type,e.amount,e.category,e.date,e.description,e.updatedAt,e.deleted);
        JSONArray arr=new JSONArray(); for(FinanceDb.Entry e:db.all()) arr.put(toJson(e));
        JSONObject root=new JSONObject(); root.put("schema",1); root.put("updatedAt",System.currentTimeMillis()); root.put("entries",arr);
        ByteArrayContent media=new ByteArrayContent("application/json",root.toString().getBytes(StandardCharsets.UTF_8));
        if(remote==null){File meta=new File().setName(FILE_NAME).setParents(Collections.singletonList("appDataFolder")); drive.files().create(meta,media).setFields("id,name").execute();}
        else drive.files().update(remote.getId(),null,media).execute();
    }
    private File find(Drive drive)throws Exception{FileList list=drive.files().list().setSpaces("appDataFolder").setQ("name = '"+FILE_NAME+"' and trashed = false").setPageSize(1).setFields("files(id,name)").execute(); return list.getFiles()==null||list.getFiles().isEmpty()?null:list.getFiles().get(0);}
    private static JSONObject toJson(FinanceDb.Entry e)throws Exception{JSONObject o=new JSONObject();o.put("id",e.id);o.put("type",e.type);o.put("amount",e.amount);o.put("category",e.category);o.put("date",e.date);o.put("description",e.description);o.put("updatedAt",e.updatedAt);o.put("deleted",e.deleted);return o;}
    private static FinanceDb.Entry fromJson(JSONObject o)throws Exception{return new FinanceDb.Entry(o.getString("id"),o.getString("type"),o.getDouble("amount"),o.getString("category"),o.getString("date"),o.optString("description",""),o.getLong("updatedAt"),o.optBoolean("deleted",false));}
}
