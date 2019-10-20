package com.oec.sdl.vehicle;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;

public class MainActivity extends AppCompatActivity {

    private int soundNoTouch;
    private SoundPool soundPool;
    private int soundBreak;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = this;
        Intent update_service = new Intent(context , SdlService.class);
        startService(update_service);

        // オーディオ情報準備
        {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    // USAGE_MEDIA
                    // USAGE_GAME
                    .setUsage(AudioAttributes.USAGE_GAME)
                    // CONTENT_TYPE_MUSIC
                    // CONTENT_TYPE_SPEECH, etc.
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setAudioAttributes(audioAttributes)
                    // ストリーム数に応じて
                    .setMaxStreams(2)
                    .build();

            // one.wav をロードしておく
            soundNoTouch = soundPool.load(this, R.raw.notouch, 1);

            // two.wav をロードしておく
            //soundBreak = soundPool.load(this, R.raw.two, 1);

            // load が終わったか確認する場合
            soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    Log.d("debug","sampleId="+sampleId);
                    Log.d("debug","status="+status);
                }
            });
        }

        // SDLサービスからのデータ受信
        {
            UpdateReceiver  upReceiver = new UpdateReceiver ();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("UPDATE_ACTION");
            registerReceiver(upReceiver, intentFilter);
        }


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

            // debug
            String msg = extras.getString("message");
            if(msg != null){
                if("unlock".equals(msg)){
                    // play(ロードしたID, 左音量, 右音量, 優先度, ループ,再生速度)
                    soundPool.play(soundNoTouch, 1.0f, 1.0f, 0, 0, 1);
                }
                else
                {
                    ((TextView)findViewById(R.id.dbgText)).setText(msg);
                }
            }

            String json = extras.getString("json");
            if(json != null){
                ResultData result = new Gson().fromJson(json, ResultData.class);
                int score = result.getTotalScore();
                if(score > 0){
                    ((TextView)findViewById(R.id.dbgText)).setText(Integer.toString(score) + "ポイント");
                }

                Log.i("debug", json);
            }

        }
    }



}
