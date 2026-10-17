package com.comidadevo.financeiro;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public class FinanceDb extends SQLiteOpenHelper {
    private static final String DB="financeiro.db";
    public FinanceDb(Context c){super(c,DB,null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE entries(id TEXT PRIMARY KEY,type TEXT NOT NULL,amount REAL NOT NULL,category TEXT NOT NULL,date TEXT NOT NULL,description TEXT,updatedAt INTEGER NOT NULL,deleted INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}
    public synchronized void upsert(String id,String type,double amount,String category,String date,String desc,long updated,boolean deleted){
        ContentValues v=new ContentValues(); v.put("id",id);v.put("type",type);v.put("amount",amount);v.put("category",category);v.put("date",date);v.put("description",desc);v.put("updatedAt",updated);v.put("deleted",deleted?1:0);
        getWritableDatabase().insertWithOnConflict("entries",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized List<Entry> all(){
        List<Entry> out=new ArrayList<>(); Cursor c=getReadableDatabase().query("entries",null,null,null,null,null,"date DESC, updatedAt DESC");
        try{while(c.moveToNext()) out.add(new Entry(c.getString(c.getColumnIndexOrThrow("id")),c.getString(c.getColumnIndexOrThrow("type")),c.getDouble(c.getColumnIndexOrThrow("amount")),c.getString(c.getColumnIndexOrThrow("category")),c.getString(c.getColumnIndexOrThrow("date")),c.getString(c.getColumnIndexOrThrow("description")),c.getLong(c.getColumnIndexOrThrow("updatedAt")),c.getInt(c.getColumnIndexOrThrow("deleted"))!=0));}finally{c.close();} return out;
    }
    public synchronized void clearAll(){getWritableDatabase().delete("entries",null,null);}
    public static class Entry { public String id,type,category,date,description; public double amount; public long updatedAt; public boolean deleted;
        public Entry(String id,String type,double amount,String category,String date,String description,long updatedAt,boolean deleted){this.id=id;this.type=type;this.amount=amount;this.category=category;this.date=date;this.description=description;this.updatedAt=updatedAt;this.deleted=deleted;}
    }
}
