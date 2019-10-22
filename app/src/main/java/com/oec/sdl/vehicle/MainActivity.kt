package com.oec.sdl.vehicle

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.view.MotionEvent
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import oupson.apng.ApngAnimator

class MainActivity : AppCompatActivity() {

    private var soundPool: SoundPool? = null
    private var soundNoTouch: Int = 0
    private var soundNyaa: Int = 0
    private var soundOhayou: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val context = this
        val updateService = Intent(context, SdlService::class.java)
        startService(updateService)

        // オーディオ情報準備
        run {
            val audioAttributes = AudioAttributes.Builder()
                    // USAGE_MEDIA
                    // USAGE_GAME
                    .setUsage(AudioAttributes.USAGE_GAME)
                    // CONTENT_TYPE_MUSIC
                    // CONTENT_TYPE_SPEECH, etc.
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

            soundPool = SoundPool.Builder()
                    .setAudioAttributes(audioAttributes)
                    // ストリーム数に応じて
                    .setMaxStreams(2)
                    .build()

            // サウンドメディア をロードしておく
            soundNoTouch = soundPool!!.load(this, R.raw.notouch, 1)
            soundNyaa = soundPool!!.load(this, R.raw.nyaa, 1)
            soundOhayou = soundPool!!.load(this, R.raw.oyahyou, 1)

            // load が終わったか確認する場合
            soundPool!!.setOnLoadCompleteListener { soundPool, sampleId, status ->
                Log.d("debug", "sampleId=$sampleId")
                Log.d("debug", "status=$status")
            }
        }

        // SDLサービスからのデータ受信
        run {
            val upReceiver = UpdateReceiver()
            val intentFilter = IntentFilter()
            intentFilter.addAction("UPDATE_ACTION")
            registerReceiver(upReceiver, intentFilter)
        }


        //If we are connected to a module we want to start our SdlService
        if (BuildConfig.TRANSPORT == "MULTI" || BuildConfig.TRANSPORT == "MULTI_HB") {
            //SdlReceiver.queryForConnectedService(this);
            val sdlServiceIntent = Intent(this, SdlService::class.java) // used for TCP
            startService(sdlServiceIntent)
        } else if (BuildConfig.TRANSPORT == "TCP") {
            val proxyIntent = Intent(this, SdlService::class.java)
            startService(proxyIntent)
        }

        // アニメーションpngに差し替え
        val catImg = findViewById<ImageView>(R.id.cat);
        val animator = ApngAnimator(context).loadInto(catImg)
        animator.load(R.raw.cat_idle_animated)
        animator.isApng = true

        catImg.setOnTouchListener(View.OnTouchListener { v, event ->
            soundPool!!.play(soundNyaa, 1.0f, 1.0f, 0, 0, 1f)
            return@OnTouchListener true
        })


        // スコア表示
        findViewById<TextView>(R.id.lbl5).text = SdlService.totalData.Point.toString()

        // ショップボタン
        findViewById<Button>(R.id.btn3).setOnClickListener {
            val intent = Intent(application, ShopActivity::class.java)
            startActivity(intent)
        }


    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("TouchEvent", "X:" + event.x + ",Y:" + event.y)


        (findViewById<View>(R.id.lbl5) as TextView).text = Integer.toString(SdlService.totalData.Point)
        return true
    }

    protected inner class UpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras

            // debug
            val msg = extras!!.getString("message")
            if (msg != null) {
                if ("unlock" == msg) {
                    // play(ロードしたID, 左音量, 右音量, 優先度, ループ,再生速度)
                    soundPool!!.play(soundNoTouch, 1.0f, 1.0f, 0, 0, 1f)
                } else {
                    (findViewById<View>(R.id.dbgText) as TextView).text = msg
                }
            }

            (findViewById<View>(R.id.lbl5) as TextView).text = Integer.toString(SdlService.totalData.Point)

        }
    }


}
