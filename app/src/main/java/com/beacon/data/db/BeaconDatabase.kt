package com.beacon.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class], version = 2, exportSchema = false)
abstract class BeaconDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        fun build(context: Context): BeaconDatabase =
            Room.databaseBuilder(context, BeaconDatabase::class.java, "beacon.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
