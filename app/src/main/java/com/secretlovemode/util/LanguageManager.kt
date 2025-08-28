package com.secretlovemode.util

import android.content.Context
import android.content.SharedPreferences

object LanguageManager {
    private const val PREF_NAME = "language_settings"
    private const val KEY_LANGUAGE = "selected_language"

    fun setLanguage(context: Context, languageCode: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "ja") ?: "ja" // 기본값은 일본어
    }

    fun isLanguageSelected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_LANGUAGE)
    }

    fun clearLanguageSelection(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_LANGUAGE).apply()
    }

    /**
     * 현재 선택된 언어에 따라 적절한 시나리오 파일명을 반환합니다.
     */
    fun getScenarioFileName(context: Context, baseFileName: String): String {
        val language = getLanguage(context)
        return when (language) {
            "en" -> "${baseFileName}_en.json"
            "ja" -> "$baseFileName.json"
            else -> "$baseFileName.json"
        }
    }

    /**
     * 현재 선택된 언어에 따라 적절한 프롬프트 파일명을 반환합니다.
     */
    fun getPromptFileName(context: Context, baseFileName: String): String {
        val language = getLanguage(context)
        return when (language) {
            "en" -> "${baseFileName}_en.txt"
            "ja" -> "$baseFileName.txt"
            else -> "$baseFileName.txt"
        }
    }

    /**
     * 현재 언어에 따른 UI 텍스트를 반환합니다.
     */
    fun getText(context: Context, key: String): String {
        val language = getLanguage(context)
        return when (language) {
            "en" -> getEnglishText(key)
            "ja" -> getJapaneseText(key)
            else -> getJapaneseText(key)
        }
    }

    private fun getJapaneseText(key: String): String {
        return when (key) {
            // Game UI
            "current_scenario" -> "現在のシナリオ"
            "thinking" -> "思考中…"
            "loading" -> "読み込み中..."
            "conversation_history" -> "対話履歴"
            "close" -> "閉じる"
            "no_history" -> "対話履歴がありません。"
            "confess" -> "告白する"
            "game_over" -> "GAME OVER"
            "game_clear" -> "GAME CLEAR"
            "section_transition" -> "セクション転換メッセージ"
            
            // Main Activity UI
            "player_name_label" -> "プレイヤー名を入力してください："
            "name_hint" -> "名前"
            "confirm" -> "確認"
            "select_model_label" -> "モデルファイルを選択してください："
            "select_model_file" -> "モデルファイルを選択"
            "model_selected" -> "選択されたファイル："
            "clear_selection" -> "選択をクリア"
            "start_game" -> "ゲーム開始"
            "model_load_failed" -> "モデルの読み込みに失敗しました。"
            "no_model_selected" -> "モデルファイルが選択されていません。"
            "not_selected" -> "未選択"
            "enter_player_name" -> "プレイヤー名を入力してください。"
            else -> key
        }
    }

    private fun getEnglishText(key: String): String {
        return when (key) {
            // Game UI
            "current_scenario" -> "Current Scenario"
            "thinking" -> "Thinking..."
            "loading" -> "Loading..."
            "conversation_history" -> "Conversation History"
            "close" -> "Close"
            "no_history" -> "No conversation history."
            "confess" -> "Confess"
            "game_over" -> "GAME OVER"
            "game_clear" -> "GAME CLEAR"
            "section_transition" -> "Section Transition Message"
            
            // Main Activity UI
            "player_name_label" -> "Please enter your player name:"
            "name_hint" -> "Name"
            "confirm" -> "Confirm"
            "select_model_label" -> "Please select a model file:"
            "select_model_file" -> "Select Model File"
            "model_selected" -> "Selected file:"
            "clear_selection" -> "Clear Selection"
            "start_game" -> "Start Game"
            "model_load_failed" -> "Failed to load model."
            "no_model_selected" -> "No model file selected."
            "not_selected" -> "Not Selected"
            "enter_player_name" -> "Please enter your player name."
            else -> key
        }
    }
}