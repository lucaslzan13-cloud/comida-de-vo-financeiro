package com.comidadevo.financeiro;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import com.google.api.services.drive.DriveScopes;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SyncWorker extends Worker {
    public SyncWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}

    @NonNull @Override public Result doWork(){
        try{
            List<Scope> scopes=Collections.singletonList(new Scope(DriveScopes.DRIVE_FILE));
            AuthorizationRequest request=AuthorizationRequest.builder().setRequestedScopes(scopes).build();
            AuthorizationResult result=Tasks.await(Identity.getAuthorizationClient(getApplicationContext()).authorize(request));
            if(result==null || result.hasResolution() || result.getAccessToken()==null || result.getAccessToken().isEmpty()){
                // A user interaction is required. The next foreground sync can complete it.
                return Result.success();
            }
            new SyncManager(getApplicationContext()).sync(result.getAccessToken());
            return Result.success();
        }catch(Exception e){return Result.retry();}
    }

    public static void schedule(Context c){
        Constraints constraints=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest r=new PeriodicWorkRequest.Builder(SyncWorker.class,15,TimeUnit.MINUTES).setConstraints(constraints).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("cdv_google_sync",ExistingPeriodicWorkPolicy.KEEP,r);
    }
}
