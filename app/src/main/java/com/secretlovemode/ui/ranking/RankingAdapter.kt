package com.secretlovemode.ui.ranking

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.secretlovemode.R
import com.secretlovemode.data.model.GameResult
import com.secretlovemode.util.LanguageManager

class RankingAdapter(
    private val context: Context,
    private var results: List<GameResult>
) : RecyclerView.Adapter<RankingAdapter.RankingViewHolder>() {

    class RankingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRank: TextView = view.findViewById(R.id.tvRank)
        val tvPlayerName: TextView = view.findViewById(R.id.tvPlayerName)
        val tvAffinity: TextView = view.findViewById(R.id.tvAffinity)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvResultStatus: TextView = view.findViewById(R.id.tvResultStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ranking, parent, false)
        return RankingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RankingViewHolder, position: Int) {
        val result = results[position]
        val language = LanguageManager.getLanguage(context)
        
        // 순위 표시
        holder.tvRank.text = (position + 1).toString()
        
        // 플레이어 이름
        holder.tvPlayerName.text = result.playerName
        
        // 호감도
        holder.tvAffinity.text = result.finalAffinity.toString()
        
        // 날짜
        holder.tvDate.text = result.getFormattedDate()
        
        // 결과 상태
        holder.tvResultStatus.text = if (language == "en") {
            result.getScoreDescriptionEn()
        } else {
            result.getScoreDescription()
        }
        
        // 결과에 따른 배경 색상 변경
        val backgroundRes = when (result.gameEndType) {
            GameResult.GameEndType.CONFESSION_SUCCESS -> R.drawable.status_badge_success
            GameResult.GameEndType.FRIEND_END -> R.drawable.status_badge_friend
            GameResult.GameEndType.CONFESSION_FAILURE -> R.drawable.status_badge_failure
            GameResult.GameEndType.GAME_OVER, 
            GameResult.GameEndType.SCENARIO_END -> R.drawable.status_badge_gameover
        }
        holder.tvResultStatus.setBackgroundResource(backgroundRes)
        
        // 1등인 경우 특별한 스타일
        if (position == 0) {
            holder.tvRank.text = "👑"
            holder.tvRank.textSize = 24f
        } else {
            holder.tvRank.textSize = 18f
        }
    }

    override fun getItemCount() = results.size

    fun updateResults(newResults: List<GameResult>) {
        results = newResults
        notifyDataSetChanged()
    }
}