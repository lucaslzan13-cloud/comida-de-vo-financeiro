package com.comidadevo.financeiro;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    @NonNull @Override public Result doWork(){
        GoogleSignInAccount account=GoogleSignIn.getLastSignedInAccount(getApplicationContext());
        if(account==null)return Result.success();
        try{new SyncManager(getApplicationContext()).sync(account);return Result.success();}
        catch(Exception e){return Result.retry();}
    }
    public static void schedule(Context c){
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest r=new PeriodicWorkRequest.Builder(SyncWorker.class,15,TimeUnit.MINUTES).setConstraints(constraints).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("cdv_google_sync",ExistingPeriodicWorkPolicy.KEEP,r);
    }
}
