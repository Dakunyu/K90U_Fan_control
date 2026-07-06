package com.k90ultra.fancontrol

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var currentLevel by mutableStateOf<Int?>(null)
    private var lastRequestedLevel by mutableStateOf<Int?>(null)
    private var isLoading by mutableStateOf(false)
    private var statusMessage by mutableStateOf("\u6b63\u5728\u542f\u52a8...")
    private var lastErrorDetail by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.TRANSPARENT
        window.navigationBarColor = AndroidColor.TRANSPARENT
        setContent {
            FanControlTheme {
                FanControlScreen(
                    currentLevel = currentLevel,
                    lastRequestedLevel = lastRequestedLevel,
                    isLoading = isLoading,
                    statusMessage = statusMessage,
                    lastErrorDetail = lastErrorDetail,
                    onRefresh = { readCurrentLevel() },
                    onSelectLevel = { setLevel(it) }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        readCurrentLevel()
    }

    private fun readCurrentLevel() {
        if (isLoading) return
        isLoading = true
        statusMessage = "\u6b63\u5728\u8bfb\u53d6\u771f\u5b9e\u6863\u4f4d..."
        lastErrorDetail = ""
        thread(name = "fan-read") {
            val result = FanController.readLevel()
            runOnUiThread {
                isLoading = false
                when (result) {
                    is FanResult.Success -> {
                        currentLevel = result.value
                        statusMessage = "\u5df2\u8bfb\u53d6\u771f\u5b9e\u6863\u4f4d\uff1a${result.value}"
                        lastErrorDetail = ""
                    }
                    is FanResult.Failure -> {
                        currentLevel = null
                        statusMessage = result.userMessage
                        lastErrorDetail = result.detail
                    }
                }
            }
        }
    }

    private fun setLevel(level: Int) {
        if (isLoading) return
        lastRequestedLevel = level
        isLoading = true
        statusMessage = "\u6b63\u5728\u5207\u6362\u5230\u6863\u4f4d $level..."
        lastErrorDetail = ""
        thread(name = "fan-set") {
            val result = FanController.setLevel(level)
            runOnUiThread {
                isLoading = false
                when (result) {
                    is FanResult.Success -> {
                        currentLevel = result.value
                        statusMessage = "\u5df2\u5e94\u7528\u5e76\u786e\u8ba4\u771f\u5b9e\u6863\u4f4d\uff1a${result.value}"
                        lastErrorDetail = ""
                    }
                    is FanResult.Failure -> {
                        statusMessage = result.userMessage
                        lastErrorDetail = result.detail
                    }
                }
            }
        }
    }
}

object FanController {
    const val FAN_NODE = "/sys/devices/platform/soc/soc:xiaomi_fan/target_level"
    private const val TIMEOUT_SECONDS = 8L

    fun readLevel(): FanResult<Int> {
        val commandResult = runAsRoot("cat '$FAN_NODE'")
        if (commandResult !is CommandResult.Done) {
            return commandResult.toFailure("\u8bfb\u53d6\u5931\u8d25")
        }
        if (commandResult.exitCode != 0) {
            return classifyFailure(commandResult, "\u8bfb\u53d6\u5931\u8d25")
        }
        val raw = commandResult.stdout.trim()
        val parsed = raw.toIntOrNull()
        return if (parsed != null && parsed in 0..4) {
            FanResult.Success(parsed)
        } else {
            FanResult.Failure(
                userMessage = "\u8bfb\u53d6\u5230\u672a\u77e5\u6863\u4f4d\u8f93\u51fa",
                detail = "stdout=$raw, stderr=${commandResult.stderr.trim()}"
            )
        }
    }

    fun setLevel(level: Int): FanResult<Int> {
        if (level !in 0..4) {
            return FanResult.Failure("\u65e0\u6548\u6863\u4f4d\uff1a$level", "\u53ea\u5141\u8bb8 0/1/2/3/4")
        }
        val writeResult = runAsRoot("echo $level > '$FAN_NODE'")
        if (writeResult !is CommandResult.Done) {
            return writeResult.toFailure("\u5199\u5165\u5931\u8d25")
        }
        if (writeResult.exitCode != 0) {
            return classifyFailure(writeResult, "\u5199\u5165\u5931\u8d25")
        }
        return readLevel()
    }

    private fun runAsRoot(command: String): CommandResult {
        return try {
            val process = ProcessBuilder("su", "-c", command).start()
            var stdout = ""
            var stderr = ""
            val outThread = thread(name = "su-stdout") {
                stdout = process.inputStream.bufferedReader().use { it.readText() }
            }
            val errThread = thread(name = "su-stderr") {
                stderr = process.errorStream.bufferedReader().use { it.readText() }
            }
            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                CommandResult.Timeout(stdout, stderr)
            } else {
                outThread.join(1000)
                errThread.join(1000)
                CommandResult.Done(process.exitValue(), stdout, stderr)
            }
        } catch (error: IOException) {
            CommandResult.ExecError(error.message ?: error::class.java.simpleName)
        } catch (error: Throwable) {
            CommandResult.ExecError(error.message ?: error::class.java.simpleName)
        }
    }

    private fun classifyFailure(result: CommandResult.Done, prefix: String): FanResult.Failure {
        val allText = "${result.stdout}\n${result.stderr}".lowercase()
        val detail = "exit=${result.exitCode}, stdout=${result.stdout.trim()}, stderr=${result.stderr.trim()}"
        return when {
            "permission denied" in allText || "denied" in allText -> {
                FanResult.Failure("$prefix\uff1aROOT \u6743\u9650\u88ab\u62d2\u7edd\u6216\u8282\u70b9\u65e0\u6743\u9650", detail)
            }
            "no such file" in allText || "not found" in allText -> {
                FanResult.Failure("$prefix\uff1a\u98ce\u6247\u8282\u70b9\u4e0d\u5b58\u5728", detail)
            }
            else -> FanResult.Failure("$prefix\uff1a\u547d\u4ee4\u6267\u884c\u5931\u8d25", detail)
        }
    }

    private fun CommandResult.toFailure(prefix: String): FanResult.Failure {
        return when (this) {
            is CommandResult.Timeout -> FanResult.Failure("$prefix\uff1aROOT \u547d\u4ee4\u8d85\u65f6", "stdout=${stdout.trim()}, stderr=${stderr.trim()}")
            is CommandResult.ExecError -> FanResult.Failure("$prefix\uff1a\u65e0\u6cd5\u6267\u884c su", message)
            is CommandResult.Done -> classifyFailure(this, prefix)
        }
    }
}

