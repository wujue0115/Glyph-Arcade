package com.nothing.glyphmatrix.games.snake

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import android.util.Log
import com.nothing.glyphmatrix.games.GlyphMatrixService
import com.nothing.ketchum.GlyphMatrixManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

private const val GRID_SIZE = 25
private const val INITIAL_SNAKE_LENGTH = 5
private const val FOOD_SCORE_VALUE = 10
private const val HOME_BLINK_DELAY_MS = 400L
private const val BASE_UPDATE_DELAY_MS = 180L
private const val MIN_UPDATE_DELAY_MS = 80L
private const val MAX_BRIGHTNESS = 2047
private const val HORIZONTAL_TILT_THRESHOLD = 1.5f
private const val VERTICAL_TILT_THRESHOLD = 2.5f
private const val TAG = "SnakeGameService"
private val BOARD_CENTER = (GRID_SIZE - 1) / 2.0
private val BOARD_RADIUS = (GRID_SIZE - 1) / 2.0
private val ROW_BOUNDS = Array(GRID_SIZE) { computeRowBounds(it) }
private val COLUMN_BOUNDS = Array(GRID_SIZE) { computeColumnBounds(it) }
private val SNAKE_HEAD_BRIGHTNESS = scaleBrightness(255)
private val SNAKE_BODY_BRIGHTNESS = scaleBrightness(128)
private val SNAKE_DIM_BRIGHTNESS = scaleBrightness(32)
private val FOOD_BRIGHTNESS = scaleBrightness(768)
private val PAUSE_OVERLAY_BRIGHTNESS = scaleBrightness(24)

private fun scaleBrightness(value: Int): Int {
    return ((value / 255f) * MAX_BRIGHTNESS).toInt().coerceIn(0, MAX_BRIGHTNESS)
}

private data class Point(val x: Int, val y: Int) {
    fun isWithinBounds(): Boolean = x in 0 until GRID_SIZE && y in 0 until GRID_SIZE
    fun isInsideCircle(): Boolean {
        if (!isWithinBounds()) return false
        val dx = x - BOARD_CENTER
        val dy = y - BOARD_CENTER
        val distanceSquared = dx * dx + dy * dy
        return distanceSquared <= BOARD_RADIUS * BOARD_RADIUS + 0.25
    }
}

private data class AxisBounds(val min: Int, val max: Int)

private fun computeRowBounds(y: Int): AxisBounds {
    val dy = y - BOARD_CENTER
    val spanSquared = BOARD_RADIUS * BOARD_RADIUS - dy * dy
    val span = sqrt(max(0.0, spanSquared))
    val min = ceil(BOARD_CENTER - span).toInt().coerceIn(0, GRID_SIZE - 1)
    val max = floor(BOARD_CENTER + span).toInt().coerceIn(0, GRID_SIZE - 1)
    return AxisBounds(min, max)
}

private fun computeColumnBounds(x: Int): AxisBounds {
    val dx = x - BOARD_CENTER
    val spanSquared = BOARD_RADIUS * BOARD_RADIUS - dx * dx
    val span = sqrt(max(0.0, spanSquared))
    val min = ceil(BOARD_CENTER - span).toInt().coerceIn(0, GRID_SIZE - 1)
    val max = floor(BOARD_CENTER + span).toInt().coerceIn(0, GRID_SIZE - 1)
    return AxisBounds(min, max)
}

private fun rowBounds(y: Int): AxisBounds = ROW_BOUNDS[y.coerceIn(0, GRID_SIZE - 1)]
private fun columnBounds(x: Int): AxisBounds = COLUMN_BOUNDS[x.coerceIn(0, GRID_SIZE - 1)]

private enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    fun move(point: Point): Point = Point(point.x + dx, point.y + dy)

    fun isOpposite(other: Direction): Boolean {
        return dx == -other.dx && dy == -other.dy
    }
}

private val GLYPH_WIDTH = 3
private val GLYPH_HEIGHT = 5
private val GLYPH_SPACING = 1

