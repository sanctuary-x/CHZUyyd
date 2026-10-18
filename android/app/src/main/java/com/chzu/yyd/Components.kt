package com.chzu.yyd

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (!title.isNullOrBlank()) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun KeyValue(label: String, value: String, valueColor: Color = Color(0xFF1B2533)) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.width(92.dp))
        Text(value, color = valueColor, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .size(8.dp)
            .background(color, CircleShape)
    ) {}
}

@Composable
fun Pill(text: String, color: Color = Blue, bg: Color = Color(0xFFDCEBFF)) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun Hint(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 12.sp, lineHeight = 18.sp)
}

@Composable
fun SpacerH(h: Int = 8) {
    Spacer(Modifier.height(h.dp))
}

@Composable
fun ButtonRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
