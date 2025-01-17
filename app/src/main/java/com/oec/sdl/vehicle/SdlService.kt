package com.oec.sdl.vehicle

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log

import com.google.gson.Gson
import com.smartdevicelink.managers.SdlManager
import com.smartdevicelink.managers.SdlManagerListener
import com.smartdevicelink.managers.file.filetypes.SdlArtwork
import com.smartdevicelink.managers.permission.PermissionElement
import com.smartdevicelink.protocol.enums.FunctionID
import com.smartdevicelink.proxy.RPCNotification
import com.smartdevicelink.proxy.RPCResponse
import com.smartdevicelink.proxy.rpc.DisplayCapabilities
import com.smartdevicelink.proxy.rpc.GetVehicleData
import com.smartdevicelink.proxy.rpc.GetVehicleDataResponse
import com.smartdevicelink.proxy.rpc.OnHMIStatus
import com.smartdevicelink.proxy.rpc.OnVehicleData
import com.smartdevicelink.proxy.rpc.SubscribeVehicleData
import com.smartdevicelink.proxy.rpc.UnsubscribeVehicleData
import com.smartdevicelink.proxy.rpc.enums.AppHMIType
import com.smartdevicelink.proxy.rpc.enums.ElectronicParkBrakeStatus
import com.smartdevicelink.proxy.rpc.enums.FileType
import com.smartdevicelink.proxy.rpc.enums.HMILevel
import com.smartdevicelink.proxy.rpc.enums.SystemCapabilityType
import com.smartdevicelink.proxy.rpc.listeners.OnRPCNotificationListener
import com.smartdevicelink.proxy.rpc.listeners.OnRPCResponseListener
import com.smartdevicelink.transport.BaseTransportConfig
import com.smartdevicelink.transport.MultiplexTransportConfig
import com.smartdevicelink.transport.TCPTransportConfig

import java.time.Duration
import java.time.LocalDateTime

//HTTP通信
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.*

@TargetApi(Build.VERSION_CODES.O)
public class SdlService : Service() {

    private lateinit var listener: SdlManagerListener

    private val penaltyTimeSec = 10L;

    // variable to create and call functions of the SyncProxy
    private var sdlManager: SdlManager? = null