private val GLYPH_PATTERNS = mapOf(
    '0' to listOf("111", "101", "101", "101", "111"),
    '1' to listOf("010", "110", "010", "010", "111"),
    '2' to listOf("111", "001", "111", "100", "111"),
    '3' to listOf("111", "001", "111", "001", "111"),
    '4' to listOf("101", "101", "111", "001", "001"),
    '5' to listOf("111", "100", "111", "001", "111"),
    '6' to listOf("111", "100", "111", "101", "111"),
    '7' to listOf("111", "001", "010", "010", "010"),
    '8' to listOf("111", "101", "111", "101", "111"),
    '9' to listOf("111", "101", "111", "001", "111")
)

private class SnakeRenderer(private val glyphMatrixManager: GlyphMatrixManager?) {

    fun renderHome(snake: List<Point>, food: Point, blink: Boolean) {
        renderSnake(
            snake = snake,
            food = food,
            headBrightness = if (blink) SNAKE_HEAD_BRIGHTNESS else SNAKE_DIM_BRIGHTNESS,
            bodyBrightness = SNAKE_DIM_BRIGHTNESS,
            showPauseOverlay = false
        )
    }

    fun renderGame(snake: List<Point>, food: Point) {
        renderSnake(
            snake = snake,
            food = food,
            headBrightness = SNAKE_HEAD_BRIGHTNESS,
            bodyBrightness = SNAKE_BODY_BRIGHTNESS,
            showPauseOverlay = false
        )
    }

    fun renderPaused(snake: List<Point>, food: Point) {
        renderSnake(
            snake = snake,
            food = food,
            headBrightness = SNAKE_BODY_BRIGHTNESS,
            bodyBrightness = SNAKE_DIM_BRIGHTNESS,
            showPauseOverlay = true
        )
    }

    fun renderGameOver(score: Int, highScore: Int) {
        val grid = IntArray(GRID_SIZE * GRID_SIZE)
        val scoreDigits = score.coerceAtLeast(0).coerceAtMost(999999).toString()
        val scoreY = (GRID_SIZE - GLYPH_HEIGHT) / 2
        drawGlyphs(grid, scoreDigits, scoreY, SNAKE_HEAD_BRIGHTNESS)
        glyphMatrixManager?.setMatrixFrame(grid)
        Log.d(TAG, "Snake Game Score: $score, High Score: $highScore")
    }

    private fun renderSnake(
        snake: List<Point>,
        food: Point,
        headBrightness: Int,
        bodyBrightness: Int,
        showPauseOverlay: Boolean
    ) {
        val grid = IntArray(GRID_SIZE * GRID_SIZE)
        drawPoint(grid, food, FOOD_BRIGHTNESS)

        snake.forEachIndexed { index, point ->
            val brightness = if (index == 0) headBrightness else bodyBrightness
            drawPoint(grid, point, brightness)
        }

        if (showPauseOverlay) {
            val midY = GRID_SIZE / 2
            for (x in GRID_SIZE / 2 - 1..GRID_SIZE / 2 + 1) {
                if (x in 0 until GRID_SIZE) {
                    val top = midY - 4
                    val bottom = midY + 4
                    for (y in top..bottom) {
                        val index = y * GRID_SIZE + x
                        if (y in 0 until GRID_SIZE && index in grid.indices) {
                            grid[index] = PAUSE_OVERLAY_BRIGHTNESS
                        }
                    }
                }
            }
        }

        glyphMatrixManager?.setMatrixFrame(grid)
    }

    private fun drawPoint(grid: IntArray, point: Point, brightness: Int) {
        if (!point.isWithinBounds()) return
        val index = point.y * GRID_SIZE + point.x
        if (index in grid.indices) {
            grid[index] = brightness
        }
    }

    private fun drawGlyphs(grid: IntArray, text: String, startY: Int, color: Int) {
        if (text.isEmpty()) return
        val startX = centeredStart(text.length)
        text.forEachIndexed { index, char ->
            val pattern = GLYPH_PATTERNS[char] ?: return@forEachIndexed
            val glyphX = startX + index * (GLYPH_WIDTH + GLYPH_SPACING)
            pattern.forEachIndexed { rowIndex, row ->
                val y = startY + rowIndex
                if (y !in 0 until GRID_SIZE) return@forEachIndexed
                row.forEachIndexed { columnIndex, pixel ->
                    if (pixel == '1') {
                        val x = glyphX + columnIndex
                        if (x in 0 until GRID_SIZE) {
                            val idx = y * GRID_SIZE + x
                            if (idx in grid.indices) {
                                grid[idx] = color
                            }
                        }
                    }
                }
            }
        }
    }

