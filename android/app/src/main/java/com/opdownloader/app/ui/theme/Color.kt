package com.opdownloader.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Base Brand Colors (Dark-First Architecture)
val BaseBackground = Color(0xFF0B0D10)
val SurfaceCard = Color(0xFF15181D)
val SurfaceElevated = Color(0xFF1E2229)
val SurfaceBorder = Color(0xFF282D35)

// Typography & Content Colors
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF9CA3AF)
val TextTertiary = Color(0xFF6B7280)

// Accent & Brand Gradient
val AccentPrimary = Color(0xFF6366F1) // Electric Indigo
val AccentSecondary = Color(0xFF8B5CF6) // Royal Violet
val AccentGradient = Brush.linearGradient(
    colors = listOf(AccentPrimary, AccentSecondary)
)
val AccentPressed = Color(0xFF4F46E5)

// Status & Feedback Colors
val StatusSuccess = Color(0xFF22C55E)
val StatusError = Color(0xFFEF4444)
val StatusWarning = Color(0xFFF59E0B)

// Chip & Tag Colors
val ChipBackground = Color(0xFF232730)
val ChipSelectedBorder = Color(0xFF6366F1)
