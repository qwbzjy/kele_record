package com.cola.record.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NightSleepDao {
    @Query("SELECT * FROM night_sleep WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<NightSleep>>
    @Query("SELECT * FROM night_sleep ORDER BY date DESC, id DESC")
    fun all(): Flow<List<NightSleep>>
    @Query("SELECT * FROM night_sleep ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<NightSleep>
    @Insert suspend fun insert(e: NightSleep): Long
    @Update suspend fun update(e: NightSleep)
    @Delete suspend fun delete(e: NightSleep)
    @Query("DELETE FROM night_sleep") suspend fun clear()
    @Insert suspend fun insertAll(list: List<NightSleep>)
}

@Dao
interface NapDao {
    @Query("SELECT * FROM nap WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Nap>>
    @Query("SELECT * FROM nap ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Nap>
    @Insert suspend fun insert(e: Nap): Long
    @Update suspend fun update(e: Nap)
    @Delete suspend fun delete(e: Nap)
    @Query("DELETE FROM nap") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Nap>)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Meal>>
    @Query("SELECT * FROM meal ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Meal>
    @Insert suspend fun insert(e: Meal): Long
    @Update suspend fun update(e: Meal)
    @Delete suspend fun delete(e: Meal)
    @Query("DELETE FROM meal") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Meal>)
}

@Dao
interface PoopDao {
    @Query("SELECT * FROM poop WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Poop>>
    @Query("SELECT * FROM poop ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Poop>
    @Insert suspend fun insert(e: Poop): Long
    @Update suspend fun update(e: Poop)
    @Delete suspend fun delete(e: Poop)
    @Query("DELETE FROM poop") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Poop>)
}

@Dao
interface MedCategoryDao {
    @Query("SELECT * FROM med_category ORDER BY id")
    fun all(): Flow<List<MedCategory>>
    @Query("SELECT * FROM med_category ORDER BY id")
    suspend fun allOnce(): List<MedCategory>
    @Insert suspend fun insert(e: MedCategory): Long
    @Update suspend fun update(e: MedCategory)
    @Delete suspend fun delete(e: MedCategory)
    @Query("DELETE FROM med_category") suspend fun clear()
    @Insert suspend fun insertAll(list: List<MedCategory>)
}

@Dao
interface MedLogDao {
    @Query("SELECT * FROM med_log WHERE date = :date ORDER BY slot")
    fun byDate(date: String): Flow<List<MedLog>>
    @Query("SELECT * FROM med_log WHERE categoryId = :categoryId AND date = :date AND slot = :slot LIMIT 1")
    suspend fun find(categoryId: Long, date: String, slot: String): MedLog?
    @Query("SELECT * FROM med_log ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<MedLog>
    @Insert suspend fun insert(e: MedLog): Long
    @Update suspend fun update(e: MedLog)
    @Delete suspend fun delete(e: MedLog)
    @Query("DELETE FROM med_log WHERE categoryId = :categoryId") suspend fun deleteByCategory(categoryId: Long)
    @Query("DELETE FROM med_log") suspend fun clear()
    @Insert suspend fun insertAll(list: List<MedLog>)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Exercise>>
    @Query("SELECT * FROM exercise ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Exercise>
    @Insert suspend fun insert(e: Exercise): Long
    @Update suspend fun update(e: Exercise)
    @Delete suspend fun delete(e: Exercise)
    @Query("DELETE FROM exercise") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Exercise>)
}

@Dao
interface OutingDao {
    @Query("SELECT * FROM outing WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Outing>>
    @Query("SELECT * FROM outing ORDER BY date DESC, id DESC")
    fun all(): Flow<List<Outing>>
    @Query("SELECT * FROM outing ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Outing>
    @Insert suspend fun insert(e: Outing): Long
    @Update suspend fun update(e: Outing)
    @Delete suspend fun delete(e: Outing)
    @Query("DELETE FROM outing") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Outing>)
}

@Dao
interface BathDao {
    @Query("SELECT * FROM bath WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Bath>>
    @Query("SELECT * FROM bath WHERE date BETWEEN :from AND :to ORDER BY date")
    fun between(from: String, to: String): Flow<List<Bath>>
    @Query("SELECT * FROM bath ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Bath>
    @Insert suspend fun insert(e: Bath): Long
    @Update suspend fun update(e: Bath)
    @Delete suspend fun delete(e: Bath)
    @Query("DELETE FROM bath") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Bath>)
}

@Dao
interface MoodDao {
    @Query("SELECT * FROM mood WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Mood>>
    @Query("SELECT * FROM mood ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Mood>
    @Insert suspend fun insert(e: Mood): Long
    @Update suspend fun update(e: Mood)
    @Delete suspend fun delete(e: Mood)
    @Query("DELETE FROM mood") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Mood>)
}

@Dao
interface CompanionshipDao {
    @Query("SELECT * FROM companionship WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Companionship>>
    @Query("SELECT * FROM companionship ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Companionship>
    @Insert suspend fun insert(e: Companionship): Long
    @Update suspend fun update(e: Companionship)
    @Delete suspend fun delete(e: Companionship)
    @Query("DELETE FROM companionship") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Companionship>)
}

@Dao
interface KindergartenDao {
    @Query("SELECT * FROM kindergarten WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Kindergarten>>
    @Query("SELECT * FROM kindergarten ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Kindergarten>
    @Insert suspend fun insert(e: Kindergarten): Long
    @Update suspend fun update(e: Kindergarten)
    @Delete suspend fun delete(e: Kindergarten)
    @Query("DELETE FROM kindergarten") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Kindergarten>)
}

@Dao
interface GameDao {
    @Query("SELECT * FROM game WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<Game>>
    @Query("SELECT * FROM game ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<Game>
    @Insert suspend fun insert(e: Game): Long
    @Update suspend fun update(e: Game)
    @Delete suspend fun delete(e: Game)
    @Query("DELETE FROM game") suspend fun clear()
    @Insert suspend fun insertAll(list: List<Game>)
}

@Dao
interface MedicalReportDao {
    @Query("SELECT * FROM medical_report ORDER BY date DESC, id DESC")
    fun all(): Flow<List<MedicalReport>>
    @Query("SELECT * FROM medical_report ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<MedicalReport>
    @Insert suspend fun insert(e: MedicalReport): Long
    @Update suspend fun update(e: MedicalReport)
    @Delete suspend fun delete(e: MedicalReport)
    @Query("DELETE FROM medical_report") suspend fun clear()
    @Insert suspend fun insertAll(list: List<MedicalReport>)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM book WHERE date = :date ORDER BY id DESC")
    fun byDate(date: String): Flow<List<BookRecord>>
    @Query("SELECT * FROM book ORDER BY date DESC, id DESC")
    suspend fun allOnce(): List<BookRecord>
    @Insert suspend fun insert(e: BookRecord): Long
    @Update suspend fun update(e: BookRecord)
    @Delete suspend fun delete(e: BookRecord)
    @Query("DELETE FROM book") suspend fun clear()
    @Insert suspend fun insertAll(list: List<BookRecord>)
}

@Dao
interface LinkItemDao {
    @Query("SELECT * FROM link_item ORDER BY createdAt DESC")
    fun all(): Flow<List<LinkItem>>
    @Query("SELECT * FROM link_item ORDER BY createdAt DESC")
    suspend fun allOnce(): List<LinkItem>
    @Insert suspend fun insert(e: LinkItem): Long
    @Update suspend fun update(e: LinkItem)
    @Delete suspend fun delete(e: LinkItem)
    @Query("DELETE FROM link_item") suspend fun clear()
    @Insert suspend fun insertAll(list: List<LinkItem>)
}

@Database(
    entities = [
        NightSleep::class, Nap::class, Meal::class, Poop::class,
        MedCategory::class, MedLog::class, Exercise::class, Bath::class,
        Outing::class, Mood::class, Companionship::class, Kindergarten::class,
        Game::class, MedicalReport::class, BookRecord::class, LinkItem::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun nightSleepDao(): NightSleepDao
    abstract fun napDao(): NapDao
    abstract fun mealDao(): MealDao
    abstract fun poopDao(): PoopDao
    abstract fun medCategoryDao(): MedCategoryDao
    abstract fun medLogDao(): MedLogDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun bathDao(): BathDao
    abstract fun outingDao(): OutingDao
    abstract fun moodDao(): MoodDao
    abstract fun companionshipDao(): CompanionshipDao
    abstract fun kindergartenDao(): KindergartenDao
    abstract fun gameDao(): GameDao
    abstract fun medicalReportDao(): MedicalReportDao
    abstract fun bookDao(): BookDao
    abstract fun linkItemDao(): LinkItemDao
}