    private fun centeredStart(length: Int): Int {
        if (length <= 0) return 0
        val totalWidth = length * GLYPH_WIDTH + (length - 1) * GLYPH_SPACING
        return max(0, (GRID_SIZE - totalWidth) / 2)
    }
}

private class SnakeGame {
    enum class GameState {
        HOME,
        PLAYING,
        PAUSED,
        GAME_OVER
    }

    private val snake = ArrayDeque<Point>()
    private var direction: Direction = Direction.RIGHT
    private var pendingDirection: Direction? = null
    private var food: Point = Point(0, 0)
    private var renderer: SnakeRenderer? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var loopJob: Job? = null
    private var idleJob: Job? = null
    private var blink = false
    private var score = 0
    private var highScore = 0
    private var state = GameState.HOME

    init {
        resetWorld()
    }

    fun setRenderer(renderer: SnakeRenderer) {
        this.renderer = renderer
        render()
        if (state == GameState.HOME) {
            startHomeAnimation()
        }
    }

    fun handleLongPress() {
        when (state) {
            GameState.HOME -> startGame()
            GameState.PLAYING -> pauseGame()
            GameState.PAUSED -> resumeGame()
            GameState.GAME_OVER -> restartToHome()
        }
    }

    fun queueDirection(direction: Direction) {
        if (state != GameState.PLAYING) return
        val activeDirection = pendingDirection ?: this.direction
        if (direction == activeDirection || direction.isOpposite(activeDirection)) return
        pendingDirection = direction
    }

    fun cleanup() {
        loopJob?.cancel()
        idleJob?.cancel()
        scope.coroutineContext.cancelChildren()
    }

    private fun startGame() {
        idleJob?.cancel()
        state = GameState.PLAYING
        startLoop()
    }

    private fun pauseGame() {
        if (state != GameState.PLAYING) return
        state = GameState.PAUSED
        loopJob?.cancel()
        render()
    }

    private fun resumeGame() {
        if (state != GameState.PAUSED) return
        state = GameState.PLAYING
        startLoop()
    }

