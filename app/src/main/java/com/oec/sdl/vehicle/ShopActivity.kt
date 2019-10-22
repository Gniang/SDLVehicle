package com.oec.sdl.vehicle

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ShopActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shop)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.


        findViewById<TextView>(R.id.textView8).text = SdlService.totalData.Point.toString()
    }

}
