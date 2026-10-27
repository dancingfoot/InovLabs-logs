package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextAlign
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
    initialClassEndEnabled: Boolean = true,
    initialDailyDigestEnabled: Boolean = false,
    initialDailyDigestHour: Int = 18,
    initialDailyDigestMinute: Int = 0,
    hasNotificationPermission: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
    onSendTestNotification: () -> Unit = {},
    onGenerateTestSchedule: () -> Unit,
    onClearTestSchedule: () -> Unit,
    onSave: (
        docenteName: String,
        webhookUrl: String,
        sheetViewUrl: String,
        classEndEnabled: Boolean,
        dailyDigestEnabled: Boolean,
        digestHour: Int,
        digestMinute: Int
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var docenteName by remember { mutableStateOf(initialDocenteName) }
    var webhookUrl by remember { mutableStateOf(initialWebhookUrl) }
    var sheetViewUrl by remember { mutableStateOf(initialSheetViewUrl) }
    var showScriptInstructions by remember { mutableStateOf(true) }
    var showScriptCode by remember { mutableStateOf(false) }
    var classEndEnabled by remember { mutableStateOf(initialClassEndEnabled) }
    var dailyDigestEnabled by remember { mutableStateOf(initialDailyDigestEnabled) }
    var dailyDigestHour by remember { mutableStateOf(initialDailyDigestHour) }
    var dailyDigestMinute by remember { mutableStateOf(initialDailyDigestMinute) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    val sampleAppsScript = """
function doGet(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheets = ss.getSheets().map(function(s) { return s.getName(); });
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      availableSheets: sheets
    })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

function doPost(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var data = JSON.parse(e.postData.contents);
    var allSheets = ss.getSheets();
    
    // Obter lista de todas as abas existentes na folha de cálculo
    var availableSheetNames = allSheets.map(function(s) { return s.getName(); });
    
    // Suporte para ação de listar abas
    if (data.action === "getSheets") {
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        availableSheets: availableSheetNames
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    // Obter todos os possíveis nomes do evento / escola
    var candidates = [
      data.sheetName,
      data.tabName,
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
    
    // 2. SE NÃO EXISTIR ABA CORRESPONDENTE: NÃO CRIAR NOVA ABA, ENVIAR AVISO
    if (!matchedSheet) {
      var requestedName = (data.sheetName || data.eventTitle || data.eventName || "").toString().trim();
      return ContentService.createTextOutput(JSON.stringify({
        status: "warning_sheet_not_found",
        code: "SHEET_NOT_FOUND",
        message: "Nenhuma folha encontrada correspondente a '" + requestedName + "'. Por favor confirme ou altere o nome da turma na app.",
        requestedName: requestedName,
        availableSheets: availableSheetNames
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
        sheetUsed: sheet.getName(),
        availableSheets: availableSheetNames
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
    
    // 5. ENCONTRAR A PRÓXIMA LINHA COMPLETAMENTE NÃO UTILIZADA
    // (Verifica TODAS as colunas da linha, e não apenas a coluna 'Nº Aula')
    if (targetRow === -1) {
      var totalCols = Math.max(sheet.getLastColumn(), 7);
      if (lastRow > 1) {
        var allData = sheet.getRange(2, 1, lastRow - 1, totalCols).getValues();
        for (var r = 0; r < allData.length; r++) {
          var isRowEmpty = true;
          for (var c = 0; c < allData[r].length; c++) {
            var cellVal = allData[r][c];
            if (cellVal !== "" && cellVal !== null && cellVal !== undefined && String(cellVal).trim() !== "") {
              isRowEmpty = false;
              break;
            }
          }
          if (isRowEmpty) {
            targetRow = r + 2;
            break;
          }
        }
      }
      
      // Se não houver linha intermédia completamente livre, coloca na linha seguinte à última com dados (lastRow + 1)
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
      updated: (targetRow <= lastRow),
      availableSheets: availableSheetNames
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
                                                    "✨ Validação de Abas: O script encaminha o registo para a aba correspondente. Se o nome não corresponder a nenhuma folha, NÃO cria nova aba e devolve um aviso à app a pedir para confirmar/alterar o nome da turma, indicando as abas existentes.\n" +
                                                    "🔍 Linhas Livres: Cada aula é inserida na próxima linha completamente desocupada (verificando todas as colunas da folha, não apenas a coluna 'Nº Aula').",
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

                    // Notificações e Lembretes de Aulas
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Notificações e Lembretes de Aulas",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Permission status badge & action button
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (hasNotificationPermission) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                } else {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (hasNotificationPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (hasNotificationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (hasNotificationPermission) {
                                                "Permissões de notificação do Android ativas"
                                            } else {
                                                "Permissão de notificações em falta"
                                            },
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = if (hasNotificationPermission) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }

                                    if (!hasNotificationPermission) {
                                        Button(
                                            onClick = {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                    onRequestNotificationPermission()
                                                } else {
                                                    try {
                                                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {}
                                                }
                                            },
                                            modifier = Modifier.testTag("btn_request_notification_permission"),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Text("Ativar", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            // Test Notification Button
                            OutlinedButton(
                                onClick = onSendTestNotification,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_test_notification")
                            ) {
                                Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Testar Notificação Agora no Telemóvel", style = MaterialTheme.typography.labelMedium)
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Switch 1: Class End Notification
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Notificar no fim de cada aula",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Recebe um alerta assim que a aula terminar para preencher o sumário.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = classEndEnabled,
                                    onCheckedChange = { classEndEnabled = it },
                                    modifier = Modifier.testTag("switch_class_end_notification")
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Switch 2: Daily Digest / Scheduled reminder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Lembrete diário a uma hora fixa",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = "Se ainda tiveres aulas por registar, a app avisa-te no horário escolhido.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = dailyDigestEnabled,
                                    onCheckedChange = { dailyDigestEnabled = it },
                                    modifier = Modifier.testTag("switch_daily_digest_notification")
                                )
                            }

                            // Time Picker for Daily Digest
                            if (dailyDigestEnabled) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Hora do lembrete diário:",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }

                                            Text(
                                                text = String.format("%02d:%02d", dailyDigestHour, dailyDigestMinute),
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Quick Presets
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(
                                                17 to 0,
                                                17 to 30,
                                                18 to 0,
                                                18 to 30,
                                                19 to 0
                                            ).forEach { (h, m) ->
                                                val isSelected = dailyDigestHour == h && dailyDigestMinute == m
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            dailyDigestHour = h
                                                            dailyDigestMinute = m
                                                        }
                                                ) {
                                                    Text(
                                                        text = String.format("%02d:%02d", h, m),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        ),
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(vertical = 6.dp)
                                                    )
                                                }
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
                        onSave(
                            docenteName,
                            webhookUrl,
                            sheetViewUrl,
                            classEndEnabled,
                            dailyDigestEnabled,
                            dailyDigestHour,
                            dailyDigestMinute
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_save_settings")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Definições")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // App Information & Version Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "InovLabs_logs",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "v${com.example.BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Build: ${com.example.BuildConfig.VERSION_CODE} • Compilação: ${com.example.BuildConfig.BUILD_TIME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Registo de presenças & Google Sheets",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { showChangelogDialog = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Changelog", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Registo de Versões")
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "v1.0.4 (Atual - Build 5)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Validação de nome de aba no Sheets: Se o nome não existir, não cria abas extras e devolve aviso com as abas disponíveis para confirmação/alteração\n" +
                                       "• Deteção de linha desocupada melhorada: Verificação de todas as colunas para encontrar a próxima linha completamente livre\n" +
                                       "• Sugestão e seleção rápida de abas existentes nos diálogos de adicionar e editar aula",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "v1.0.3 (Build 4)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Sistema de Versionamento unificado (UI, APK, Docs, BuildConfig)\n" +
                                       "• Notificações no fim de cada aula para preenchimento de sumário\n" +
                                       "• Lembrete diário agendado com horário customizável\n" +
                                       "• Reprogramação automática de alarmes após reinício do telemóvel",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "v1.0.2 (Build 3)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Separação das ações 'Guardar' (local) e 'Submeter' (commit Google Sheets)\n" +
                                       "• Explicação clara sobre armazenamento local vs sincronização remota",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "v1.0.1 (Build 2)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "• Nova identidade visual InovLabs_logs\n" +
                                       "• Publicação automatizada de APKs no GitHub Releases\n" +
                                       "• Diálogo unificado para adicionar e editar aulas\n" +
                                       "• Eliminação sincronizada no Google Sheets (deleteRow)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showChangelogDialog = false }) {
                    Text("Fechar")
                }
            }
        )
    }
}
