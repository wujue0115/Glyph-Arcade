# Glyph Arcade

A collection of mini-games designed specifically for the Nothing Phone 3's Glyph Matrix display. Experience classic arcade gameplay reimagined on the unique 25×25 LED matrix.

## 🎮 Games

### Jump Game
An endless vertical platformer where you climb higher and higher, avoiding the fall.

**Gameplay:**
- Bounce upward by landing on platforms that regenerate as you ascend
- Gravity constantly pulls you down while the screen wraps horizontally
- Climb as high as possible and beat your high score

**Platform Types:**
- **Normal platforms** (80%) - Standard jump height
- **Moving platforms** (5%) - Horizontal movement challenge
- **Bouncy platforms** (15%) - Super jump for extra height

**Controls:**
- **Long-press** Glyph button: Start/Pause/Resume/Restart
- **Tilt left/right**: Move your character horizontally
- **Screen wrapping**: Move off one side to appear on the other

### Snake Game
Guide your snake through a circular arena, eating food and growing longer.

**Gameplay:**
- Navigate a 25×25 circular matrix where food spawns anywhere inside the lit area
- Each bite grows your snake and speeds up the game
- Avoid colliding with walls or your own body

**Controls:**
- **Long-press** Glyph button: Start/Pause/Resume/Restart
- **Gentle horizontal tilts**: Steer left or right
- **Stronger vertical tilts**: Move up or down
- **Edge wrapping**: Touch a wall to wrap to the opposite edge

## 🎯 Features

- **Dual game experience**: Two complete arcade games in one app
- **Adjustable sensitivity**: Fine-tune tilt controls to your preference
- **High score tracking**: Beat your personal best in each game
- **Glyph Matrix optimization**: Designed specifically for the 25×25 LED display
- **Material Design 3**: Modern, clean UI following Android design guidelines
- **Real-time rendering**: Smooth 40 FPS gameplay (Jump) and adaptive speed (Snake)

## 🛠️ Technical Details

- **Platform**: Android (minSdk 34, targetSdk 36)
- **Language**: Kotlin with Jetpack Compose UI
- **Architecture**: Service-based game engine with coroutine-driven game loops
- **Hardware Requirements**:
  - Nothing Phone 3 with Glyph Matrix support
  - Accelerometer sensor for tilt controls
  - Glyph Matrix SDK 1.0

## 🚀 Getting Started

1. Clone the repository
2. Open the project in Android Studio
3. Ensure you have the Glyph Matrix SDK (`glyph-matrix-sdk-1.0.aar`) in `app/libs/`
4. Build and install on your Nothing Phone 3
5. Enable the games in your Nothing Phone's Glyph settings

## 📱 How to Play

1. Open the Glyph Arcade app to view game descriptions and adjust settings
2. Access games directly from the Glyph interface on your Nothing Phone 3
3. Use long-press on the Glyph button to control game states
4. Tilt your phone to control gameplay

## ⚙️ Settings

- **Horizontal tilt sensitivity**: Shared across both games (Range: 0.5× – 2.0×)
- Increase for faster, more responsive controls
- Decrease for precise, controlled movements

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

- Follow existing code style and design patterns
- Test on Nothing Phone 3 hardware when possible
- Maintain Material Design 3 UI consistency
