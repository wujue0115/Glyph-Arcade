package com.nothing.glyphmatrix.games.jump

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.util.Log
import com.nothing.glyphmatrix.games.GlyphMatrixService
import com.nothing.glyphmatrix.games.jump.settings.JumpGameSettingsRepository
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private const val SCREEN_WIDTH = 25
private const val SCREEN_HEIGHT = 25
private const val PLATFORM_COUNT = 8
private const val FRAME_DELAY_MS = 25L
private const val BASE_FRAME_DELAY_MS = 50f
private const val HOME_RENDER_DELAY_MS = 100L
private const val CAMERA_FOLLOW_THRESHOLD = 8f
private const val PLATFORM_REMOVAL_MARGIN = 5f
private const val PLATFORM_SPAWN_BUFFER = 10f
private const val GAME_OVER_MARGIN = 5f
private const val GLYPH_WIDTH = 3
private const val GLYPH_HEIGHT = 5
private const val GLYPH_SPACING = 1
private const val MAX_BRIGHTNESS = 2047
private val BRIGHTNESS_FULL = MAX_BRIGHTNESS
private val BRIGHTNESS_DIM = scaleBrightness(100)
private val PLATFORM_BRIGHTNESS_NORMAL = scaleBrightness(100)
private val PLATFORM_BRIGHTNESS_BOUNCY = scaleBrightness(511)
private val PLATFORM_BRIGHTNESS_MOVING = scaleBrightness(100)

private fun scaleBrightness(value: Int): Int {
    return ((value / 255f) * MAX_BRIGHTNESS).roundToInt().coerceIn(0, MAX_BRIGHTNESS)
}

private val TIME_SCALE = FRAME_DELAY_MS.toFloat() / BASE_FRAME_DELAY_MS

private val GLYPH_PATTERNS = mapOf(
    '0' to listOf(
        "111",
        "101",
        "101",
        "101",
        "111"
    ),
    '1' to listOf(
        "010",
        "110",
        "010",
        "010",
        "111"
    ),
    '2' to listOf(
        "111",
        "001",
        "111",
        "100",
        "111"
    ),
    '3' to listOf(
        "111",
        "001",
        "111",
        "001",
        "111"
    ),
    '4' to listOf(
        "101",
        "101",
        "111",
        "001",
        "001"
    ),
    '5' to listOf(
        "111",
        "100",
        "111",
        "001",
        "111"
    ),
    '6' to listOf(
        "111",
        "100",
        "111",
        "101",
        "111"
    ),
    '7' to listOf(
        "111",
        "001",
        "010",
        "010",
        "010"
    ),
    '8' to listOf(
        "111",
        "101",
        "111",
        "101",
        "111"
    ),
    '9' to listOf(
        "111",
        "101",
        "111",
        "001",
        "111"
    )
)

private data class Vector2(var x: Float, var y: Float) {
    operator fun plus(other: Vector2) = Vector2(x + other.x, y + other.y)
    operator fun plusAssign(other: Vector2) {
        x += other.x
        y += other.y
    }
    operator fun times(scalar: Float) = Vector2(x * scalar, y * scalar)
}

private data class Bounds(val position: Vector2, val size: Vector2) {
    fun intersects(other: Bounds): Boolean {
        return position.x < other.position.x + other.size.x &&
            position.x + size.x > other.position.x &&
            position.y < other.position.y + other.size.y &&
            position.y + size.y > other.position.y
    }
}

private fun clamp(value: Float, minValue: Float, maxValue: Float): Float {
    return max(minValue, min(maxValue, value))
}

private enum class PlatformType {
    NORMAL,
    BOUNCY,
    MOVING
}

private class Player(startPosition: Vector2) {
    companion object {
        private const val JUMP_FORCE = -2f
        private const val SUPER_JUMP_FORCE = -6f
        private const val GRAVITY = 0.15f
        private const val MAX_HORIZONTAL_SPEED = 2f
    }

    var position: Vector2 = startPosition
        private set
    private val size = Vector2(2f, 2f)
    private var velocity = Vector2(0f, 0f)
    var maxHeightReached = startPosition.y
        private set

    val bounds: Bounds
        get() = Bounds(position, size)
    val isFalling: Boolean
        get() = velocity.y > 0f

    fun update() {
        velocity.y += GRAVITY * TIME_SCALE
        position.plusAssign(velocity * TIME_SCALE)

        if (position.x < 0f) {
            position.x = SCREEN_WIDTH - size.x
        } else if (position.x + size.x > SCREEN_WIDTH) {
            position.x = 0f
        }

        if (position.y < maxHeightReached) {
            maxHeightReached = position.y
        }
    }

