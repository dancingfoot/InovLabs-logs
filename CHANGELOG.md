# Registo de Versões (Changelog)

## [1.0.3] - 2026-10-23

### Destaques & Correções Principais
- **Sistema Completo de Notificações e Lembretes de Aulas**:
  - **Avisos no Fim de Cada Aula**: Notificação automática precisa assim que cada aula termina, convidando a preencher o sumário e confirmar presenças de imediato.
  - **Abertura Direta via Notificação**: Tocar na notificação abre a aplicação e aciona de imediato a janela da aula correspondente.
  - **Cancelamento Inteligente**: Se uma aula já tiver sido marcada ou guardada antes de terminar, o alarme e a notificação dessa aula são cancelados automaticamente.
  - **Lembrete Diário Agendado com Horário Personalizado**: Opção para receber um resumo diário à hora escolhida (ex: 17:00, 17:30, 18:00, 18:30, 19:00 ou hora à medida) caso existam aulas pendentes por registar nesse dia.
  - **Secção nas Definições ("Notificações e Lembretes de Aulas")**:
    - Indicador de estado da permissão de notificações do Android (`POST_NOTIFICATIONS`).
    - Botão para ativar/solicitar permissões de notificação no Android.
    - Botão "Testar Notificação Agora no Telemóvel" para validação imediata de som/vibração/pop-up.
    - Interruptores independentes para ligar/desligar alertas de fim de aula e lembrete diário.
  - **Persistência após Reinício**: Registado `BootReceiver` para reprogramar automaticamente os alarmes das aulas do dia caso o telemóvel reinicie.
- **Incremento de Versão**:
  - Atualizado para a versão **1.0.3** (Build 4).

## [1.0.2] - 2026-10-22

### Destaques & Correções Principais
- **Separação das Ações de Edição: Guardar (Local) e Submeter (Commit)**:
  - Na janela de edição de aula/presença, o botão único anterior foi dividido em dois botões específicos:
    - **Guardar**: Guarda todas as alterações e detalhes da aula (escola, sumário, data, duração, notas) localmente na aplicação e base de dados Room sem efetuar envio para a folha de cálculo Google Sheets.
    - **Submeter**: Guarda as alterações e submete/sincroniza a presença diretamente para a respetiva aba no Google Sheets via Webhook.
  - Adicionado cartão informativo explicativo com a distinção entre as duas operações.
- **Incremento de Versão**:
  - Atualizado para a versão **1.0.2** (Build 3).

## [1.0.1] - 2026-09-13

### Destaques & Correções Principais
- **Publicação Automática de APK em GitHub Releases**:
  - Adicionado fluxo automatizado do GitHub Actions (`.github/workflows/release.yml`) que compila e anexa automaticamente o ficheiro APK (`InovLabs_logs.apk` e `InovLabsATTENDANCE.apk`) aos releases do GitHub sempre que uma nova tag (`v*`) for criada ou um release for publicado.
  - Atualizado o APK compilado mais recente diretamente na pasta do repositório (`apk/InovLabs_logs.apk` e `apk/InovLabsATTENDANCE.apk`).
- **Nova Janela de Adicionar Aula / Presença**:
  - A janela para adicionar aula passa a ter a mesma estrutura, estilo e opções da janela detalhada ("Editar Aula / Presença"):
    - Campo de **Escola / Aba destino no Sheets** (com ícone e placeholder).
    - Campo de **Sumário da Aula** (ex: Robótica Educativa).
    - Linha com **Data (DD/MM/AAAA)** e **Minutos**, acompanhada por **Atalhos rápidos** (45m, 60m, 90m, 120m).
    - Campo de **Docente InovLabs** e campo de **Observações (Turma, ocorrências...)**.
    - Sem a secção de duplicados, mantendo o formulário focado e limpo.
    - Botões de ação completos: **Guardar e Marcar Assistida** (com sincronização imediata no Google Sheets), **Não Assistida**, **Agendar** (pendente) e **Cancelar**.
- **Resolução do Erro KSP NullPointerException (Issue #2763)**:
  - Corrigida a exceção `Exception in thread "AWT-EventQueue-0" java.lang.NullPointerException: Cannot invoke "ksp.com.intellij.openapi.application.Application.getService(java.lang.Class)"` ao atualizar o plugin KSP para a versão estável `2.3.6` e desativar o processador codegen não utilizado.
  - Atualizado o teste unitário `ExampleRobolectricTest` para validar a nova designação `InovLabs_logs`.
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
