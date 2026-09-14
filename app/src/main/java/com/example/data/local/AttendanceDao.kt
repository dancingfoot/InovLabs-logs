package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records ORDER BY lessonNumber ASC, id ASC")
    fun getAllRecords(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE eventId = :eventId LIMIT 1")
    suspend fun getRecordByEventId(eventId: Long): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE eventId = :eventId")
    suspend fun getRecordsByEventId(eventId: Long): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE eventId = :eventId AND date = :date LIMIT 1")
    suspend fun getRecordByEventIdAndDate(eventId: Long, date: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE date = :date AND (sheetName = :sheetName OR professorEscola = :sheetName) AND sumario = :sumario LIMIT 1")
    suspend fun findDuplicateRecord(date: String, sheetName: String, sumario: String): AttendanceRecord?

    @Query("SELECT * FROM attendance_records WHERE date = :date AND (sheetName = :sheetName OR professorEscola = :sheetName) AND lessonNumber = :lessonNumber LIMIT 1")
    suspend fun findRecordByDateSheetAndLesson(date: String, sheetName: String, lessonNumber: Int): AttendanceRecord?

    @Query("SELECT COUNT(*) FROM attendance_records")
    fun getRecordCount(): Flow<Int>

    @Query("SELECT MAX(lessonNumber) FROM attendance_records")
    suspend fun getMaxLessonNumber(): Int?

    @Query("SELECT MAX(lessonNumber) FROM attendance_records WHERE sheetName = :sheetName OR professorEscola = :sheetName")
    suspend fun getMaxLessonNumberForSheet(sheetName: String): Int?

    @Query("SELECT SUM(minutos) FROM attendance_records")
    fun getTotalMinutes(): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: AttendanceRecord): Long

    @Update
    suspend fun updateRecord(record: AttendanceRecord)

    @Delete
    suspend fun deleteRecord(record: AttendanceRecord)

    @Query("DELETE FROM attendance_records WHERE id = :recordId")
    suspend fun deleteRecordById(recordId: Long)

    @Query("DELETE FROM attendance_records WHERE eventId = :eventId")
    suspend fun deleteRecordByEventId(eventId: Long)

    @Query("DELETE FROM attendance_records")
    suspend fun clearAll()
}
