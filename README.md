# Secret Love Mode

**あなたの選択が物語を変える——AI と紡ぐ禁断の恋。**

`Secret Love Mode` は、選択によって物語が分岐し、キャラクターの感情が変化するインタラクティブな恋愛シミュレーション（Android）です。大規模言語モデル（LLM）を活用したキャラクター AI と、外部シナリオによるダイナミックな進行で、毎回新鮮な体験が楽しめます。

---

## 主な特徴
- **知的なキャラクター AI**: 単純なスクリプトではなく、LLM によって会話内容や状況に応じた感情表現と応答を生成します。
- **ダイナミックなシナリオ管理**: シナリオは `assets` 配下の JSON から読み込まれ、物語の追加・拡張が容易です。
- **アダプティブ UI**: 台詞の長さに応じてボタンのサイズや高さを動的に調整し、常に読みやすさを確保します。
- **感情（好感度）ステート**: 選択に応じてリアルタイムに好感度が変動し、口調・反応・分岐条件に影響します。
- **多言語対応（JP/EN）**: 言語選択画面を備え、日本語/英語のシナリオ・プロンプトを自動切替します。
- **ランキング表示**: プレイ結果のサマリをランキング画面に表示できます。

---

## プロジェクト構成（抜粋）
- `com.secretlovemode`
  - `MyApplication`: アプリ全体の初期化とグローバル状態管理。
  - `ui.main.MainActivity`: エントリーポイント。プレイヤー名やモデルファイルの選択を行います。
  - `ui.game.GameActivity`: ゲーム本編の画面。
  - `ui.main.SlmViewModel`: UI 状態とドメインロジックを連携。
  - `domain.CharacterAi`: LLM を用いた感情判定・告白判定などの対話ロジック。
  - `data.repository.ScenarioManager`: `assets` からシナリオを読み込み、進行と分岐を管理。
  - `util.LanguageManager`: 言語設定に応じたファイル名・UI 文言の切替。
  - `util.ButtonUtils`: UI ボタンのスタイル・サイズを動的調整。
  - `ui.ranking.RankingActivity / RankingAdapter`: ランキング画面。

---

## セットアップ
### 必要環境
- Android Studio（最新版推奨）
- Android SDK（Target: API 35）
- Kotlin 2.0.21
- Java SDK 21

### 実行手順
1. リポジトリをクローン
   ```bash
   git clone <YOUR_REPOSITORY_URL>
   ```
2. Android Studio でプロジェクトを開く
3. Gradle を同期
4. 実機またはエミュレータで `Run 'app'` を実行

---

## ビルド
ターミナルからのビルド:
```bash
./gradlew build
```

---

## アセットとシナリオ
- シナリオは `app/src/main/assets` に配置します。
  - 例: `session1.json`（日本語）、`session1_en.json`（英語）
  - `LanguageManager` により言語設定に応じてファイルが自動選択されます。
- プロンプトは `affection_judge_prompt(_en).txt`、`confession_prompt(_en).txt` を使用します。

---

## 公開に関する注意
- 本プロジェクトに使用しているゲーム用の画像（キャラクター、背景、スクリーンショット等）およびシナリオデータ（JSON）は、権利・契約・プライバシー等の理由により**公開できません**。
- これらのアセットはリポジトリへの同梱・再配布を行いません。必要に応じて、各自で代替アセット／独自シナリオをご用意ください。

---

## 補足
- 不明点や要望があれば issue もしくは連絡先までお知らせください。

<!--
## 🤝 Contributors / 連絡先
- an.minhyoung(at)gmail(dot)com
- Thank you for ubiquitous laboratory members
-->