sealed interface FanResult<out T> {
    data class Success<T>(val value: T) : FanResult<T>
    data class Failure(val userMessage: String, val detail: String = "") : FanResult<Nothing>
}

private sealed interface CommandResult {
    data class Done(val exitCode: Int, val stdout: String, val stderr: String) : CommandResult
    data class Timeout(val stdout: String, val stderr: String) : CommandResult
    data class ExecError(val message: String) : CommandResult
}

private data class FanLevel(
    val value: Int,
    val name: String,
    val detail: String,
    val rpm: String,
    val start: Color,
    val end: Color
)

private val fanLevels = listOf(
    FanLevel(0, "\u667a\u80fd", "\u81ea\u52a8\u8c03\u9891\u7b56\u7565", "\u81ea\u52a8", Color(0xFF22D3EE), Color(0xFF2563EB)),
    FanLevel(1, "\u9759\u8c27", "\u4f4e\u566a\u58f0\u6863\u4f4d", "12000\u8f6c", Color(0xFF60A5FA), Color(0xFF6366F1)),
    FanLevel(2, "\u5f3a\u51b7", "\u9ad8\u901f\u5f3a\u51b7\u6863", "15000\u8f6c", Color(0xFF8B5CF6), Color(0xFFEC4899)),
    FanLevel(3, "\u589e\u5f3a", "\u9690\u85cf\u6863\u4f4d", "16000\u8f6c", Color(0xFFF59E0B), Color(0xFFEF4444)),
    FanLevel(4, "\u6781\u901f", "\u9690\u85cf\u6863\u4f4d", "20000\u8f6c", Color(0xFFFF3B5F), Color(0xFF7C2D12))
)