    fun setHorizontalVelocity(input: Float) {
        velocity.x = clamp(input, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED)
    }

    fun reset(newPosition: Vector2) {
        position = newPosition
        velocity = Vector2(0f, 0f)
        maxHeightReached = newPosition.y
    }

    fun landOn(platform: Platform) {
        position.y = platform.position.y - size.y
        when (platform.type) {
            PlatformType.NORMAL, PlatformType.MOVING -> jump()
            PlatformType.BOUNCY -> superJump()
        }
    }

    private fun jump() {
        if (velocity.y >= 0f) {
            velocity.y = JUMP_FORCE
        }
    }

    private fun superJump() {
        velocity.y = SUPER_JUMP_FORCE
    }
}

private class Platform(
    var position: Vector2,
    val type: PlatformType,
    val size: Vector2 = Vector2(4f, 1f)
) {
    private var direction = 1f
    private val moveSpeed = 0.5f

    val bounds: Bounds
        get() = Bounds(position, size)

    fun update() {
        if (type == PlatformType.MOVING) {
            position.x += direction * moveSpeed * TIME_SCALE
            if (position.x <= 0f || position.x + size.x >= SCREEN_WIDTH) {
                direction *= -1f
                position.x = clamp(position.x, 0f, SCREEN_WIDTH - size.x)
            }
        }
    }
}

private fun PlatformType.color(): Int = when (this) {
    PlatformType.NORMAL -> PLATFORM_BRIGHTNESS_NORMAL
    PlatformType.BOUNCY -> PLATFORM_BRIGHTNESS_BOUNCY
    PlatformType.MOVING -> PLATFORM_BRIGHTNESS_MOVING
}

private class GameRenderer(private val glyphMatrixManager: GlyphMatrixManager?) {
    fun renderHome(player: Player, platforms: List<Platform>, blink: Boolean) {
        val brightness = if (blink) BRIGHTNESS_FULL else BRIGHTNESS_DIM
        renderScene(player, platforms, 0f, brightness)
    }

    fun renderGame(player: Player, platforms: List<Platform>, cameraOffset: Float) {
        renderScene(player, platforms, cameraOffset, BRIGHTNESS_FULL)
    }

    fun renderGameOver(score: Int, highScore: Int) {
        val grid = IntArray(SCREEN_WIDTH * SCREEN_HEIGHT)

        val scoreDigits = formatNumber(score)
        val scoreY = (SCREEN_HEIGHT - GLYPH_HEIGHT) / 2
        drawGlyphLine(grid, scoreDigits, scoreY, BRIGHTNESS_FULL)

        glyphMatrixManager?.setMatrixFrame(grid)
        Log.d("JumpGame", "Score: $score, High Score: $highScore")
    }

    private fun renderScene(
        player: Player,
        platforms: List<Platform>,
        cameraOffset: Float,
        playerBrightness: Int
    ) {
        val grid = IntArray(SCREEN_WIDTH * SCREEN_HEIGHT)

        platforms.forEach { platform ->
            val onScreenY = platform.position.y - cameraOffset
            if (onScreenY > SCREEN_HEIGHT + 1 || onScreenY + platform.size.y < -1) {
                return@forEach
            }
            drawRectangle(
                grid,
                platform.position.x,
                onScreenY,
                platform.size,
                platform.type.color()
            )
        }

        val playerBounds = player.bounds
        val playerY = playerBounds.position.y - cameraOffset
        drawRectangle(grid, playerBounds.position.x, playerY, playerBounds.size, playerBrightness)
        glyphMatrixManager?.setMatrixFrame(grid)
    }

    private fun drawRectangle(
        grid: IntArray,
        xPos: Float,
        yPos: Float,
        size: Vector2,
        color: Int
    ) {
        val startX = floor(xPos.toDouble()).toInt()
        val startY = floor(yPos.toDouble()).toInt()
        val width = size.x.toInt().coerceAtLeast(1)
        val height = size.y.toInt().coerceAtLeast(1)

        for (y in startY until (startY + height)) {
            if (y !in 0 until SCREEN_HEIGHT) continue
            for (x in startX until (startX + width)) {
                if (x !in 0 until SCREEN_WIDTH) continue
                val index = y * SCREEN_WIDTH + x
                if (index in grid.indices) {
                    grid[index] = color
                }
            }
        }
    }

