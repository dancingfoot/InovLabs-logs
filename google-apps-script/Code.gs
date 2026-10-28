/**
 * InovLabs_logs - Google Sheets Webhook (Google Apps Script)
 * Versão: 1.0.5
 * 
 * ATUALIZAÇÃO 1.0.5:
 * - Proteção estrita da Linha 1 (cabeçalhos): nunca escreve na linha 1; os dados começam sempre na linha 2 ou seguinte.
 * - Verificação de entradas anteriores ignorando a Coluna A ("Nº Aula"), que pode conter números aleatórios.
 * - Deteção de duplicados com base na Data + Nome/Sumário/Turma.
 * - Envio de aviso de duplicado para a app notificar o utilizador e permitir escolher "Atualizar Existente" ou "Criar Nova Linha".
 * - Suporte completo a objetos Date nativos do Google Sheets e formatos DD/MM/AAAA.
 * - Verificação de todas as colunas para encontrar a próxima linha vazia.
 * 
 * INSTRUÇÕES PARA ATUALIZAR NO GOOGLE SHEETS:
 * 1. Abra a sua folha de cálculo no Google Sheets (no computador).
 * 2. Menu superior: Extensões -> Apps Script.
 * 3. Substitua TODO o código existente pelo código abaixo.
 * 4. Clique em "Implementar" (Deploy) -> "Gerir implementações" (Manage deployments).
 * 5. Clique no ícone de lápis (Editar) na implementação ativa:
 *    - Versão: selecione "Nova versão" (New version).
 *    - Clique em "Implementar" (Deploy).
 * 6. (O URL mantém-se o mesmo, mas a nova lógica fica ativa de imediato!)
 */

function doGet(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var sheets = ss.getSheets().map(function(s) { return s.getName(); });
    return ContentService.createTextOutput(JSON.stringify({
      status: "success",
      version: "1.0.5",
      availableSheets: sheets
    })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}

// Função para normalizar texto (ignora acentos, espaços, maiúsculas e pontuação)
function normalizeText(str) {
  if (!str) return "";
  return str.toString()
    .toLowerCase()
    .normalize("NFD").replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]/g, "");
}

