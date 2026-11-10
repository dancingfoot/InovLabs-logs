/**
 * InovLabs_logs - Google Sheets Webhook (Google Apps Script)
 * Versão: 1.0.8
 *
 * REGRAS PRINCIPAIS:
 * 1. A Coluna A ("Nº Aula") é TOTALMENTE IGNORADA para determinar a última linha usada.
 *    (Apenas as colunas B a G com dados reais de aulas são tidas em conta).
 * 2. Ao adicionar uma nova aula, CRIA SEMPRE UMA NOVA LINHA FÍSICA (sheet.insertRowAfter).
 *    Garante a 100% que NUNCA é posto nada por cima de uma aula já existente.
 * 3. A Linha 1 é estritamente protegida para os cabeçalhos.
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
      version: "1.0.8",
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

    // Se não existir aba correspondente: avisa a app
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
    if (sheet.getLastRow() === 0) {
      sheet.appendRow(["Nº Aula", "Docente InovLabs", "Professor Escola", "Data", "Sumário", "Minutos", "Obs"]);
    }

    // 4. DETERMINAR A ÚLTIMA LINHA UTILIZADA (IGNORANDO COMPLETAMENTE A COLUNA A "Nº Aula")
    var lastUsedRow = getLastUsedLessonRow(sheet);

    // 5. CRIAR SEMPRE UMA NOVA LINHA FÍSICA NO SHEET
    // Inserir uma nova linha física limpa logo após a última aula registada
    sheet.insertRowAfter(lastUsedRow);
    var targetRow = lastUsedRow + 1;

    // Determinar o número da aula
    var lessonNum = parseInt(data.lessonNumber, 10);
    if (!lessonNum || isNaN(lessonNum) || lessonNum <= 0) {
      lessonNum = targetRow - 1; // Ex: na linha 2 é a aula 1, na linha 3 é a aula 2
    }

    // 6. Escrever os dados na nova linha criada (nunca escreve por cima de nada)
    sheet.getRange(targetRow, 1, 1, 7).setValues([[
      lessonNum,
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
      row: targetRow,
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

/**
 * Encontra a última linha que contém uma aula real.
 * REGRA CRÍTICA: A Coluna A ("Nº Aula") é TOTALMENTE IGNORADA, pois pode conter
 * números aleatórios, fórmulas ou contadores pré-preenchidos.
 * Verifica apenas as colunas B até G (Docente, Escola, Data, Sumário, Minutos, Obs).
 */
function getLastUsedLessonRow(sheet) {
  var maxRows = sheet.getMaxRows();
  var maxCols = sheet.getMaxColumns();
  if (maxRows <= 1 || maxCols < 2) return 1;

  // Inspecionar da Coluna 2 (B) até à 7 (G) ou última coluna existente
  var colsToCheck = Math.max(Math.min(maxCols - 1, 6), 1);
  // Verificar todas as linhas até ao máximo da folha (ou até 1000 linhas)
  var numRowsToCheck = Math.min(maxRows, 1000);
  if (numRowsToCheck <= 1) return 1;

  var rangeValues = sheet.getRange(1, 2, numRowsToCheck, colsToCheck).getValues();

  // Percorre de baixo para cima à procura da última linha com conteúdo real nas colunas B..G
  for (var r = rangeValues.length - 1; r >= 1; r--) { // r=0 é a linha 1 (cabeçalhos)
    var row = rangeValues[r];
    for (var c = 0; c < row.length; c++) {
      var val = row[c];
      if (val !== null && val !== undefined && String(val).trim() !== "") {
        return r + 1; // Linha real no Google Sheets (1-based)
      }
    }
  }

  return 1; // Apenas a linha 1 de cabeçalho está ocupada
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