    private fun restartToHome() {
        loopJob?.cancel()
        resetWorld()
        state = GameState.HOME
        render()
        startHomeAnimation()
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive && state == GameState.PLAYING) {
                val stillPlaying = updateWorld()
                render()
                if (!stillPlaying) {
                    state = GameState.GAME_OVER
                    renderer?.renderGameOver(score, highScore)
                    break
                }
                delay(currentDelay())
            }
        }
    }

    private fun currentDelay(): Long {
        val speedBonus = min(80, score / 5)
        return max(MIN_UPDATE_DELAY_MS, BASE_UPDATE_DELAY_MS - speedBonus).toLong()
    }

    private fun startHomeAnimation() {
        idleJob?.cancel()
        idleJob = scope.launch {
            while (isActive && state == GameState.HOME) {
                blink = !blink
                render()
                delay(HOME_BLINK_DELAY_MS)
            }
        }
    }

    private fun updateWorld(): Boolean {
        pendingDirection?.let {
            direction = it
            pendingDirection = null
        }

        var newHead = direction.move(snake.first())
        newHead = wrapWithinCircle(newHead, direction)
        if (!newHead.isInsideCircle() || snake.contains(newHead)) {
            return false
        }

        snake.addFirst(newHead)
        val ateFood = newHead == food
        if (ateFood) {
            score += FOOD_SCORE_VALUE
            if (score > highScore) {
                highScore = score
            }
            food = spawnFood()
        } else {
            snake.removeLast()
        }
        return true
    }

    private fun spawnFood(): Point {
        val occupied = snake.toSet()
        val openSlots = mutableListOf<Point>()
        for (y in 0 until GRID_SIZE) {
            for (x in 0 until GRID_SIZE) {
                val candidate = Point(x, y)
                if (candidate.isInsideCircle() && !occupied.contains(candidate)) {
                    openSlots.add(candidate)
                }
            }
        }
        if (openSlots.isEmpty()) {
            return snake.firstOrNull() ?: Point(GRID_SIZE / 2, GRID_SIZE / 2)
        }
        return openSlots.random()
    }

    private fun wrapWithinCircle(candidate: Point, direction: Direction): Point {
        var x = candidate.x
        var y = candidate.y
        when (direction) {
            Direction.RIGHT -> {
                y = wrapIndex(y)
                val bounds = rowBounds(y)
                if (x >= GRID_SIZE) x = 0
                if (x < bounds.min) x = bounds.max
                if (x > bounds.max) x = bounds.min
            }
            Direction.LEFT -> {
                y = wrapIndex(y)
                val bounds = rowBounds(y)
                if (x < 0) x = GRID_SIZE - 1
                if (x < bounds.min) x = bounds.max
                if (x > bounds.max) x = bounds.min
            }
            Direction.UP -> {
                x = wrapIndex(x)
                val bounds = columnBounds(x)
                if (y < 0) y = GRID_SIZE - 1
                if (y < bounds.min) y = bounds.max
                if (y > bounds.max) y = bounds.min
            }
            Direction.DOWN -> {
                x = wrapIndex(x)
                val bounds = columnBounds(x)
                if (y >= GRID_SIZE) y = 0
                if (y < bounds.min) y = bounds.max
                if (y > bounds.max) y = bounds.min
            }
        }
        return Point(x.coerceIn(0, GRID_SIZE - 1), y.coerceIn(0, GRID_SIZE - 1))
    }

    private fun wrapIndex(value: Int): Int = when {
        value < 0 -> GRID_SIZE - 1
        value >= GRID_SIZE -> 0
        else -> value
    }

    private fun resetWorld() {
        snake.clear()
        val startY = GRID_SIZE / 2
        val startX = GRID_SIZE / 2 - INITIAL_SNAKE_LENGTH / 2
        for (i in 0 until INITIAL_SNAKE_LENGTH) {
            snake.addFirst(Point(startX + i, startY))
        }
        direction = Direction.RIGHT
        pendingDirection = null
        food = spawnFood()
        score = 0
        blink = false
    }

    private fun render() {
        when (state) {
            GameState.HOME -> renderer?.renderHome(snake.toList(), food, blink)
            GameState.PLAYING -> renderer?.renderGame(snake.toList(), food)
            GameState.PAUSED -> renderer?.renderPaused(snake.toList(), food)
            GameState.GAME_OVER -> renderer?.renderGameOver(score, highScore)
        }
    }
}

class SnakeGameService : GlyphMatrixService("Snake-Game") {

    private val game = SnakeGame()

    override fun onCreate() {
        super.onCreate()
        try {
            registerSpecificSensor(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to register accelerometer", e)
        }
    }

    override fun performOnServiceConnected(context: Context, glyphMatrixManager: GlyphMatrixManager) {
        try {
            game.setRenderer(SnakeRenderer(glyphMatrixManager))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach renderer", e)
        }
    }

    override fun performOnServiceDisconnected(context: Context) {
        try {
            unregisterSpecificSensor(Sensor.TYPE_ACCELEROMETER)
            game.cleanup()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    override fun onTouchPointLongPress() {
        game.handleLongPress()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values.getOrNull(0) ?: return
        val y = event.values.getOrNull(1) ?: return
        DirectionDetector.fromTilt(x, y)?.let { direction ->
            game.queueDirection(direction)
        }
    }
}

private object DirectionDetector {
    fun fromTilt(x: Float, y: Float): Direction? {
        val absX = abs(x)
        val absY = abs(y)
        val horizontalActive = absX >= HORIZONTAL_TILT_THRESHOLD
        val verticalActive = absY >= VERTICAL_TILT_THRESHOLD
        if (!horizontalActive && !verticalActive) {
            return null
        }
        return when {
            horizontalActive && (!verticalActive || absX >= absY) ->
                if (x > 0) Direction.RIGHT else Direction.LEFT
            verticalActive ->
                if (y > 0) Direction.DOWN else Direction.UP
            else -> null
        }
    }
}
