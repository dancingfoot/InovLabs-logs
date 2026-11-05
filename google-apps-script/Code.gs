/**
 * InovLabs_logs - Google Sheets Webhook (Google Apps Script)
 * Versão: 1.0.7
 *
 * REGRA PRINCIPAL:
 * Cria SEMPRE uma nova linha física (sheet.insertRowAfter), inserindo uma nova
 * Row no sheet para garantir que NUNCA escreve por cima de nada já criado anteriormente.
 * A Linha 1 é estritamente protegida para os cabeçalhos.
 *
 * INSTRUÇÕES:
 * 1. Abra a sua folha de cálculo no Google Sheets no computador.
 * 2. Menu superior: Extensões -> Apps Script.
 * 3. Substitua TODO o código existente por este script limpo.
 * 4. Clique em "Implementar" (Deploy) -> "Gerir implementações" (Manage deployments).
 * 5. Clique no lápis (Editar) -> Versão: "Nova versão" -> "Implementar".
 */

function doGet(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheets = ss.getSheets().map(function(s) { return s.getName(); });
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      version: "1.0.7",
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
    var availableSheetNames = allSheets.map(function(s) { return s.getName(); });

    // Ação: listar abas
    if (data.action === "getSheets") {
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        availableSheets: availableSheetNames
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // 1. Procurar a aba correspondente
    var targetName = (data.sheetName || data.schoolName || data.professorEscola || data.tabName || data.eventTitle || "").toString().trim();
    var sheet = findSheet(allSheets, targetName);

    // Se não existir aba correspondente: não cria aba desconhecida, avisa a app
    if (!sheet) {
      return ContentService.createTextOutput(JSON.stringify({
        status: "warning_sheet_not_found",
        code: "SHEET_NOT_FOUND",
        message: "Nenhuma folha encontrada correspondente a '" + targetName + "'. Por favor confirme ou altere o nome da turma na app.",
        requestedName: targetName,
        availableSheets: availableSheetNames
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // 2. Ação: eliminar linha (quando o utilizador remove/desfaz um registo na app)
    if (data.action === "deleteRow") {
      var deleted = deleteLessonRow(sheet, data);
      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        action: "deleted",
        deleted: deleted,
        sheetUsed: sheet.getName(),
        availableSheets: availableSheetNames
      })).setMimeType(ContentService.MimeType.JSON);
    }

    // 3. Garantir cabeçalhos na Linha 1 se a folha estiver vazia
    var lastRow = sheet.getLastRow();
    if (lastRow === 0) {
      sheet.appendRow(["Nº Aula", "Docente InovLabs", "Professor Escola", "Data", "Sumário", "Minutos", "Obs"]);
      lastRow = 1;
    }

    // 4. CRIAÇÃO DE NOVA LINHA FÍSICA NO SHEET
    // Inserção explícita de uma nova linha (sheet.insertRowAfter):
    // Garante a 100% que NUNCA substitui nem escreve por cima de nada existente.
    sheet.insertRowAfter(lastRow);
    var newRow = lastRow + 1;

    // Escrever os dados na nova linha criada
    sheet.getRange(newRow, 1, 1, 7).setValues([[
      data.lessonNumber || "",
      data.docenteInovLabs || "",
      data.professorEscola || sheet.getName(),
      data.data || data.date || "",
      data.sumario || "",
      data.minutos || "",
      data.obs || ""
    ]]);

    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      sheetUsed: sheet.getName(),
      row: newRow,
      createdNewRow: true,
      availableSheets: availableSheetNames
    })).setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

// Função para localizar a aba de forma limpa e tolerante
function findSheet(sheets, targetName) {
  if (!targetName) return null;
  var target = targetName.toLowerCase().trim();

  // A. Correspondência exata
  for (var i = 0; i < sheets.length; i++) {
    if (sheets[i].getName().trim().toLowerCase() === target) {
      return sheets[i];
    }
  }

  // B. Correspondência ignorando acentos e caracteres especiais
  var cleanTarget = target.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]/g, "");
  if (!cleanTarget) return null;

  for (var i = 0; i < sheets.length; i++) {
    var cleanName = sheets[i].getName().trim().toLowerCase()
      .normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]/g, "");
    if (cleanName && (cleanName === cleanTarget || cleanName.indexOf(cleanTarget) !== -1 || cleanTarget.indexOf(cleanName) !== -1)) {
      return sheets[i];
    }
  }
  return null;
}

// Função para eliminar linha caso seja pedido
function deleteLessonRow(sheet, data) {
  var lastRow = sheet.getLastRow();
  if (lastRow <= 1) return false;

  var targetDate = (data.data || data.date || "").toString().trim();
  var lessonNum = parseInt(data.lessonNumber, 10) || 0;
  var values = sheet.getRange(2, 1, lastRow - 1, 5).getValues();

  for (var r = values.length - 1; r >= 0; r--) {
    var rowLesson = parseInt(values[r][0], 10) || 0;
    var rowDate = (values[r][3] ? values[r][3].toString() : "");

    var matchDate = targetDate && (rowDate.indexOf(targetDate) !== -1 || targetDate.indexOf(rowDate) !== -1);
    var matchLesson = lessonNum > 0 && rowLesson === lessonNum;

    if (matchLesson || matchDate) {
      sheet.deleteRow(r + 2);
      return true;
    }
  }
  return false;
}
