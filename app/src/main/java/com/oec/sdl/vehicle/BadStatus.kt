package com.oec.sdl.vehicle

import java.time.LocalDateTime

class BadStatus {
    val Keisu: Double
    val Start : LocalDateTime
    val IsFaital : Boolean
    constructor(start:LocalDateTime, keisu:Double, isFatal:Boolean){
        Start = start
        Keisu = keisu
        IsFaital = isFatal
    }
}