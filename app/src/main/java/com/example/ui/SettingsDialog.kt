package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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

@Composable
fun SettingsDialog(
    initialDocenteName: String,
    initialWebhookUrl: String,
    initialSheetViewUrl: String,
    isDemoMode: Boolean,
    onGenerateTestSchedule: () -> Unit,
    onClearTestSchedule: () -> Unit,
    onSave: (docenteName: String, webhookUrl: String, sheetViewUrl: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var docenteName by remember { mutableStateOf(initialDocenteName) }
    var webhookUrl by remember { mutableStateOf(initialWebhookUrl) }
    var sheetViewUrl by remember { mutableStateOf(initialSheetViewUrl) }
    var showScriptInstructions by remember { mutableStateOf(true) }
    var showScriptCode by remember { mutableStateOf(false) }

    val sampleAppsScript = """
function doPost(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var data = JSON.parse(e.postData.contents);
    
    // Obter todos os possíveis nomes do evento / escola
    var candidates = [
      data.sheetName,
      data.eventTitle,
      data.eventName,
      data.schoolName,
      data.professorEscola
    ];
    
    // Função para normalizar texto (ignorar acentos, espaços e maiúsculas)
    function normalize(str) {
      if (!str) return "";
      return str.toString()
        .toLowerCase()
        .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
        .replace(/[^a-z0-9]/g, "");
    }
    
    var allSheets = ss.getSheets();
    var matchedSheet = null;
    
    // 1. Procura a aba que corresponda ao nome do evento
    for (var c = 0; c < candidates.length; c++) {
      var candidate = (candidates[c] || "").toString().trim();
      if (!candidate) continue;
      
      var normCandidate = normalize(candidate);
      if (!normCandidate) continue;
      
      // A. Correspondência exata no nome da aba
      for (var i = 0; i < allSheets.length; i++) {
        var sName = allSheets[i].getName().trim();
        if (sName.toLowerCase() === candidate.toLowerCase()) {
          matchedSheet = allSheets[i];
          break;
        }
      }
      if (matchedSheet) break;
      
      // B. Correspondência aproximada (ignora espaços, maiúsculas, hífens)
      for (var i = 0; i < allSheets.length; i++) {
        var normSheetName = normalize(allSheets[i].getName());
        if (normSheetName && (normSheetName === normCandidate || 
            normCandidate.indexOf(normSheetName) !== -1 || 
            normSheetName.indexOf(normCandidate) !== -1)) {
          matchedSheet = allSheets[i];
          break;
        }
      }
      if (matchedSheet) break;
    }
    
    // 2. SE NÃO EXISTIR ABA CORRESPONDENTE: DESCARTAR
    if (!matchedSheet) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "discarded",
        message: "Nenhuma aba encontrada com o nome do evento."
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    var sheet = matchedSheet;
    var targetDate = (data.data || data.date || "").toString().trim();
    var lessonNum = parseInt(data.lessonNumber, 10) || 0;
    var lastRow = sheet.getLastRow();
    
    // 3. AÇÃO: ELIMINAR LINHA (QUANDO DESFAZ OU ELIMINA REGISTO NA APP)
    if (data.action === "deleteRow") {
      var deleted = false;
      if (lastRow > 1) {
        var values = sheet.getRange(2, 1, lastRow - 1, 5).getValues();
        for (var r = values.length - 1; r >= 0; r--) {
          var rowDate = (values[r][3] ? values[r][3].toString() : "");
          var rowLesson = parseInt(values[r][0], 10) || 0;
          var rowSumario = (values[r][4] ? values[r][4].toString() : "");
          
          var matchDate = targetDate && (rowDate.indexOf(targetDate) !== -1 || targetDate.indexOf(rowDate) !== -1);
          var matchLesson = lessonNum > 0 && rowLesson === lessonNum;
          var matchSumario = data.sumario && rowSumario.toLowerCase().indexOf(data.sumario.toLowerCase()) !== -1;
          
          if ((matchDate && matchLesson) || (matchDate && matchSumario)) {
            sheet.deleteRow(r + 2);
            deleted = true;
            break;
          }
        }
      }
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        action: "deleted",
        deleted: deleted,
        sheetUsed: sheet.getName()
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    // 4. AÇÃO: ADICIONAR OU ATUALIZAR LINHA
    if (lastRow === 0) {
      sheet.appendRow(["Nº Aula", "Docente InovLabs", "Professor Escola", "Data", "Sumário", "Minutos", "Obs"]);
      lastRow = 1;
    }
    
    var targetRow = -1;
    
    // Se NÃO for permitido duplicados, verifica se já existe registo da mesma aula nesta data
    if (!data.allowDuplicate && lastRow > 1) {
      var values = sheet.getRange(2, 1, lastRow - 1, 5).getValues();
      for (var r = 0; r < values.length; r++) {
        var rowDate = (values[r][3] ? values[r][3].toString() : "");
        var rowLesson = parseInt(values[r][0], 10) || 0;
        var rowSumario = (values[r][4] ? values[r][4].toString() : "");
        
        var matchDate = targetDate && (rowDate.indexOf(targetDate) !== -1 || targetDate.indexOf(rowDate) !== -1);
        var matchLesson = lessonNum > 0 && rowLesson === lessonNum;
        var matchSumario = data.sumario && rowSumario.toLowerCase().indexOf(data.sumario.toLowerCase()) !== -1;
        
        if ((matchDate && matchLesson) || (matchDate && matchSumario)) {
          targetRow = r + 2;
          break;
        }
      }
    }
    
    // Se for nova linha ou duplicado: encontrar próxima linha vazia
    if (targetRow === -1) {
      if (lastRow > 1) {
        var colA = sheet.getRange(2, 1, lastRow - 1, 1).getValues();
        for (var r = 0; r < colA.length; r++) {
          if (colA[r][0] === "" || colA[r][0] === null || colA[r][0] === undefined) {
            targetRow = r + 2;
            break;
          }
        }
      }
      if (targetRow === -1) {
        targetRow = Math.max(lastRow + 1, 2);
      }
    }
    
    // Escreve os dados na linha correspondente
    sheet.getRange(targetRow, 1, 1, 7).setValues([[
      data.lessonNumber,
      data.docenteInovLabs,
      data.professorEscola || sheet.getName(),
      data.data || data.date,
      data.sumario,
      data.minutos,
      data.obs || ""
    ]]);
    
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      sheetUsed: sheet.getName(),
      row: targetRow,
      updated: (targetRow <= lastRow)
    })).setMimeType(ContentService.MimeType.JSON);
    
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}
""".trimIndent()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .testTag("settings_dialog"),
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
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                text = "Definições e Sincronização",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Configurar formador/docente e ligação ao Google Sheets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Docente InovLabs
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Docente InovLabs (Nome do Formador)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedTextField(
                                value = docenteName,
                                onValueChange = { docenteName = it },
                                label = { Text("Nome Padrão do Docente") },
                                placeholder = { Text("Docente InovLabs") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_docente_name"),
                                singleLine = true
                            )
                            Text(
                                text = "Este nome é preenchido automaticamente na 2ª coluna ('Docente InovLabs') em todas as aulas registadas.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Google Sheets Webhook / Apps Script
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Webhook do Google Sheets em Direto (Opcional)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(onClick = { showScriptInstructions = !showScriptInstructions }) {
                                    Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Guia de Ligação", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            OutlinedTextField(
                                value = webhookUrl,
                                onValueChange = { webhookUrl = it },
                                label = { Text("URL do Webhook (Google Apps Script)") },
                                placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_webhook_url"),
                                singleLine = true
                            )

                            if (showScriptInstructions) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "💻 Importante: Faça este passo no COMPUTADOR (PC). A aplicação Google Sheets no telemóvel não tem o menu 'Extensões'.",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }

                                        Text(
                                            text = "Passo a passo no computador:",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "1. No seu PC, abra a folha de cálculo no Google Sheets.\n" +
                                                    "2. No menu superior, clique em: Extensões → Apps Script.\n" +
                                                    "3. Apague o código que lá estiver e cole o código abaixo.\n" +
                                                    "4. Clique em Implementar (Deploy) → Nova implementação.\n" +
                                                    "5. Na engrenagem ⚙️ escolha 'Aplicação Web'.\n" +
                                                    "   • Executar como: Eu\n" +
                                                    "   • Quem tem acesso: Qualquer pessoa (Anyone)\n" +
                                                    "6. Clique em Implementar, dê as permissões e copie o URL da Aplicação Web (/exec).\n" +
                                                    "7. Cole o URL no campo acima.\n\n" +
                                                    "✨ Suporte Multi-Abas: O script encaminha automaticamente o registo de cada aula para o separador/aba correspondente ao evento do calendário (ou cria a aba se ainda não existir).",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // Dropdown to view code + Direct Copy Button
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { showScriptCode = !showScriptCode },
                                                modifier = Modifier.testTag("btn_toggle_script_code")
                                            ) {
                                                Icon(
                                                    imageVector = if (showScriptCode) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (showScriptCode) "Ocultar Script ▲" else "Ver Script Google Apps ▼",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                                                )
                                            }

                                            Button(
                                                onClick = {
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("Apps Script", sampleAppsScript))
                                                    Toast.makeText(context, "Código copiado para a área de transferência!", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary
                                                ),
                                                modifier = Modifier.testTag("btn_copy_script_code")
                                            ) {
                                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Copiar Código", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                            }
                                        }

                                        // Script Code Details (Visible only when dropdown is expanded)
                                        if (showScriptCode) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 240.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.surfaceVariant,
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(8.dp)
                                            ) {
                                                Text(
                                                    text = sampleAppsScript,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Calendário de Testes (CEPAO e LiceuOeiras)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Calendário de Testes",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Gera um horário de testes para hoje com as turmas da folha de cálculo (CEPAO e LiceuOeiras) para experimentar o registo de presenças e o envio para a folha.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onGenerateTestSchedule,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("btn_generate_test_schedule"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    )
                                ) {
                                    Text("Gerar Aulas de Teste")
                                }
                                if (isDemoMode) {
                                    OutlinedButton(
                                        onClick = onClearTestSchedule,
                                        modifier = Modifier.testTag("btn_clear_test_schedule")
                                    ) {
                                        Text("Limpar")
                                    }
                                }
                            }
                            if (isDemoMode) {
                                Text(
                                    text = "Aulas de teste ativas: CEPAO (10h00, 90m) e LiceuOeiras (14h30, 60m)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Save Button
                Button(
                    onClick = {
                        onSave(docenteName, webhookUrl, sheetViewUrl)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_save_settings")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Definições")
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "InovLabs_logs v1.0.1 (Build 2)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
