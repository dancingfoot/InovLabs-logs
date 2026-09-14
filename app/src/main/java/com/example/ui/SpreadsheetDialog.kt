package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AttendanceRecord
import java.util.Locale

@Composable
fun SpreadsheetDialog(
    records: List<AttendanceRecord>,
    totalMinutes: Int,
    onDismiss: () -> Unit,
    onShareCsv: () -> Unit,
    onDeleteRecord: (AttendanceRecord) -> Unit,
    onClearAll: () -> Unit,
    onImportCsv: (String) -> Unit = {}
) {
    val context = LocalContext.current

    var selectedSheetFilter by remember { mutableStateOf("Todas as Folhas") }
    val uniqueSheets = remember(records) {
        listOf("Todas as Folhas") + records.map { it.sheetName.ifBlank { it.professorEscola } }.filter { it.isNotBlank() }.distinct()
    }
    val filteredRecords = remember(records, selectedSheetFilter) {
        if (selectedSheetFilter == "Todas as Folhas") records
        else records.filter { (it.sheetName.ifBlank { it.professorEscola }) == selectedSheetFilter }
    }
    val filteredMinutes = remember(filteredRecords) {
        filteredRecords.sumOf { it.minutos }
    }

    var showImportDialog by remember { mutableStateOf(false) }
    var showCsvPreview by remember { mutableStateOf(false) }

    fun generateFormattedCsv(): String {
        val exportList = filteredRecords
        val currentMinutes = if (selectedSheetFilter == "Todas as Folhas") totalMinutes else filteredMinutes
        val sb = StringBuilder()
        sb.append("Nº Aula,Docente InovLabs,Professor Escola,Folha/Aba,Data,Sumário,Minutos,Obs,,${currentMinutes},Total Minutos\n")
        val maxRows = maxOf(exportList.size, 1)
        for (i in 0 until maxRows) {
            val record = exportList.getOrNull(i)
            val baseColumns = if (record != null) {
                val targetSheet = record.sheetName.ifBlank { record.professorEscola }
                "${record.lessonNumber},${record.docenteInovLabs},${record.professorEscola},$targetSheet,${record.date},${record.sumario},${record.minutos},${record.obs}"
            } else {
                ",,,,,,,"
            }
            sb.append("$baseColumns,,\n")
        }
        return sb.toString()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .testTag("spreadsheet_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TableChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                text = "InovLabs_logs",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Estrutura do Ficheiro CSV & Tabela",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Total Aulas",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${records.size}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1.2f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Minutos Totais",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "$totalMinutes min (${totalMinutes / 60}h ${totalMinutes % 60}m)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Total Abas",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${maxOf(0, uniqueSheets.size - 1)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Table or CSV Text View Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showCsvPreview) "Visualização Direta do Ficheiro CSV" else "Tabela de Presenças Registadas",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { showCsvPreview = !showCsvPreview }) {
                            Text(if (showCsvPreview) "Ver Tabela" else "Ver Formato CSV", fontSize = 12.sp)
                        }
                        TextButton(onClick = { showImportDialog = true }) {
                            Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Importar CSV", fontSize = 12.sp)
                        }
                    }
                }

                // Sheet Filter Chips (if multiple sheets exist)
                if (uniqueSheets.size > 1) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uniqueSheets) { sheetNameItem ->
                            val isSelected = sheetNameItem == selectedSheetFilter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedSheetFilter = sheetNameItem },
                                label = {
                                    Text(
                                        text = if (sheetNameItem == "Todas as Folhas") "📑 Todas as Folhas" else "📄 $sheetNameItem",
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Table View / CSV Raw Content
                if (showCsvPreview) {
                    val rawCsv = generateFormattedCsv()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = rawCsv,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else if (filteredRecords.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = if (records.isEmpty()) "Nenhuma linha de presença adicionada ainda" else "Nenhum registo na folha '$selectedSheetFilter'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Marque as aulas de hoje como 'Assistida' para preencher as linhas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp)
                    ) {
                        val horizontalScroll = rememberScrollState()

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(horizontalScroll)
                        ) {
                            // Table Header Row
                            Row(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TableCell(text = "Nº Aula", width = 65.dp, isHeader = true)
                                TableCell(text = "Folha/Aba", width = 130.dp, isHeader = true)
                                TableCell(text = "Docente InovLabs", width = 130.dp, isHeader = true)
                                TableCell(text = "Professor Escola", width = 140.dp, isHeader = true)
                                TableCell(text = "Data", width = 90.dp, isHeader = true)
                                TableCell(text = "Sumário", width = 170.dp, isHeader = true)
                                TableCell(text = "Minutos", width = 75.dp, isHeader = true)
                                TableCell(text = "Obs", width = 90.dp, isHeader = true)
                                TableCell(text = "Ação", width = 50.dp, isHeader = true)
                            }

                            Divider(modifier = Modifier.padding(vertical = 4.dp))

                            LazyColumn(
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                items(filteredRecords) { record ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TableCell(text = record.lessonNumber.toString(), width = 65.dp, isBold = true)
                                        TableCell(text = record.sheetName.ifBlank { record.professorEscola }, width = 130.dp, isBold = true)
                                        TableCell(text = record.docenteInovLabs, width = 130.dp)
                                        TableCell(text = record.professorEscola, width = 140.dp)
                                        TableCell(text = record.date, width = 90.dp)
                                        TableCell(text = record.sumario, width = 170.dp)
                                        TableCell(text = "${record.minutos} min", width = 75.dp)
                                        TableCell(text = record.obs.ifBlank { "-" }, width = 90.dp)
                                        Box(modifier = Modifier.width(50.dp), contentAlignment = Alignment.Center) {
                                            IconButton(
                                                onClick = { onDeleteRecord(record) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Eliminar linha",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val formattedCsv = generateFormattedCsv()
                            clipboard.setPrimaryClip(ClipData.newPlainText("Attendance CSV", formattedCsv))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar CSV")
                    }

                    FilledTonalButton(
                        onClick = onShareCsv,
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar / Partilhar")
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        var importText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Text("Importar Ficheiro / Conteúdo CSV", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cole o conteúdo CSV para adicionar as linhas à base de dados:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        placeholder = { Text("Nº Aula,Docente InovLabs,Professor Escola,Data,Sumário,Minutos,Obs...") },
                        maxLines = 10
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importText.isNotBlank()) {
                            onImportCsv(importText)
                            showImportDialog = false
                        }
                    },
                    enabled = importText.isNotBlank()
                ) {
                    Text("Importar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun TableCell(
    text: String,
    width: androidx.compose.ui.unit.Dp,
    isHeader: Boolean = false,
    isBold: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp),
        style = if (isHeader) {
            MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        } else {
            MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = FontFamily.SansSerif,
                fontSize = 12.sp
            )
        },
        maxLines = 2
    )
}
