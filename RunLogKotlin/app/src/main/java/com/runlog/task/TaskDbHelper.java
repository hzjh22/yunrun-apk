package com.runlog.task;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class TaskDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "runlog_tasks.db";
    public static final int DB_VERSION = 1;

    public TaskDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS tasks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "file_name TEXT NOT NULL UNIQUE,"
                + "source_dir TEXT NOT NULL,"
                + "task_json TEXT NOT NULL,"
                + "distance_km REAL NOT NULL,"
                + "duration_seconds INTEGER NOT NULL,"
                + "pace REAL NOT NULL,"
                + "points_count INTEGER NOT NULL,"
                + "manage_count INTEGER NOT NULL,"
                + "fingerprint TEXT NOT NULL UNIQUE,"
                + "duplicate_count INTEGER NOT NULL DEFAULT 1,"
                + "use_count INTEGER NOT NULL DEFAULT 0,"
                + "created_at INTEGER NOT NULL,"
                + "updated_at INTEGER NOT NULL"
                + ")");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_updated_at ON tasks(updated_at DESC)");
        db.execSQL("CREATE TABLE IF NOT EXISTS kv ("
                + "key TEXT PRIMARY KEY,"
                + "value TEXT NOT NULL"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onCreate(db);
    }
}
