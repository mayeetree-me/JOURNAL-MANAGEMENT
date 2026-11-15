package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.EditorialTask
import com.example.data.model.Target
import com.example.data.model.TaskStatus
import com.example.domain.IsoDateUtils
import com.example.ui.theme.LocalStatusColors
import com.example.ui.viewmodel.JournalViewModel
import com.example.ui.viewmodel.MemberWorkloadSummary
import com.example.ui.viewmodel.TargetProgressSummary
import kotlin.math.max

@Composable
fun SegmentedProgressBar(
  summary: TargetProgressSummary,
  modifier: Modifier = Modifier,
) {
  val statusColors = LocalStatusColors.current
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val total = summary.totalCount

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = summary.target.name,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "${summary.completionPercent}% (${summary.doneCount}/$total)",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Canvas(
      modifier = Modifier
        .fillMaxWidth()
        .height(14.dp)
        .clip(RoundedCornerShape(7.dp)),
    ) {
      drawRect(color = trackColor, size = size)
      if (total > 0) {
        val segments = listOf(
          summary.doneCount to statusColors.doneFg,
          summary.inProgressCount to statusColors.inProgressFg,
          summary.todoCount to statusColors.todoFg,
          summary.overdueCount to statusColors.overdueFg,
        )
        var currentX = 0f
        segments.forEach { (count, color) ->
          if (count > 0) {
            val segWidth = (count.toFloat() / total.toFloat()) * size.width
            drawRect(
              color = color,
              topLeft = Offset(currentX, 0f),
              size = Size(segWidth, size.height),
            )
            currentX += segWidth
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      LegendDot("Done ${summary.doneCount}", statusColors.doneFg)
      LegendDot("In prog ${summary.inProgressCount}", statusColors.inProgressFg)
      LegendDot("To do ${summary.todoCount}", statusColors.todoFg)
      if (summary.overdueCount > 0) {
        LegendDot("Overdue ${summary.overdueCount}", statusColors.overdueFg)
      }
    }
  }
}

@Composable
fun GanttTimelineChart(
  target: Target,
  tasks: List<EditorialTask>,
  modifier: Modifier = Modifier,
) {
  val statusColors = LocalStatusColors.current
  val primaryColor = MaterialTheme.colorScheme.primary
  val outlineColor = MaterialTheme.colorScheme.outlineVariant
  val trackBg = MaterialTheme.colorScheme.surfaceVariant
  val todayIso = IsoDateUtils.todayIso()
  val stages = target.stages.sortedBy { it.offsetDays }

  if (stages.isEmpty()) {
    Surface(
      modifier = modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
      Text(
        text = "No workflow stages defined yet for this target.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(14.dp),
      )
    }
    return
  }

  val deadlineIso = IsoDateUtils.normalizeOrToday(target.deadline)
  val minOffset = minOf(stages.minOf { it.offsetDays }, -60L)
  val spanDays = max(1L, -minOffset + 5L)
  val timelineWidthDp = max(340, (spanDays * 5).toInt().coerceAtMost(620)).dp
  val startIso = IsoDateUtils.plusDays(deadlineIso, minOffset)

  val targetTasks = tasks.filter { it.targetId == target.id }

  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Publication Gantt Timeline",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = "Deadline ${IsoDateUtils.formatShortMonthDay(deadlineIso)}",
        style = MaterialTheme.typography.labelSmall,
        color = primaryColor,
      )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Surface(
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surfaceContainerLow,
      border = BorderStroke(1.dp, outlineColor),
    ) {
      Column(
        modifier = Modifier
          .horizontalScroll(rememberScrollState())
          .padding(12.dp),
      ) {
        // Header axis ticks
        Row(
          modifier = Modifier.width(timelineWidthDp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = IsoDateUtils.formatShortMonthDay(startIso),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = "Today (${IsoDateUtils.formatShortMonthDay(todayIso)})",
            style = MaterialTheme.typography.labelSmall,
            color = primaryColor,
          )
          Text(
            text = IsoDateUtils.formatShortMonthDay(deadlineIso),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        stages.forEachIndexed { index, stage ->
          val stageEndIso = IsoDateUtils.normalizeOrToday(stage.dueDate)
          val prevOffset = if (index == 0) minOffset - 5L else stages[index - 1].offsetDays
          val stageStartIso = IsoDateUtils.plusDays(deadlineIso, prevOffset)
          val stageTasks = targetTasks.filter { it.stageName.equals(stage.name, ignoreCase = true) }

          val barColor = when {
            stageTasks.isNotEmpty() && stageTasks.all { it.statusEnum == TaskStatus.DONE } -> statusColors.doneFg
            stageTasks.any { JournalViewModel.isTaskOverdue(it, todayIso) } -> statusColors.overdueFg
            stageTasks.any { it.statusEnum == TaskStatus.IN_PROGRESS } -> statusColors.inProgressFg
            IsoDateUtils.isBefore(stageEndIso, todayIso) && stageTasks.isEmpty() -> statusColors.todoFg
            else -> primaryColor
          }

          Column(
            modifier = Modifier
              .width(timelineWidthDp)
              .padding(vertical = 4.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = stage.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                text = "${IsoDateUtils.formatShortMonthDay(stageEndIso)} (${stage.offsetDays}d)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Canvas(
              modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            ) {
              drawRoundRect(
                color = trackBg,
                cornerRadius = CornerRadius(6f, 6f),
                size = size,
              )
              val startDays = IsoDateUtils.daysBetween(startIso, stageStartIso).coerceIn(0L, spanDays)
              val endDays = IsoDateUtils.daysBetween(startIso, stageEndIso).coerceIn(startDays + 1L, spanDays)
              val leftPx = (startDays.toFloat() / spanDays.toFloat()) * size.width
              val widthPx = max(14f, ((endDays - startDays).toFloat() / spanDays.toFloat()) * size.width)
              drawRoundRect(
                color = barColor,
                topLeft = Offset(leftPx, 0f),
                size = Size(widthPx.coerceAtMost(size.width - leftPx), size.height),
                cornerRadius = CornerRadius(6f, 6f),
              )

              val todayDays = IsoDateUtils.daysBetween(startIso, todayIso)
              if (todayDays in 0..spanDays) {
                val todayX = (todayDays.toFloat() / spanDays.toFloat()) * size.width
                drawLine(
                  color = primaryColor,
                  start = Offset(todayX, -4f),
                  end = Offset(todayX, size.height + 4f),
                  strokeWidth = 3f,
                  pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun WorkloadBarChart(
  workloads: List<MemberWorkloadSummary>,
  modifier: Modifier = Modifier,
) {
  val statusColors = LocalStatusColors.current
  val trackBg = MaterialTheme.colorScheme.surfaceVariant
  val primaryColor = MaterialTheme.colorScheme.primary
  val maxTotal = max(1, workloads.maxOfOrNull { it.totalCount } ?: 1)

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      LegendDot("Open", primaryColor)
      LegendDot("Overdue", statusColors.overdueFg)
      LegendDot("Done", statusColors.doneFg)
    }

    workloads.forEach { item ->
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = item.user.displayName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          Text(
            text = "${item.openCount} open · ${item.doneCount} done",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Canvas(
          modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)),
        ) {
          drawRect(color = trackBg, size = size)
          val nonOverdueOpen = (item.openCount - item.overdueCount).coerceAtLeast(0)
          var x = 0f
          listOf(
            item.overdueCount to statusColors.overdueFg,
            nonOverdueOpen to primaryColor,
            item.doneCount to statusColors.doneFg,
          ).forEach { (count, color) ->
            if (count > 0) {
              val w = (count.toFloat() / maxTotal.toFloat()) * size.width
              drawRect(color = color, topLeft = Offset(x, 0f), size = Size(w, size.height))
              x += w
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LegendDot(label: String, color: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp),
  ) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .clip(CircleShape)
        .background(color),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
