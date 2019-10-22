package com.oec.sdl.vehicle

import java.time.LocalDateTime

class BadStatus {
    /** ペナルティ係数 基本値ｘペナルティ＝スコアとなる */
    val keisu: Double
    /** ペナルティ期間（上限） */
    val start : LocalDateTime
    /** 重過失か？…ペナルティの加算方法が変化する */
    val isFatal : Boolean
    constructor(start:LocalDateTime, keisu:Double, isFatal:Boolean){
        this.start = start
        this.keisu = keisu
        this.isFatal = isFatal
    }
}