package com.oec.sdl.vehicle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = this;
        Intent update_service = new Intent(context , SdlService.class);
        startService(update_service);

        UpdateReceiver  upReceiver = new UpdateReceiver ();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("UPDATE_ACTION");
        registerReceiver(upReceiver, intentFilter);


        //If we are connected to a module we want to start our SdlService
        if(BuildConfig.TRANSPORT.equals("MULTI") || BuildConfig.TRANSPORT.equals("MULTI_HB")) {
            //SdlReceiver.queryForConnectedService(this);
            Intent sdlServiceIntent = new Intent(this, SdlService.class); // used for TCP
            startService(sdlServiceIntent);
        }else if(BuildConfig.TRANSPORT.equals("TCP")) {
            Intent proxyIntent = new Intent(this, SdlService.class);
            startService(proxyIntent);
        }



        Button sendButton = findViewById(R.id.btn3);
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            Intent intent = new Intent(getApplication(), ShopActivity.class);
                startActivity(intent);
            }
        });
    }

    protected class UpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent){
            Bundle extras = intent.getExtras();
            String msg = extras.getString("message");
            ((TextView)findViewById(R.id.dbgText)).setText(msg);
        }
    }
}
