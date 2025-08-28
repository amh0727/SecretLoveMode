package com.secretlovemode.ui.ranking

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.secretlovemode.R
import com.secretlovemode.data.model.GameResult
import com.secretlovemode.data.repository.GameResultManager
import com.secretlovemode.ui.main.MainActivity
import com.secretlovemode.ui.game.GameActivity
import com.secretlovemode.util.LanguageManager

class RankingActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_CURRENT_RESULT = "current_result"
        const val EXTRA_PLAYER_NAME = "player_name"
        const val EXTRA_FINAL_AFFINITY = "final_affinity"
        const val EXTRA_CONFESSION_SUCCESS = "confession_success"
        const val EXTRA_GAME_END_TYPE = "game_end_type"
    }
    
    private lateinit var tvRankingTitle: TextView
    private lateinit var tvRankingSubtitle: TextView
    private lateinit var tvCurrentPlayerName: TextView
    private lateinit var tvCurrentAffinity: TextView
    private lateinit var tvCurrentResult: TextView
    private lateinit var rvRanking: RecyclerView
    private lateinit var tvNoResults: TextView
    private lateinit var btnPlayAgain: Button
    private lateinit var btnMainMenu: Button
    
    private lateinit var rankingAdapter: RankingAdapter
    private var currentResult: GameResult? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force light mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        
        setContentView(R.layout.activity_ranking)
        
        initializeViews()
        initializeMultilingualUI()
        setupRecyclerView()
        loadCurrentResult()
        loadRankingData()
        setupClickListeners()
    }
    
    private fun initializeViews() {
        tvRankingTitle = findViewById(R.id.tvRankingTitle)
        tvRankingSubtitle = findViewById(R.id.tvRankingSubtitle)
        tvCurrentPlayerName = findViewById(R.id.tvCurrentPlayerName)
        tvCurrentAffinity = findViewById(R.id.tvCurrentAffinity)
        tvCurrentResult = findViewById(R.id.tvCurrentResult)
        rvRanking = findViewById(R.id.rvRanking)
        tvNoResults = findViewById(R.id.tvNoResults)
        btnPlayAgain = findViewById(R.id.btnPlayAgain)
        btnMainMenu = findViewById(R.id.btnMainMenu)
    }
    
    private fun initializeMultilingualUI() {
        val language = LanguageManager.getLanguage(this)
        
        if (language == "en") {
            tvRankingTitle.text = "Game Results Ranking"
            tvRankingSubtitle.text = "Check your romance skills!"
            btnPlayAgain.text = "Play Again"
            btnMainMenu.text = "Main Menu"
            tvNoResults.text = "No records yet"
            
            // Update labels
            findViewById<TextView>(R.id.tvCurrentGameLabel)?.text = "This Game"
            findViewById<TextView>(R.id.tvPlayerLabel)?.text = "Player"
            findViewById<TextView>(R.id.tvAffinityLabel)?.text = "Final Affinity"
            findViewById<TextView>(R.id.tvResultLabel)?.text = "Result"
            findViewById<TextView>(R.id.tvAllRecordsLabel)?.text = "All Records Ranking"
        } else {
            tvRankingTitle.text = "ゲーム結果ランキング"
            tvRankingSubtitle.text = "あなたの恋愛スキルをチェック！"
            btnPlayAgain.text = "もう一度プレイ"
            btnMainMenu.text = "メインメニュー"
            tvNoResults.text = "まだ記録がありません"
        }
    }
    
    private fun setupRecyclerView() {
        rankingAdapter = RankingAdapter(this, emptyList())
        rvRanking.layoutManager = LinearLayoutManager(this)
        rvRanking.adapter = rankingAdapter
    }
    
    private fun loadCurrentResult() {
        // Intent에서 현재 게임 결과 받아오기
        val playerName = intent.getStringExtra(EXTRA_PLAYER_NAME) ?: ""
        val finalAffinity = intent.getIntExtra(EXTRA_FINAL_AFFINITY, 0)
        val confessionSuccess = intent.getBooleanExtra(EXTRA_CONFESSION_SUCCESS, false)
        val gameEndTypeString = intent.getStringExtra(EXTRA_GAME_END_TYPE) ?: "GAME_OVER"
        
        val gameEndType = when (gameEndTypeString) {
            "CONFESSION_SUCCESS" -> GameResult.GameEndType.CONFESSION_SUCCESS
            "CONFESSION_FAILURE" -> GameResult.GameEndType.CONFESSION_FAILURE
            "FRIEND_END" -> GameResult.GameEndType.FRIEND_END
            "SCENARIO_END" -> GameResult.GameEndType.SCENARIO_END
            else -> GameResult.GameEndType.GAME_OVER
        }
        
        if (playerName.isNotEmpty()) {
            currentResult = GameResult(playerName, finalAffinity, confessionSuccess, gameEndType)
            
            // 현재 결과를 저장
            GameResultManager.saveGameResult(this, currentResult!!)
            
            // UI에 현재 결과 표시
            displayCurrentResult(currentResult!!)
        }
    }
    
    private fun displayCurrentResult(result: GameResult) {
        val language = LanguageManager.getLanguage(this)
        
        tvCurrentPlayerName.text = result.playerName
        tvCurrentAffinity.text = result.finalAffinity.toString()
        tvCurrentResult.text = if (language == "en") {
            result.getScoreDescriptionEn()
        } else {
            result.getScoreDescription()
        }
    }
    
    private fun loadRankingData() {
        val rankedResults = GameResultManager.getRankedResults(this)
        
        if (rankedResults.isEmpty()) {
            rvRanking.visibility = View.GONE
            tvNoResults.visibility = View.VISIBLE
        } else {
            rvRanking.visibility = View.VISIBLE
            tvNoResults.visibility = View.GONE
            rankingAdapter.updateResults(rankedResults)
        }
    }
    
    private fun setupClickListeners() {
        btnPlayAgain.setOnClickListener {
            // 새 게임 시작
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
        
        btnMainMenu.setOnClickListener {
            // 메인 메뉴로 이동
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
}