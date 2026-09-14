package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceStatus
import com.example.data.model.ClassEvent
import com.example.data.repository.CalendarRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttendancePromptDialog(
    event: ClassEvent,
    docenteName: String,
    onDismiss: () -> Unit,
    onNotAttended: () -> Unit,
    onResetOrDelete: (() -> Unit)? = null,
    onSaveAndSync: (sumario: String, minutos: Int, obs: String, sheetName: String, customDate: String, docente: String, allowDuplicate: Boolean) -> Unit
) {
    val durationMinutes = remember(event) {
        val diff = event.endTimeMillis - event.startTimeMillis
        val mins = (diff / (1000 * 60)).toInt()
        if (mins in 1..480) mins else 60
    }

    val eventDateStr = remember(event) {
        val date = if (event.startTimeMillis > 0) Date(event.startTimeMillis) else Date()
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
    }

    var sheetName by remember {
        mutableStateOf(event.schoolName.ifBlank { event.title })
    }
    var sumario by remember {
        mutableStateOf(CalendarRepository.extractClassSummary(event.title).ifBlank { event.title })
    }
    var minutosText by remember { mutableStateOf(durationMinutes.toString()) }
    var dateText by remember { mutableStateOf(eventDateStr) }
    var currentDocente by remember { mutableStateOf(docenteName.ifBlank { "Docente InovLabs" }) }
    var obs by remember { mutableStateOf("") }
    var allowDuplicate by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("edit_event_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Editar Aula / Presença",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Modifique os detalhes antes de guardar",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // School Name / Google Sheets Tab Name
                OutlinedTextField(
                    value = sheetName,
                    onValueChange = { sheetName = it },
                    label = { Text("Escola / Aba destino no Sheets") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.TableChart, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_sheet_name"),
                    singleLine = true
                )

                // Summary / Topic of Class
                OutlinedTextField(
                    value = sumario,
                    onValueChange = { sumario = it },
                    label = { Text("Sumário da Aula") },
                    placeholder = { Text("Tema ou conteúdo lecionado") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_sumario"),
                    singleLine = true
                )

                // Date and Duration Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = { dateText = it },
                        label = { Text("Data (DD/MM/AAAA)") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Event, contentDescription = null)
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("input_edit_date"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = minutosText,
                        onValueChange = { minutosText = it.filter { c -> c.isDigit() } },
                        label = { Text("Minutos") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null)
                        },
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("input_edit_minutos"),
                        singleLine = true
                    )
                }

                // Quick Duration Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Atalhos:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(45, 60, 90, 120).forEach { mins ->
                        val isSelected = minutosText == mins.toString()
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { minutosText = mins.toString() }
                        ) {
                            Text(
                                text = "${mins}m",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Docente Name
                OutlinedTextField(
                    value = currentDocente,
                    onValueChange = { currentDocente = it },
                    label = { Text("Docente InovLabs") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_docente"),
                    singleLine = true
                )

                // Observations
                OutlinedTextField(
                    value = obs,
                    onValueChange = { obs = it },
                    label = { Text("Observações (Turma, ocorrências...)") },
                    placeholder = { Text("Ex: Turma 3ºB - Sala 12") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_edit_obs"),
                    maxLines = 2
                )

                // Add Duplicate Option
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (allowDuplicate) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { allowDuplicate = !allowDuplicate }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = allowDuplicate,
                            onCheckedChange = { allowDuplicate = it },
                            modifier = Modifier.testTag("checkbox_allow_duplicate")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permitir duplicado (nova linha na folha)",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = if (allowDuplicate) "Será criada uma nova linha mesmo que já exista uma aula nesta data." else "Por defeito, atualiza a linha existente sem criar duplicados.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Delete / Revert Attendance Section (inside dialog options)
                if (event.attendanceStatus != AttendanceStatus.PENDING && onResetOrDelete != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Eliminar Registo / Repor",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Repõe a aula a pendente e apaga a entrada no Google Sheets.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    onResetOrDelete()
                                    onDismiss()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.testTag("btn_delete_attendance_dialog")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Eliminar")
                            }
                        }
                    }
                }

                // Anti-Duplicate Guarantee Banner
                if (!allowDuplicate) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Sem duplicados: Se já existir registo desta aula na folha, este será atualizado sem criar uma nova linha.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = Color(0xFF1B5E20)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = minutosText.toIntOrNull() ?: durationMinutes
                    onSaveAndSync(
                        sumario.ifBlank { event.title },
                        mins,
                        obs,
                        sheetName.ifBlank { event.schoolName.ifBlank { event.title } },
                        dateText.ifBlank { eventDateStr },
                        currentDocente,
                        allowDuplicate
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                ),
                modifier = Modifier.testTag("btn_save_edit_dialog")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (event.attendanceStatus == AttendanceStatus.ATTENDED) "Atualizar Presença" else "Guardar e Marcar Assistida",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (event.attendanceStatus == AttendanceStatus.PENDING) {
                    TextButton(
                        onClick = onNotAttended,
                        modifier = Modifier.testTag("btn_dialog_not_attended")
                    ) {
                        Text(
                            text = "Não Assistida",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_cancel_edit_dialog")
                ) {
                    Text("Cancelar", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    )
}