// Função para normalizar datas (converte objetos Date, ISO e DD/MM/AAAA para formato padrão DD/MM/AAAA)
function normalizeDate(val) {
  if (!val) return "";
  if (val instanceof Date) {
    var d = val.getDate();
    var m = val.getMonth() + 1;
    var y = val.getFullYear();
    var pad = function(n) { return (n < 10 ? "0" : "") + n; };
    return pad(d) + "/" + pad(m) + "/" + y;
  }
  var s = val.toString().trim();
  // Se for formato YYYY-MM-DD
  var isoMatch = s.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})/);
  if (isoMatch) {
    var pad = function(n) { return (parseInt(n, 10) < 10 ? "0" : "") + parseInt(n, 10); };
    return pad(isoMatch[3]) + "/" + pad(isoMatch[2]) + "/" + isoMatch[1];
  }
  // Se for formato DD/MM/YYYY
  var dmyMatch = s.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})/);
  if (dmyMatch) {
    var pad = function(n) { return (parseInt(n, 10) < 10 ? "0" : "") + parseInt(n, 10); };
    return pad(dmyMatch[1]) + "/" + pad(dmyMatch[2]) + "/" + dmyMatch[3];
  }
  return s.replace(/[^0-9]/g, "");
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
    
    var matchedSheet = null;
    
    // 1. PROCURA A ABA CORRESPONDENTE AO NOME DO EVENTO / ESCOLA
    for (var c = 0; c < candidates.length; c++) {
      var candidate = (candidates[c] || "").toString().trim();
      if (!candidate) continue;
      
      var normCandidate = normalizeText(candidate);
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
        var normSheetName = normalizeText(allSheets[i].getName());
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
    var targetDateRaw = (data.data || data.date || "").toString().trim();
    var normTargetDate = normalizeDate(targetDateRaw);
    var targetSummary = (data.sumario || "").toString().trim();
    var normTargetSummary = normalizeText(targetSummary);
    var targetSchool = (data.professorEscola || data.schoolName || sheet.getName()).toString().trim();
    var normTargetSchool = normalizeText(targetSchool);
    var lessonNum = parseInt(data.lessonNumber, 10) || 0;
    var lastRow = sheet.getLastRow();
    
    // Assegurar cabeçalho na Linha 1 se a folha estiver totalmente vazia
    if (lastRow === 0) {
      sheet.getRange(1, 1, 1, 7).setValues([["Nº Aula", "Docente InovLabs", "Professor Escola", "Data", "Sumário", "Minutos", "Obs"]]);
      lastRow = 1;
    }
    
    // 3. AÇÃO: ELIMINAR LINHA (QUANDO DESFAZ OU ELIMINA REGISTO NA APP)
    if (data.action === "deleteRow") {
      var deleted = false;
      if (lastRow > 1) {
        var numRows = lastRow - 1;
        var values = sheet.getRange(2, 1, numRows, 5).getValues();
        for (var r = values.length - 1; r >= 0; r--) {
          var rowDateNorm = normalizeDate(values[r][3]);
          var rowSumarioNorm = normalizeText(values[r][4]);
          
          var matchDate = normTargetDate && (rowDateNorm === normTargetDate || rowDateNorm.indexOf(normTargetDate) !== -1);
          var matchSumario = normTargetSummary && (rowSumarioNorm.indexOf(normTargetSummary) !== -1 || normTargetSummary.indexOf(rowSumarioNorm) !== -1);
          
          if (matchDate && (matchSumario || normTargetSummary === "")) {
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
    
    // 4. VERIFICAÇÃO DE ENTRADA ANTERIOR / DUPLICADA
    // REGRA: Ignora a Coluna A ("Nº Aula") pois pode ter números aleatórios.
    // Verifica a Data (Coluna D / índice 3) e o Nome/Sumário/Turma (Colunas B, C, E).
    var existingRow = -1;
    var existingSummary = "";
    
    if (lastRow > 1) {
      var numRows = lastRow - 1;
      var values = sheet.getRange(2, 1, numRows, Math.max(sheet.getLastColumn(), 7)).getValues();
      
      for (var r = 0; r < values.length; r++) {
        var rowDateNorm = normalizeDate(values[r][3]);
        var rowDocente = normalizeText(values[r][1]);
        var rowEscola = normalizeText(values[r][2]);
        var rowSumarioNorm = normalizeText(values[r][4]);
        
        // Match de Data estrito (ignora coluna A)
        var isDateMatch = normTargetDate && (rowDateNorm === normTargetDate || (rowDateNorm.length > 5 && normTargetDate.length > 5 && (rowDateNorm.indexOf(normTargetDate) !== -1 || normTargetDate.indexOf(rowDateNorm) !== -1)));
        
        if (isDateMatch) {
          // Se na mesma data houver coincidência de sumário, de escola/turma, ou se já existe registo preenchido nesta aba de turma
          var isSummaryMatch = (normTargetSummary !== "" && rowSumarioNorm !== "" && (rowSumarioNorm.indexOf(normTargetSummary) !== -1 || normTargetSummary.indexOf(rowSumarioNorm) !== -1));
          var isSchoolMatch = (normTargetSchool !== "" && rowEscola !== "" && (rowEscola.indexOf(normTargetSchool) !== -1 || normTargetSchool.indexOf(rowEscola) !== -1));
          var isGeneralLessonOnDate = (rowSumarioNorm !== "" || rowEscola !== "");
          
          if (isSummaryMatch || isSchoolMatch || isGeneralLessonOnDate) {
            existingRow = r + 2;
            existingSummary = (values[r][4] ? values[r][4].toString() : "");
            break;
          }
        }
      }
    }
    
    // SE JÁ EXISTIR ENTRADA NA MESMA DATA:
    // A. Se não for 'forceUpdate' nem 'allowDuplicate', envia aviso para a app notificar o utilizador!
    if (existingRow > 1 && !data.forceUpdate && !data.allowDuplicate) {
      var displayName = targetSummary || targetSchool || sheet.getName();
      return ContentService.createTextOutput(JSON.stringify({
        status: "warning_duplicate_entry",
        code: "DUPLICATE_ENTRY",
        message: "Já existe uma aula registada para '" + displayName + "' na data " + targetDateRaw + " (linha " + existingRow + "). Deseja atualizar a linha existente ou criar uma nova linha?",
        sheetUsed: sheet.getName(),
        existingRow: existingRow,
        existingSummary: existingSummary,
        targetRow: existingRow,
        availableSheets: availableSheetNames
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    var targetRow = -1;
    
    // B. Se o utilizador pediu para ATUALIZAR a linha existente ('forceUpdate'):
    if (existingRow > 1 && data.forceUpdate) {
      targetRow = existingRow;
    }
    
    // 5. ENCONTRAR A PRÓXIMA LINHA COMPLETAMENTE NÃO UTILIZADA
    // (Verifica TODAS as colunas da linha, garantindo que NUNCA escreve na Linha 1 de cabeçalho)
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
      
      // Se não houver linha intermédia completamente livre, coloca após a última linha com dados
      if (targetRow === -1) {
        targetRow = Math.max(lastRow + 1, 2);
      }
    }
    
    // PROTEÇÃO CRÍTICA: targetRow NUNCA pode ser menor que 2 (a linha 1 é cabeçalho)
    if (targetRow < 2) {
      targetRow = 2;
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
      updated: (existingRow > 1 && data.forceUpdate),
      isDuplicateCreated: (existingRow > 1 && data.allowDuplicate),
      availableSheets: availableSheetNames
    })).setMimeType(ContentService.MimeType.JSON);
    
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}
