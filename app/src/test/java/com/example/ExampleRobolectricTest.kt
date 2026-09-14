package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AttendanceRecord
import com.example.data.repository.AttendanceRepository
import com.example.data.repository.CalendarRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("InovLabs_logs", appName)
    }

    @Test
    fun `extract school name correctly from event title`() {
        val title1 = "EB1 Dr. Alberto Iria - 3º A Robótica"
        val school1 = CalendarRepository.extractSchoolName(title1)
        assertEquals("EB1 Dr. Alberto Iria", school1)

        val title2 = "Escola Básica José Carlos da Maia: Aula Programação"
        val school2 = CalendarRepository.extractSchoolName(title2)
        assertEquals("Escola Básica José Carlos da Maia", school2)

        val title3 = "Colégio São Tomás"
        val school3 = CalendarRepository.extractSchoolName(title3)
        assertEquals("Colégio São Tomás", school3)
    }

    @Test
    fun `generate csv matches exact user spreadsheet template structure`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = AttendanceRepository(context, db.attendanceDao())

        val records = listOf(
            AttendanceRecord(
                id = 1L,
                lessonNumber = 1,
                docenteInovLabs = "Docente InovLabs",
                professorEscola = "EB1 Alberto Iria",
                date = "10/09/2026",
                sumario = "Introdução à robótica",
                minutos = 90,
                obs = "Turma 4ºB",
                eventId = 101L,
                syncedToGoogleSheets = false
            ),
            AttendanceRecord(
                id = 2L,
                lessonNumber = 2,
                docenteInovLabs = "Docente InovLabs",
                professorEscola = "EB 2,3 Dr. João Lúcio",
                date = "10/09/2026",
                sumario = "Montagem de circuitos",
                minutos = 60,
                obs = "",
                eventId = 102L,
                syncedToGoogleSheets = false
            )
        )

        val totalMinutes = 150
        val projectHours = 150
        val csv = repo.generateCsv(records, totalMinutes, projectHours)

        val lines = csv.lines()
        // Line 0: Header with Total Minutos
        assertTrue(lines[0].startsWith("Nº Aula,Docente InovLabs,Professor Escola,Data,Sumário,Minutos,Obs,,150,Total Minutos"))

        // Row 1 & 2: Data columns
        assertTrue(lines[1].contains("1,Docente InovLabs,EB1 Alberto Iria,10/09/2026,Introdução à robótica,90,Turma 4ºB"))
        assertTrue(lines[2].contains("2,Docente InovLabs,\"EB 2,3 Dr. João Lúcio\",10/09/2026,Montagem de circuitos,60"))

        // Row 4: % Execução (150 min / (150*60 min) = 150 / 9000 = 1.7% or 1.6%)
        assertTrue(csv.contains("% Execução"))
        // Row 5: Total de horas do Projeto
        assertTrue(csv.contains("150,Total de horas do Projeto"))
    }

    @Test
    fun `import and parse csv content correctly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = AttendanceRepository(context, db.attendanceDao())

        val rawCsv = """
Nº Aula,Docente InovLabs,Professor Escola,Data,Sumário,Minutos,Obs,,1110,Total Minutos
1,Docente InovLabs,EB1 Poeta Emiliano da Costa,05/09/2026,Apresentação e regras,90,Turma A,,
2,Docente InovLabs,Escola Secundária Pinheiro e Rosa,06/09/2026,Eletrónica básica,60,Sem faltas,,
,,,,,,,,0,% Execução
,,,,,,,,150,Total de horas do Projeto
        """.trimIndent()

        val (count, error) = repo.importCsvContent(rawCsv)
        assertEquals(null, error)
        assertEquals(2, count)
        assertEquals(150, repo.getProjectHours())
    }
}
