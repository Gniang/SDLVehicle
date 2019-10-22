package com.oec.sdl.vehicle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

import com.google.gson.Gson

class ResultActivity : AppCompatActivity() {

    private var upReceiver: UpdateReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_resul)

        // SDLサービスからのデータ受信
        upReceiver = UpdateReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction("UPDATE_ACTION")
        registerReceiver(upReceiver, intentFilter)
    }


    public override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(upReceiver)
    }

    private inner class UpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras
            val json = extras!!.getString("json")
            if (json != null) {
                val result = Gson().fromJson(json, ResultData::class.java)
                val score = result.totalScore
                //if(score > 0){
                // スコア表示
                findViewById<TextView>(R.id.lbl3).text = score.toString()
                SdlService.totalData.add(score)
                val total = SdlService.totalData.Point
                findViewById<TextView>(R.id.lbl).text = total.toString()
                //}

                Log.i("debug", json)
            }

        }
    }


}
