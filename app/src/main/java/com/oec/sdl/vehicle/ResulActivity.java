package com.oec.sdl.vehicle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.gson.Gson;

public class ResulActivity extends AppCompatActivity {

    private UpdateReceiver upReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resul);

        // SDLサービスからのデータ受信
        if(upReceiver == null)
        {
            upReceiver = new UpdateReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("UPDATE_ACTION");
            registerReceiver(upReceiver, intentFilter);
        }
    }


    protected class UpdateReceiver extends BroadcastReceiver {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onReceive(Context context, Intent intent){
            Bundle extras = intent.getExtras();
            String json = extras.getString("json");
            if(json != null){
                ResultData result = new Gson().fromJson(json, ResultData.class);
                int score = result.getTotalScore();
                //if(score > 0){
                    // スコア表示
                    ((TextView)findViewById(R.id.lbl3)).setText(Integer.toString(score));
                    SdlService.totalData.add(score);
                    int total = SdlService.totalData.getPoint();
                    ((TextView)findViewById(R.id.lbl)).setText(Integer.toString(total));
                //}

                Log.i("debug", json);
            }

        }
    }


}
