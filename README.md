# InovLabs_logs

[![Versão](https://img.shields.io/badge/versão-v1.0.4-00A4E4.svg)](CHANGELOG.md)
[![Build](https://img.shields.io/badge/build-5-8DC63F.svg)](CHANGELOG.md)
[![Android](https://img.shields.io/badge/Android-7.0%2B-green.svg)](https://developer.android.com)
[![Licença](https://img.shields.io/badge/licença-Proprietária-lightgrey.svg)]()

Aplicação Android desenvolvida em **Kotlin** e **Jetpack Compose** para docentes e formadores da **InovLabs**. Permite a gestão, registo local e sincronização instantânea de presenças e sumários de aulas com folhas de cálculo do Google Sheets via Webhooks, com sistema inteligente de notificações ao fim de cada aula.

---

## 📱 Download do APK

| Ficheiro | Versão | Build | Descrição |
| :--- | :---: | :---: | :--- |
| **[`apk/InovLabs_logs-v1.0.4.apk`](apk/InovLabs_logs-v1.0.4.apk)** | `1.0.4` | `5` | APK versionado com as funcionalidades mais recentes. |
| **[`apk/InovLabs_logs.apk`](apk/InovLabs_logs.apk)** | `1.0.4` | `5` | APK estável com link fixo para atualizações diretas. |

Também disponível através dos [GitHub Releases](../../releases) com compilação e publicação automática a cada nova tag (`v*`).

---

## 🏷️ Sistema de Versionamento

A aplicação segue estritamente a convenção **Semantic Versioning (SemVer)**: `MAJOR.MINOR.PATCH`:

* **`MAJOR` (1)**: Alterações estruturais ou incompatibilidades de dados/API.
* **`MINOR` (0)**: Novas funcionalidades com retrocompatibilidade (ex: novos diálogos, sistemas de alarme).
* **`PATCH` (4)**: Correções de erros e otimizações.
* **`BUILD` (5)**: Código incremental único (`versionCode`) para controlo de atualizações no Android OS.

### Onde a Versão é Exibida
1. **Interface Principal (TopBar)**: Badge dinâmico `v1.0.4` junto ao logótipo oficial InovLabs.
2. **Definições da Aplicação**: Cartão informativo com a versão instalada, número de build e data/hora de compilação obtidos diretamente do `BuildConfig`.
3. **Visualizador de Changelog na App**: Botão "Changelog" nas Definições para consultar as novidades de cada versão sem sair da aplicação.
4. **Ficheiro APK**: Nomenclatura automática dos artefactos gerados: `InovLabs_logs-v1.0.4-b5.apk`.
5. **Documentação**: Badges e registo de alterações em `README.md` e `CHANGELOG.md`.

---

## ✨ Funcionalidades Principais

* **Integração com Google Calendar**:
  * Deteta e extrai automaticamente turmas e escolas dos eventos agendados (`CalendarContract`).
* **Sincronização com Google Sheets**:
  * Envio de registos via Webhook (Apps Script) diretamente para a aba correspondente da escola/projeto.
  * Mapeamento inteligente de abas existentes para evitar duplicações involuntárias.
* **Ações Claras de Edição**:
  * **Guardar**: Guarda as alterações localmente na base de dados Room sem sincronizar.
  * **Submeter**: Guarda e sincroniza de imediato com o Google Sheets.
* **Sistema de Notificações & Lembretes**:
  * Alerta sonoro e pop-up imediato ao término de cada aula para preenchimento de presenças.
  * Toque na notificação abre diretamente a janela da aula correspondente.
  * Lembrete diário agendado (com hora personalizável nas Definições) para aulas pendentes.
  * Reprogramação automática de alarmes ao reiniciar o telemóvel (`BootReceiver`).
* **Modo Offline & Persistência**:
  * Armazenamento local seguro e rápido através de **Room Database**.

---

## 🛠️ Como Compilar o Projeto

### Requisitos
* JDK 17 ou superior
* Android SDK (API 34 ou 36)
* Gradle 8+

### Comandos de Compilação
```bash
# Compilar APK de depuração (Debug)
gradle assembleDebug

# Copiar e etiquetar o APK versionado na pasta apk/
gradle copyVersionedApk

# Executar testes unitários e Robolectric
gradle :app:testDebugUnitTest
```

---

## 📄 Registo de Alterações
Para consultar o histórico detalhado de todas as versões anteriores, consulte o ficheiro [CHANGELOG.md](CHANGELOG.md).
