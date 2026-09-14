# Registo de Versões (Changelog)

## [1.0.1] - 2026-09-13

### Destaques & Correções Principais
- **Identidade Visual & Renomeação InovLabs_logs**:
  - Nome do projeto, metadados, títulos e recursos atualizados para **InovLabs_logs**.
  - Logótipo oficial da InovLabs (blocos ciano 'i' e verde lima 'N') implementado como ícone adaptativo da aplicação e integrado no cabeçalho do ecrã principal.
  - Paleta de cores oficial da InovLabs integrada no tema da aplicação (`InovCyanPrimary #00A4E4` e `InovGreenSecondary #8DC63F`), com suporte a modo claro e escuro.
- **Novo Webhook Predefinido do Google Sheets**:
  - Atualizado o URL predefinido para a nova implementação do Google Apps Script (`AKfycbzOdRgX5FErlY6dSgrI7yrsnUTSQVy2-Flno6xOjWgw0bELo6Jh7e6hYpJ8phyUgiBX/exec`).
- **Eliminação Sincronizada no Google Sheets**:
  - Ao desfazer ou eliminar uma presença, a aplicação envia um pedido ao webhook com a ação `deleteRow`, removendo também a entrada correspondente da folha no Google Sheets.
- **Gestão de Eliminação no Pop-up do Evento (Lápis)**:
  - Os botões de "Desfazer / Repor" foram retirados do ecrã principal para manter a interface limpa.
  - Para repor ou eliminar uma presença, acede-se ao ícone do lápis (✏️) no evento, onde se encontra a secção dedicada "Eliminar Registo / Repor".
- **Opção de Adicionar Duplicados**:
  - No diálogo de edição do evento, foi adicionada a opção "Permitir duplicado (nova linha na folha)", permitindo criar uma nova linha quando desejado em vez de atualizar a existente.
- **Interface de Definições mais Limpa (Dropdown do Script)**:
  - O código do Google Apps Script já não é exibido por defeito, ficando recolhido sob um botão expansível ("Ver Script Google Apps ▼") acompanhado pelo botão direto "Copiar Código".
- **Sincronização Fidedigna da Data da Aula**:
  - A confirmação de presença passa a registar a **data real da aula** (`startTimeMillis`), evitando que aulas de dias anteriores ou futuros fiquem com a data do momento em que foram marcadas.
- **Resolução de Instâncias e Recorrências do Calendário**:
  - `CalendarRepository` consulta `CalendarContract.Instances._ID`, garantindo identificadores únicos para cada ocorrência de aula recorrente semanal.
- **Rastreio de Versão**:
  - `versionCode = 2` e `versionName = "1.0.1"`.
  - Indicador visível de versão nas Definições da aplicação.

---

## [1.0.0] - Versão Inicial
- Seleção e leitura de eventos do Calendário do dispositivo.
- Confirmação direta de presença com cálculo automático de duração em minutos.
- Diálogo de edição de sumário, número de tempos e observações.
- Sincronização e exportação para Google Sheets via Webhook Apps Script.
- Modo de demonstração com turmas de teste (CEPAO e LiceuOeiras).
- Persistência local com Room Database (tabela `attendance_records`).