    private fun drawGlyphLine(grid: IntArray, text: String, startY: Int, color: Int) {
        if (startY < 0 || startY + GLYPH_HEIGHT > SCREEN_HEIGHT) return
        val sanitized = text.filter { GLYPH_PATTERNS.containsKey(it) }
        if (sanitized.isEmpty()) return
        val startX = centeredStart(sanitized.length)
        drawGlyphs(grid, sanitized, startX, startY, color)
    }

    private fun drawGlyphs(
        grid: IntArray,
        text: String,
        startX: Int,
        startY: Int,
        color: Int
    ) {
        text.forEachIndexed { index, char ->
            val pattern = GLYPH_PATTERNS[char] ?: return@forEachIndexed
            val glyphX = startX + index * (GLYPH_WIDTH + GLYPH_SPACING)
            pattern.forEachIndexed { rowIndex, row ->
                val y = startY + rowIndex
                if (y !in 0 until SCREEN_HEIGHT) return@forEachIndexed
                row.forEachIndexed { columnIndex, pixel ->
                    if (pixel == '1') {
                        val x = glyphX + columnIndex
                        if (x in 0 until SCREEN_WIDTH) {
                            val indexInGrid = y * SCREEN_WIDTH + x
                            grid[indexInGrid] = color
                        }
                    }
                }
            }
        }
    }

    private fun centeredStart(length: Int): Int {
        if (length <= 0) return 0
        val totalWidth = length * GLYPH_WIDTH + (length - 1) * GLYPH_SPACING
        return max(0, (SCREEN_WIDTH - totalWidth) / 2)
    }

    private fun formatNumber(value: Int): String {
        val normalized = value.coerceAtLeast(0).coerceAtMost(999999)
        return normalized.toString()
    }

}

private class Game {
    enum class GameState {
        HOME,
        PLAYING,
        PAUSED,
        GAME_OVER
    }

    var gameState: GameState = GameState.HOME
        private set

    private var renderer: GameRenderer? = null
    private val player = Player(Vector2(12f, 15f))
    private val platforms = mutableListOf<Platform>()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var gameLoop: Job? = null
    private var idleRenderJob: Job? = null
    private var cameraOffset = 0f
    private var currentScore = 0
    private var highScore = 0
    private var blinkToggle = false

    init {
        resetWorld()
    }

    fun setRenderer(renderer: GameRenderer) {
        this.renderer = renderer
        render()
        if (gameState == GameState.HOME) {
            startHomeScreenRender()
        }
    }

    fun updatePlayerMovement(horizontalVelocity: Float) {
        if (gameState == GameState.PLAYING) {
            player.setHorizontalVelocity(horizontalVelocity)
        }
    }

    fun startGame() {
        if (gameState == GameState.PLAYING) return

        stopHomeScreenRender()

        if (gameState == GameState.GAME_OVER) {
            resetWorld()
        }

        currentScore = 0
        gameState = GameState.PLAYING
        renderer?.renderGame(player, platforms, cameraOffset)
        startGameLoop()
    }

    fun pauseGame() {
        if (gameState != GameState.PLAYING) return
        gameState = GameState.PAUSED
        gameLoop?.cancel()
        render()
    }

    fun resumeGame() {
        if (gameState != GameState.PAUSED) return
        gameState = GameState.PLAYING
        startGameLoop()
    }

    fun restartGame() {
        stopHomeScreenRender()
        gameLoop?.cancel()
        resetWorld()
        currentScore = 0
        gameState = GameState.HOME
        render()
        startHomeScreenRender()
    }

    fun handleLongPress() {
        when (gameState) {
            GameState.HOME -> startGame()
            GameState.PLAYING -> pauseGame()
            GameState.PAUSED -> resumeGame()
            GameState.GAME_OVER -> restartGame()
        }
    }

    fun render() {
        when (gameState) {
            GameState.HOME -> renderer?.renderHome(player, platforms, blinkToggle)
            GameState.PLAYING,
            GameState.PAUSED -> renderer?.renderGame(player, platforms, cameraOffset)
            GameState.GAME_OVER -> renderer?.renderGameOver(currentScore, highScore)
        }
    }

    fun cleanup() {
        gameLoop?.cancel()
        idleRenderJob?.cancel()
    }

    private fun startGameLoop() {
        gameLoop?.cancel()
        gameLoop = scope.launch {
            while (isActive && gameState == GameState.PLAYING) {
                updateWorld()
                if (gameState != GameState.PLAYING) {
                    break
                }
                renderer?.renderGame(player, platforms, cameraOffset)
                delay(FRAME_DELAY_MS)
            }
        }
    }

