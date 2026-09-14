package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AddEventDialog(
    selectedDateFormatted: String = "",
    targetDateMillis: Long = System.currentTimeMillis(),
    onDismiss: () -> Unit,
    onAddEvent: (title: String, durationMinutes: Int, dateMillis: Long) -> Unit
) {
    var schoolName by remember { mutableStateOf("") }
    var classTopic by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf("60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("add_event_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (selectedDateFormatted.isNotBlank()) "Adicionar Aula ($selectedDateFormatted)" else "Adicionar Aula",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (selectedDateFormatted.isNotBlank()) {
                        "Agende uma aula para $selectedDateFormatted indicando o nome da escola:"
                    } else {
                        "Agende uma aula indicando o nome da escola:"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = schoolName,
                    onValueChange = { schoolName = it },
                    label = { Text("Nome da Escola (ex: Escola Básica D. Pedro)") },
                    placeholder = { Text("Escola Básica D. Pedro") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_school_name"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = classTopic,
                    onValueChange = { classTopic = it },
                    label = { Text("Tema da Aula / Disciplina") },
                    placeholder = { Text("Robótica / Programação") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_class_topic"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                    label = { Text("Duração (Minutos)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_class_duration"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fullTitle = if (classTopic.isNotBlank()) {
                        "${schoolName.trim()} - ${classTopic.trim()}"
                    } else {
                        schoolName.trim()
                    }
                    val mins = durationText.toIntOrNull() ?: 60
                    if (fullTitle.isNotBlank()) {
                        onAddEvent(fullTitle, mins, targetDateMillis)
                    }
                },
                enabled = schoolName.isNotBlank(),
                modifier = Modifier.testTag("btn_confirm_add_class")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Agendar Aula")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