@Composable
private fun FanControlTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF22D3EE),
        secondary = Color(0xFF8B5CF6),
        tertiary = Color(0xFFFF3B5F),
        background = Color(0xFF05070D),
        surface = Color(0xFF0E1422),
        surfaceVariant = Color(0xFF151D30),
        onPrimary = Color(0xFF001014),
        onSecondary = Color.White,
        onBackground = Color(0xFFF2F7FF),
        onSurface = Color(0xFFF2F7FF),
        onSurfaceVariant = Color(0xFFB7C3D8)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
private fun FanControlScreen(
    currentLevel: Int?,
    lastRequestedLevel: Int?,
    isLoading: Boolean,
    statusMessage: String,
    lastErrorDetail: String,
    onRefresh: () -> Unit,
    onSelectLevel: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF030712), Color(0xFF081528), Color(0xFF05070D))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TopBar(
                isLoading = isLoading,
                statusMessage = statusMessage,
                lastErrorDetail = lastErrorDetail,
                onRefresh = onRefresh
            )
            DashboardCard(currentLevel = currentLevel)
            ModeDeck(currentLevel = currentLevel, isLoading = isLoading, onSelectLevel = onSelectLevel)
        }
    }
}

@Composable
private fun TopBar(
    isLoading: Boolean,
    statusMessage: String,
    lastErrorDetail: String,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                "\u98ce\u6247\u63a7\u5236",
                color = Color.White,
                fontSize = 27.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
            Text(
                if (lastErrorDetail.isBlank()) statusMessage else statusMessage,
                color = if (lastErrorDetail.isBlank()) Color(0xFF8EA0BC) else Color(0xFFFF9FB2),
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false
            )
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0x6638D6C9)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBFFAF5))
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(19.dp), strokeWidth = 2.dp, color = Color(0xFF22D3EE))
            } else {
                Text("\u5237\u65b0", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun DashboardCard(currentLevel: Int?) {
    val level = currentLevel?.let { value -> fanLevels.firstOrNull { it.value == value } }
    val glowStart = level?.start ?: Color(0xFF22D3EE)
    val glowEnd = level?.end ?: Color(0xFF8B5CF6)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xE60D1320)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(glowStart, glowEnd))),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        currentLevel?.toString() ?: "--",
                        color = Color.White,
                        fontSize = 30.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        "Level",
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    "\u5f53\u524d\u6863\u4f4d",
                    color = Color(0xFF8290AA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    level?.name ?: "\u672a\u77e5",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ModeDeck(currentLevel: Int?, isLoading: Boolean, onSelectLevel: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            "\u98ce\u6247\u6a21\u5f0f",
            color = Color(0xFFB8C7E6),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
        fanLevels.forEach { level ->
            ModeCard(
                level = level,
                selected = currentLevel == level.value,
                enabled = !isLoading,
                onClick = { onSelectLevel(level.value) }
            )
        }
    }
}

@Composable
private fun ModeCard(
    level: FanLevel,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) level.start.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.10f),
        label = "mode-border"
    )
    val textColor by animateColorAsState(
        targetValue = if (enabled) Color.White else Color(0xFF657087),
        label = "mode-text"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(94.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(if (selected) 1.6.dp else 1.dp, borderColor),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = textColor,
            disabledContentColor = Color(0xFF657087)
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        if (selected) {
                            listOf(level.start.copy(alpha = 0.90f), level.end.copy(alpha = 0.84f))
                        } else {
                            listOf(Color(0xFF121A2A), Color(0xFF0B1020))
                        }
                    )
                )
                .padding(horizontal = 15.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.20f) else level.start.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Level ${level.value}",
                        color = if (selected) Color.White else level.start,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Text(
                    level.name,
                    modifier = Modifier.weight(1f),
                    color = textColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        level.rpm,
                        color = if (selected) Color.White else Color(0xFFBFD0EA),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Text(
                level.detail,
                modifier = Modifier.fillMaxWidth(),
                color = textColor.copy(alpha = 0.80f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