    private fun startHomeScreenRender() {
        idleRenderJob?.cancel()
        idleRenderJob = scope.launch {
            while (isActive && gameState == GameState.HOME) {
                renderer?.renderHome(player, platforms, blinkToggle)
                blinkToggle = !blinkToggle
                delay(HOME_RENDER_DELAY_MS)
            }
        }
    }

    private fun stopHomeScreenRender() {
        idleRenderJob?.cancel()
        blinkToggle = false
    }

    private fun updateWorld() {
        player.update()
        platforms.forEach { it.update() }
        handleCollisions()
        updateScore()
        updateCamera()
        spawnPlatformsIfNeeded()
        checkGameOver()
    }

    private fun handleCollisions() {
        if (!player.isFalling) return
        val playerBounds = player.bounds
        for (platform in platforms) {
            if (playerBounds.intersects(platform.bounds)) {
                player.landOn(platform)
                return
            }
        }
    }

    private fun updateScore() {
        val newScore = max(0, ((SCREEN_HEIGHT - player.position.y).toInt()))
        if (newScore > currentScore) {
            currentScore = newScore
            if (currentScore > highScore) {
                highScore = currentScore
            }
        }
    }

    private fun updateCamera() {
        if (player.position.y < cameraOffset + CAMERA_FOLLOW_THRESHOLD) {
            cameraOffset = player.position.y - CAMERA_FOLLOW_THRESHOLD
        }
    }

    private fun spawnPlatformsIfNeeded() {
        platforms.removeAll { it.position.y > cameraOffset + SCREEN_HEIGHT + PLATFORM_REMOVAL_MARGIN }
        val highestPlatform = platforms.minByOrNull { it.position.y } ?: return
        if (highestPlatform.position.y > cameraOffset - PLATFORM_SPAWN_BUFFER) {
            val newY = highestPlatform.position.y - Random.nextFloat() * 4f - 3f
            val newX = Random.nextFloat() * (SCREEN_WIDTH - 4)
            platforms.add(
                Platform(
                    Vector2(newX, newY),
                    randomPlatformType()
                )
            )
        }
    }

    private fun checkGameOver() {
        if (player.position.y > cameraOffset + SCREEN_HEIGHT + GAME_OVER_MARGIN) {
            gameState = GameState.GAME_OVER
            renderer?.renderGameOver(currentScore, highScore)
        }
    }

    private fun resetWorld() {
        player.reset(Vector2(12f, 15f))
        cameraOffset = 0f
        platforms.clear()
        platforms.add(Platform(Vector2(10f, 17f), PlatformType.NORMAL))

        var currentY = 13f
        repeat(PLATFORM_COUNT - 1) {
            val x = Random.nextFloat() * (SCREEN_WIDTH - 4)
            platforms.add(
                Platform(
                    Vector2(x, currentY),
                    randomPlatformType()
                )
            )
            currentY -= Random.nextFloat() * 4f + 2f
        }
    }

    private fun randomPlatformType(): PlatformType {
        return when (Random.nextInt(100)) {
            in 0..79 -> PlatformType.NORMAL
            in 80..94 -> PlatformType.MOVING
            else -> PlatformType.BOUNCY
        }
    }
}

class JumpGameService : GlyphMatrixService("Jump-Game") {
    private companion object {
        private const val TAG = "JumpGameService"
        private const val MAX_TILT = 6f
    }

    private val game = Game()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Jump Game Service Created")
        try {
            registerSpecificSensor(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering accelerometer", e)
        }
    }

    override fun performOnServiceConnected(
        context: Context,
        glyphMatrixManager: GlyphMatrixManager
    ) {
        try {
            Log.d(TAG, "Service connected, initializing renderer")
            game.setRenderer(GameRenderer(glyphMatrixManager))
            Log.d(TAG, "Renderer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service connection", e)
        }
    }

    override fun performOnServiceDisconnected(context: Context) {
        try {
            Log.d(TAG, "Service disconnected, cleaning up")
            unregisterSpecificSensor(Sensor.TYPE_ACCELEROMETER)
            game.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error during service disconnection", e)
        }
    }

    override fun onTouchPointLongPress() {
        try {
            game.handleLongPress()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling long press", e)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        try {
            if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                val rawTiltX = event.values[0]
                val clampedTilt = clamp(rawTiltX, -MAX_TILT, MAX_TILT)
                val sensitivity = JumpGameSettingsRepository.getHorizontalSensitivity(this)
                game.updatePlayerMovement(clampedTilt * sensitivity)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing sensor data", e)
        }
    }
}
