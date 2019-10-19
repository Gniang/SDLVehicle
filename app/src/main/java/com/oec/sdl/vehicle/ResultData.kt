package com.oec.sdl.vehicle

import java.time.LocalDateTime
import java.util.*

class ResultData {
    // 良評価メッセージ
    val Positives: ArrayList<String> = ArrayList<String>()
    // 悪評価メッセージ
    val Negatives: ArrayList<String> = ArrayList<String>()
    // 区間ごとのスコア
    val Scores: ArrayList<Int> = ArrayList<Int>()
    // スコアごとの時刻
    val Times: ArrayList<LocalDateTime> = ArrayList<LocalDateTime>()

    // ペナルティ
    val BadStatus:ArrayList<BadStatus> = ArrayList<BadStatus>();

    val totalScore: Int
        get() = Scores.stream().mapToInt { x -> x }.sum();

}