    // 走行結果
    private var resultData = ResultData()
    // パーキングブレーキ状態
    private var parkBraak = ElectronicParkBrakeStatus.CLOSED
    // 前回時刻
    private var prevDate = LocalDateTime.now()

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterForeground()
        }

        // ロック解除時
        run{
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_USER_PRESENT)
            registerReceiver(PhoneUnlockedReceiver(), filter)
        }
    }

    // Helper method to let the service enter foreground mode
    @SuppressLint("NewApi")
    fun enterForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(APP_ID, "SdlService", NotificationManager.IMPORTANCE_DEFAULT)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel)
                val serviceNotification = Notification.Builder(this, channel.id)
                        .setContentTitle("Connected through SDL")
                        .setSmallIcon(R.drawable.ic_sdl)
                        .build()
                startForeground(FOREGROUND_SERVICE_ID, serviceNotification)
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startProxy()
        return Service.START_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true)
        }

        sdlManager?.dispose()

        super.onDestroy()
    }

    private fun startProxy() {

        if (sdlManager == null) {
            Log.i(TAG, "Starting SDL Proxy")

            var transport: BaseTransportConfig? = null
            if (BuildConfig.TRANSPORT == "MULTI") {
                val securityLevel: Int
                if (BuildConfig.SECURITY == "HIGH") {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_HIGH
                } else if (BuildConfig.SECURITY == "MED") {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_MED
                } else if (BuildConfig.SECURITY == "LOW") {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_LOW
                } else {
                    securityLevel = MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF
                }
                transport = MultiplexTransportConfig(this, APP_ID, securityLevel)
            } else if (BuildConfig.TRANSPORT == "TCP") {
                transport = TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true)
            } else if (BuildConfig.TRANSPORT == "MULTI_HB") {
                val mtc = MultiplexTransportConfig(this, APP_ID, MultiplexTransportConfig.FLAG_MULTI_SECURITY_OFF)
                mtc.setRequiresHighBandwidth(true)
                transport = mtc
            }

            // The app type to be used
            val appType = Vector<AppHMIType>()
            appType.add(AppHMIType.MEDIA)

            // The manager listener helps you know when certain events that pertain to the SDL Manager happen
            // Here we will listen for ON_HMI_STATUS and ON_COMMAND notifications
            val listener = object : SdlManagerListener {



                override fun onStart() {
                    // HMI Status Listener
                    sdlManager!!.addOnRPCNotificationListener(
                            FunctionID.ON_HMI_STATUS,
                            object : OnRPCNotificationListener()
                    {
                        override fun onNotified(notification: RPCNotification) {

                            val status = notification as OnHMIStatus
                            if (status.hmiLevel == HMILevel.HMI_FULL
                                    && notification.firstRun!!) {

                                checkTemplateType()
                                checkPermission()
                                setDisplayDefault()
                            }
                        }
                    })

                    //これをすると定期的にデータが取得可能
                    sdlManager!!.addOnRPCNotificationListener(FunctionID.ON_VEHICLE_DATA, object : OnRPCNotificationListener() {
                        override fun onNotified(notification: RPCNotification) {
                            val onVehicleDataNotification = notification as OnVehicleData

                            sdlManager!!.screenManager.beginTransaction()

                            // パーキングブレーキがはいると走行結果を画面に通達する。
                            val newPark = onVehicleDataNotification.electronicParkBrakeStatus
                            if(newPark != null){
                                Log.i(TAG, "ParkBreak:$newPark")
                                if (parkBraak != newPark && newPark == ElectronicParkBrakeStatus.CLOSED) {

                                    // 画面表示
                                    val intent = Intent(serviceContext(), ResultActivity::class.java)
                                    intent.flags = (Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    application.startActivity(intent)

                                    // スコア通達
                                    sendBroadCast("json", Gson().toJson(resultData))
                                    resultData = createNewResultData(resultData)
                                    Log.i(TAG, "sendResultScore")
                                }
                                parkBraak = newPark
                            }

                            // スコア計算
                            var speed = onVehicleDataNotification.speed
                            if(speed != null){
                                Log.i(TAG, "Speed:$speed")
                                calcScore(speed)
                            }

                            sdlManager!!.screenManager.commit { success ->
                                if (success) {
                                    Log.i(TAG, "change successful")
                                }
                            }
                        }
                    })
                }


                override fun onDestroy() {
                    val unsubscribeRequest = UnsubscribeVehicleData()
                    unsubscribeRequest.rpm = true
                    unsubscribeRequest.prndl = true
                    unsubscribeRequest.speed = true
                    unsubscribeRequest.electronicParkBrakeStatus = true    //パーキングブレーキの状態
                    unsubscribeRequest.onRPCResponseListener = object : OnRPCResponseListener() {
                        override fun onResponse(correlationId: Int, response: RPCResponse) {
                            if (response.success!!) {
                                Log.i("SdlService", "Successfully unsubscribed to vehicle data.")
                            } else {
                                Log.i("SdlService", "Request to unsubscribe to vehicle data was rejected.")
                            }
                        }
                    }
                    sdlManager!!.sendRPC(unsubscribeRequest)

                    this@SdlService.stopSelf()
                }

                override fun onError(info: String, e: Exception) {}
            }

            // Create App Icon, this is set in the SdlManager builder
            val appIcon = SdlArtwork(ICON_FILENAME, FileType.GRAPHIC_PNG, R.mipmap.ic_launcher, true)

            // The manager builder sets options for your session
            val builder = SdlManager.Builder(this, APP_ID, APP_NAME, listener)
            builder.setAppTypes(appType)
            //builder.setTransportType(transport);
            builder.setTransportType(TCPTransportConfig(TCP_PORT, DEV_MACHINE_IP_ADDRESS, true))
            builder.setAppIcon(appIcon)
            sdlManager = builder.build()
            sdlManager!!.start()
        }
    }


	/**
	 * JSON形式でPOSTしたい場合　
	 */
    private fun doJsonPost( url :String, jsonString:String, headers:List<Pair<String,String>> ) {
        val mediaTypeJson = "application/json; charset=utf-8".toMediaTypeOrNull();

        val requestBody = jsonString.toRequestBody(mediaTypeJson);
		val reqBuilder= Request.Builder()

        // ヘッダ付与
        headers.forEach { x -> reqBuilder.header(x.first, x.second)}
        val request = reqBuilder
				.url(url)
				.post(requestBody)//POST指定
                .build();

        val client = OkHttpClient.Builder()
				    .build()
		//同期呼び出し
		val response = client.newCall(request).execute()
        Log.i(TAG, "Res:" + response.body?.string())
	}

    private fun serviceContext():Context{
        return this
    }

    private fun createNewResultData(resultData: ResultData): ResultData {
        val now = LocalDateTime.now();
        val newData = ResultData()
        // ペナの引継ぎ
        newData.BadStatus.addAll(
                resultData.BadStatus.filter { x -> x.start > now  }
        )
        return  newData
    }

    // ポイント計算
    @TargetApi(Build.VERSION_CODES.O)
    private fun calcScore(speedKph:Double) {
        val newDate = LocalDateTime.now()
        val d = Duration.between(prevDate, newDate)


        val totalHour = d.seconds / (60.0 * 60.0)
        val distanceMeter = speedKph * totalHour * 1000.0

        // 乗算ペナルティ係数を求める
        val badStsCount = resultData.BadStatus
                .stream()
                .filter{x->x.start > prevDate}
                .count()

        var penalty = 1.0
        if(badStsCount > 0){
            penalty = resultData.BadStatus
                    .stream()
                    .filter{x->x.start > prevDate}
                    .map { x -> x.keisu}
                    .reduce { x,y -> (x * y)}.get()
        }

        Log.i(TAG, "BadStatus:$penalty    count:$badStsCount"  )

        // スコア設定
        val point = (distanceMeter * penalty).toInt()
        resultData.Scores.add(point);
        resultData.Times.add(newDate)
        prevDate = newDate

        sendKintoneScore(newDate, point)
    }


    /**
     * キントーンにスコア送信
     */
    @TargetApi(Build.VERSION_CODES.O)
    private fun sendKintoneScore(date:LocalDateTime, point:Int) {

        try{
            var utcDate = date.plusHours(-9);
            val formatDate = String.format("%02d-%02d-%02dT%02d:%02d:%02dZ",
                    utcDate.year,utcDate.monthValue,utcDate.dayOfMonth,
                    utcDate.hour,utcDate.minute,utcDate.second);
            val data =
                    """{
                    "app": 5,
                    "record": {
                    "f_datetime": {
                        "value": "$formatDate"
                        },
                    "f_nekopo": {
                        "value": "$point"
                        },
                    "f_memo": {
                        "value": ""
                        }
                    }
                }
                """


            val headers :List<Pair<String,String>> = listOf(
                    "X-Cybozu-API-Token" to KINTONE_API_TOKEN,
                    "Content-Type" to "Content-Type: application/json"
            )

            doJsonPost("https://sdlhack2019neko.cybozu.com/k/v1/record.json?app=5&id=1",
                    data,
                    headers)

        }catch (e:Exception){
            e.stackTrace
        }
    }

    /**
     * ペナ付与
     */
    private fun addPenalty(penalty:Double, isFatal:Boolean) {

        var last:LocalDateTime = LocalDateTime.now()
        var badStCnt = resultData.BadStatus
                .stream()
                .filter{x->x.isFatal}
                .count()
        if(isFatal && badStCnt > 0){
            last =  resultData.BadStatus
                    .stream()
                    .filter{x->x.isFatal}.map { x->x.start  }
                    .max{x,y-> x.compareTo(y) }.get();
        }

        resultData.BadStatus.add(
                BadStatus(last.plusSeconds(penaltyTimeSec), penalty, isFatal)
        )
    }

    /**
     * インテントでメッセージ送信
     */
    private fun sendBroadCast(key: String, message: String) {
        val broadcastIntent = Intent()
        broadcastIntent.putExtra(key, message)
        broadcastIntent.action = "UPDATE_ACTION"
        baseContext.sendBroadcast(broadcastIntent)

    }

    /**
     * 一度だけの情報受信
     */
    private fun setOnTimeSpeedResponse() {

        val vdRequest = GetVehicleData()
        vdRequest.speed = true
        vdRequest.onRPCResponseListener = object : OnRPCResponseListener() {
            override fun onResponse(correlationId: Int, response: RPCResponse) {
                if (response.success!!) {
                    val speed = (response as GetVehicleDataResponse).speed
                    changeSpeedTextField(speed!!)

                } else {
                    Log.i("SdlService", "GetVehicleData was rejected.")
                }
            }
        }
        sdlManager!!.sendRPC(vdRequest)
    }

    /**
     * Speed情報の往診
     * @param speed
     */
    private fun changeSpeedTextField(speed: Double) {
        sdlManager!!.screenManager.beginTransaction()

        //テキストを登録する場合
        sdlManager!!.screenManager.textField3 = "Speed: $speed"

        sdlManager!!.screenManager.commit { success ->
            if (success) {
                Log.i(TAG, "change successful")
            }
        }
    }

    /**
     * 利用可能なテンプレートをチェックする
     */
    private fun checkTemplateType() {

        val result = sdlManager!!.systemCapabilityManager.getCapability(SystemCapabilityType.DISPLAY)
        if (result is DisplayCapabilities) {
            val templates = result.templatesAvailable

            Log.i("Templete", templates.toString())

        }
    }

    /**
     * 利用する項目が利用可能かどうか
     */
    private fun checkPermission() {
        val permissionElements = ArrayList<PermissionElement>()

        //チェックを行う項目
        val keys = ArrayList<String>()
        keys.add(GetVehicleData.KEY_RPM)
        keys.add(GetVehicleData.KEY_SPEED)
        keys.add(GetVehicleData.KEY_PRNDL)
        permissionElements.add(PermissionElement(FunctionID.GET_VEHICLE_DATA, keys))

        val status = sdlManager!!.permissionManager.getStatusOfPermissions(permissionElements)

        //すべてが許可されているかどうか
        Log.i("Permission", "Allowed:" + status[FunctionID.GET_VEHICLE_DATA]!!.isRPCAllowed)

        //各項目ごとも可能
        Log.i("Permission", "KEY_RPM　Allowed:" + status[FunctionID.GET_VEHICLE_DATA]!!.allowedParameters[GetVehicleData.KEY_RPM]!!)

    }

    /**
     * DEFULTテンプレートのサンプル
     */
    private fun setDisplayDefault() {

        sdlManager!!.screenManager.beginTransaction()

        //テキストを登録する場合
        sdlManager!!.screenManager.textField1 = "RPM: None"
        sdlManager!!.screenManager.textField2 = "ParkBrake: None"
        sdlManager!!.screenManager.textField3 = "Speed: None"

        //画像を登録する
        val artwork = SdlArtwork("clap.png", FileType.GRAPHIC_PNG, R.drawable.goods_nekofood, true)

        sdlManager!!.screenManager.primaryGraphic = artwork
        sdlManager!!.screenManager.commit { success ->
            if (success) {
                //定期受信用のデータを設定する
                val subscribeRequest = SubscribeVehicleData()
                subscribeRequest.rpm = true                          //エンジン回転数
                subscribeRequest.prndl = true                        //シフトレーバの状態
                subscribeRequest.speed = true
                subscribeRequest.electronicParkBrakeStatus = true    //パーキングブレーキの状態
                subscribeRequest.onRPCResponseListener = object : OnRPCResponseListener() {
                    override fun onResponse(correlationId: Int, response: RPCResponse) {
                        if (response.success!!) {
                            Log.i("SdlService", "Successfully subscribed to vehicle data.")
                        } else {
                            Log.i("SdlService", "Request to subscribe to vehicle data was rejected.")
                        }
                    }
                }
                sdlManager!!.sendRPC(subscribeRequest)

            }
        }
    }

    private inner class PhoneUnlockedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {

                // パーキングブレーキが入っていないときは操作を通知
                if(parkBraak != ElectronicParkBrakeStatus.CLOSED){
                    addPenalty(0.05, true)
                    sendBroadCast("message", "unlock")
                }
                Log.i("debug", "Unlocked")

            } else if (intent.action == Intent.ACTION_SCREEN_OFF) {
                Log.d("debug", "Screen OFF")
            }
        }
    }

    public companion object {


        private val TAG = "SDL Service"

        private val APP_NAME = "SDL Display"
        private val APP_ID = "8678309"

        private val ICON_FILENAME = "hello_sdl_icon.png"

        private val FOREGROUND_SERVICE_ID = 111

        // TCP/IP transport config
        // The default port is 12345
        // The IP is of the machine that is running SDL Core
        // マンティコア起動セッションごとにポートは書き換え
        private val TCP_PORT = 14989
        private val DEV_MACHINE_IP_ADDRESS = "m.sdl.tools"

        private val KINTONE_API_TOKEN = "xxxx<Slackからコピペして>"


        @JvmField val totalData:TotalData = TotalData()

    }

}
