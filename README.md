
-----

# 💖 Secret Love Mode 💖

**Your Choices Shape the Story\! A Tale of Forbidden Love with an Interactive AI.**

\<br\>

`Secret Love Mode` is an interactive romance simulation for Android where the story branches and characters' emotions change based on your choices. Enjoy a unique experience every time, thanks to an intelligent character AI and a dynamic scenario system.

-----

## ✨ Key Features

* **🤖 Intelligent Character AI**: This isn't just simple scripting. A character AI powered by a Large Language Model (LLM) reacts to your words and expresses emotions in real-time.
* **📚 Dynamic Scenario System**: The story isn't hard-coded. Scenarios are loaded and managed from an external `json` file, allowing for easy addition and expansion of new storylines.
* **🎨 Adaptive UI**: Button sizes and heights dynamically adjust based on the length of the dialogue text, always providing optimal readability.
* **💖 Emotion State Management**: Based on your choices, the character's 'Affinity' and 'Drunkenness' levels change in real-time, directly impacting their tone, reactions, and the story's branching points.

-----

## 🛠️ Project Structure

The key components and their roles are as follows:

- **`com.SecretLoveMode`**
    - `MainActivity`: The main screen Activity that serves as the app's entry point.
    - `GameActivity`: The core screen Activity where gameplay takes place.
    - `MyApplication`: The Application class for managing global state and the ViewModel.
    - `SlmViewModel`: The ViewModel that connects the UI state with business logic.
    - `CharacterAi`: The AI engine that handles character dialogue and emotional responses.
    - `ScenarioManager`: Loads scenario files from `assets` and manages the game's story progression.
    - `GameState`: A data class that stores all current game states, such as affinity and conversation count.
    - `ButtonUtils`: A utility for dynamically adjusting the size and style of UI buttons.

-----

## 🚀 Getting Started

### Prerequisites

To build and run this project, you will need the following:

- Android Studio (Latest version recommended)
- Android SDK (Target: API 35)
- Kotlin 2.0
- Java SDK 21

### Installation and Running

1.  **Clone the Repository**
    ```bash
    git clone [Your Repository URL]
    ```
2.  **Open the Project**
    - Launch Android Studio and select `Open an existing project`, then navigate to the cloned project folder.
3.  **Sync Gradle**
    - Wait for Android Studio to automatically sync the Gradle files. (If needed, click the `Sync Project with Gradle Files` icon).
4.  **Build and Run**
    - Connect an emulator or a physical device and click the `Run 'app'` (▶️) button to launch the application.

-----

## 🔧 Building

You can build the project from the terminal using the command below:

```bash
./gradlew build
```

-----

<!-- ## 📄 License

This project is distributed under the [MIT License](https://opensource.org/licenses/MIT). See the `LICENSE` file for more details.

*(It is recommended to add a LICENSE file if one does not already exist.)*

-----

## 🤝 Contributors

Thank you to all the contributors who have helped with this project.

*(You can add the GitHub IDs or names of contributors here.)*

\<a href="[https://github.com/](https://github.com/)[your-repo]/graphs/contributors"\>
\<img src="[https://contrib.rocks/image?repo=](https://www.google.com/search?q=https://contrib.rocks/image%3Frepo%3D)[your-username]/[your-repo]"/\>
\</a\>

-----

## 📞 Contact

For any questions or suggestions about the project, please feel free to reach out.

[Your Email Address or other contact information]
-->