package com.example.offgridmesh.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.offgridmesh.data.MeshPacket
import com.example.offgridmesh.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    allPermissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val userName by viewModel.userName.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val peerCount by viewModel.peerCount.collectAsState()
    val isMeshActive by viewModel.isMeshActive.collectAsState()

    var messageText by remember { mutableStateOf("") }
    var showNameDialog by remember { mutableStateOf(userName.isBlank()) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Username setup dialog
    if (showNameDialog) {
        UserNameDialog(
            onConfirm = { name ->
                viewModel.setUserName(name)
                showNameDialog = false
            }
        )
    }

    Scaffold(
        containerColor = DeepNavy,
        topBar = {
            MeshTopBar(
                isMeshActive = isMeshActive,
                peerCount = peerCount,
                userName = userName,
                onToggleMesh = {
                    if (isMeshActive) viewModel.stopMesh() else viewModel.startMesh()
                },
                onEditName = { showNameDialog = true }
            )
        },
        bottomBar = {
            MessageInputBar(
                value = messageText,
                onValueChange = { messageText = it },
                onSend = {
                    viewModel.sendMessage(messageText)
                    messageText = ""
                    keyboardController?.hide()
                },
                enabled = isMeshActive && userName.isNotBlank()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Permission banner
            AnimatedVisibility(visible = !allPermissionsGranted) {
                PermissionBanner(onRequestPermissions = onRequestPermissions)
            }

            if (messages.isEmpty()) {
                // Empty state
                EmptyState(
                    isMeshActive = isMeshActive,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            } else {
                // Message list
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages, key = { it.messageId }) { packet ->
                        val isOwn = packet.senderName == userName
                        MessageBubble(
                            packet = packet,
                            isOwn = isOwn,
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

// ─── Top App Bar ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeshTopBar(
    isMeshActive: Boolean,
    peerCount: Int,
    userName: String,
    onToggleMesh: () -> Unit,
    onEditName: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val statusColor by animateColorAsState(
        targetValue = if (isMeshActive) MeshGreen else TextMuted,
        animationSpec = tween(500),
        label = "statusColor"
    )

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DarkSurface,
            titleContentColor = TextPrimary
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Animated status dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .alpha(if (isMeshActive) pulseAlpha else 1f)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "OffGrid Mesh",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isMeshActive) {
                            if (peerCount > 0) "$peerCount peer${if (peerCount > 1) "s" else ""} connected"
                            else "Searching for peers…"
                        } else {
                            "Mesh offline"
                        },
                        fontSize = 12.sp,
                        color = if (isMeshActive) MeshGreen.copy(alpha = 0.8f) else TextMuted
                    )
                }
            }
        },
        actions = {
            // Peer count pill
            if (isMeshActive && peerCount > 0) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MeshGreen.copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = "🟢 $peerCount",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MeshGreen
                    )
                }
            }

            // Toggle mesh button
            IconButton(onClick = onToggleMesh) {
                Icon(
                    imageVector = if (isMeshActive) Icons.Filled.Close else Icons.Filled.PlayArrow,
                    contentDescription = if (isMeshActive) "Stop Mesh" else "Start Mesh",
                    tint = if (isMeshActive) ErrorRed else MeshGreen
                )
            }
        }
    )
}

// ─── Permission Banner ──────────────────────────────────────

@Composable
private fun PermissionBanner(onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            WarningAmber.copy(alpha = 0.2f),
                            ErrorRed.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = 1.dp,
                    color = WarningAmber.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Permissions Required",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Bluetooth & location access needed for mesh networking.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarningAmber,
                        contentColor = DeepNavy
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Grant", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─── Message Bubble ─────────────────────────────────────────

@Composable
private fun MessageBubble(
    packet: MeshPacket,
    isOwn: Boolean,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(packet.timestamp) { timeFormat.format(Date(packet.timestamp)) }

    val bubbleColor = if (isOwn) OwnBubble else PeerBubble
    val borderColor = if (isOwn) OwnBubbleBorder.copy(alpha = 0.4f) else PeerBubbleBorder
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isOwn) 16.dp else 4.dp,
        bottomEnd = if (isOwn) 4.dp else 16.dp
    )

    val hopText = when (packet.hopCount) {
        0 -> "Direct"
        1 -> "1 hop"
        else -> "${packet.hopCount} hops"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isOwn) Alignment.End else Alignment.Start
    ) {
        // Sender name (only for others)
        if (!isOwn) {
            Text(
                text = packet.senderName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MeshCyan,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }

        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .border(1.dp, borderColor, bubbleShape)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = packet.text,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hop badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (packet.hopCount == 0)
                            MeshGreen.copy(alpha = 0.12f)
                        else
                            MeshCyan.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = hopText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (packet.hopCount == 0) MeshGreen else MeshCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

// ─── Empty State ────────────────────────────────────────────

@Composable
private fun EmptyState(isMeshActive: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Mesh icon glow
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .drawBehind {
                        drawCircle(
                            color = MeshGreen.copy(alpha = glowAlpha),
                            radius = size.minDimension / 1.5f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📡",
                    fontSize = 48.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isMeshActive) "Listening for nearby peers…"
                else "Start the mesh to connect",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isMeshActive) "Messages will appear here when peers send them."
                else "Tap the play button above to begin advertising\nand discovering nearby devices.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

// ─── Message Input Bar ──────────────────────────────────────

@Composable
private fun MessageInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        color = DarkSurface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (enabled) "Type a message…" else "Set name & start mesh first",
                        color = TextMuted
                    )
                },
                enabled = enabled,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeshGreen,
                    unfocusedBorderColor = DividerColor,
                    disabledBorderColor = DividerColor.copy(alpha = 0.5f),
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface,
                    disabledContainerColor = CardSurface.copy(alpha = 0.5f),
                    cursorColor = MeshGreen,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSend() }),
                maxLines = 4,
                singleLine = false
            )

            Spacer(modifier = Modifier.width(8.dp))

            val sendEnabled = enabled && value.isNotBlank()
            val buttonColor by animateColorAsState(
                targetValue = if (sendEnabled) MeshGreen else TextMuted,
                animationSpec = tween(300),
                label = "sendButtonColor"
            )

            FilledIconButton(
                onClick = onSend,
                enabled = sendEnabled,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = buttonColor.copy(alpha = 0.2f),
                    contentColor = buttonColor,
                    disabledContainerColor = TextMuted.copy(alpha = 0.1f),
                    disabledContentColor = TextMuted.copy(alpha = 0.5f)
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

// ─── Username Dialog ────────────────────────────────────────

@Composable
private fun UserNameDialog(onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { /* Non-dismissable — user must set a name */ },
        containerColor = CardSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Column {
                Text("📡", fontSize = 32.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Welcome to OffGrid Mesh",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Choose a display name that other mesh peers will see.",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = { Text("Your name", color = TextMuted) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MeshGreen,
                        unfocusedBorderColor = DividerColor,
                        cursorColor = MeshGreen,
                        focusedContainerColor = ElevatedSurface,
                        unfocusedContainerColor = ElevatedSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${name.length}/20",
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MeshGreen,
                    contentColor = DeepNavy
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Join Mesh", fontWeight = FontWeight.Bold)
            }
        }
    )
}
