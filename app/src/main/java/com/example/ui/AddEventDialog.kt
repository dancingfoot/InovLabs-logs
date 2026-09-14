package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddEventDialog(
    selectedDateFormatted: String = "",
    targetDateMillis: Long = System.currentTimeMillis(),
    docenteName: String = "",
    onDismiss: () -> Unit,
    onAddClass: (
        schoolName: String,
        sumario: String,
        durationMinutes: Int,
        customDateStr: String,
        docente: String,
        obs: String,
        markAsAttended: Boolean,
        markAsNotAttended: Boolean
    ) -> Unit
) {
    val initialDateStr = remember(targetDateMillis) {
        val date = if (targetDateMillis > 0) Date(targetDateMillis) else Date()
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date)
    }

    var sheetName by remember { mutableStateOf("") }
    var sumario by remember { mutableStateOf("") }
    var minutosText by remember { mutableStateOf("90") }
    var dateText by remember { mutableStateOf(initialDateStr) }
    var currentDocente by remember { mutableStateOf(docenteName.ifBlank { "Docente InovLabs" }) }
    var obs by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_event_dialog"),
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
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Adicionar Aula / Presença",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Preencha os detalhes antes de guardar",
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
                    placeholder = { Text("Ex: CEPAO") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.TableChart, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_school_name"),
                    singleLine = true
                )

                // Summary / Topic of Class
                OutlinedTextField(
                    value = sumario,
                    onValueChange = { sumario = it },
                    label = { Text("Sumário da Aula") },
                    placeholder = { Text("Robótica Educativa") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_sumario"),
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
                            .testTag("input_add_date"),
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
                            .testTag("input_add_minutos"),
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
                    placeholder = { Text("Docente InovLabs") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_add_docente"),
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
                        .testTag("input_add_obs"),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = minutosText.toIntOrNull() ?: 90
                    onAddClass(
                        sheetName.trim(),
                        sumario.trim().ifBlank { "Robótica Educativa" },
                        mins,
                        dateText.trim().ifBlank { initialDateStr },
                        currentDocente.trim(),
                        obs.trim(),
                        true,  // markAsAttended
                        false  // markAsNotAttended
                    )
                },
                enabled = sheetName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_add_dialog")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Guardar e Marcar Assistida",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val mins = minutosText.toIntOrNull() ?: 90
                            onAddClass(
                                sheetName.trim(),
                                sumario.trim().ifBlank { "Robótica Educativa" },
                                mins,
                                dateText.trim().ifBlank { initialDateStr },
                                currentDocente.trim(),
                                obs.trim(),
                                false, // markAsAttended
                                true   // markAsNotAttended
                            )
                        },
                        enabled = sheetName.isNotBlank(),
                        modifier = Modifier.testTag("btn_add_not_attended")
                    ) {
                        Text(
                            text = "Não Assistida",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    TextButton(
                        onClick = {
                            val mins = minutosText.toIntOrNull() ?: 90
                            onAddClass(
                                sheetName.trim(),
                                sumario.trim().ifBlank { "Robótica Educativa" },
                                mins,
                                dateText.trim().ifBlank { initialDateStr },
                                currentDocente.trim(),
                                obs.trim(),
                                false, // markAsAttended
                                false  // pending
                            )
                        },
                        enabled = sheetName.isNotBlank(),
                        modifier = Modifier.testTag("btn_add_schedule_pending")
                    ) {
                        Text(
                            text = "Agendar",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_cancel_add_dialog")
                ) {
                    Text("Cancelar", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    )
}

