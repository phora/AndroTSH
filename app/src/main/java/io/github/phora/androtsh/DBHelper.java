package io.github.phora.androtsh;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.LinkedList;

/**
 * Created by phora on 8/19/15.
 */
public class DBHelper extends SQLiteOpenHelper {

    private static DBHelper sInstance;
    private final static String DATABASE_NAME = "uploads.db";
    private final static int DATABASE_VERSION = 1;

    public final static String TABLE_UPLOADS = "uploads";
    public final static String COLUMN_ID = "_id";
    public final static String COLUMN_PID = "_pid";
    public final static String BASE_URL = "base_url";
    public final static String UPLOAD_TOKEN  = "token";
    public final static String UPLOADED_FPATH = "upload_fpath";
    public final static String UPLOADED_DATE = "upload_date";

    public final static String TABLE_SERVERS = "servers";
    //also has _id and base_url fields
    public final static String SERVER_DURATION = "expiration";
    public final static String SERVER_DEFAULT = "is_default";

    private final static String UPLOADS_CREATE = "CREATE TABLE " + TABLE_UPLOADS +
        " ( " + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
        + COLUMN_PID + " INTEGER DEFAULT -1, "
        + BASE_URL + " TEXT NOT NULL, "
        + UPLOAD_TOKEN + " TEXT NOT NULL, "
        + UPLOADED_FPATH + " TEXT, "
        + UPLOADED_DATE + " INT)";

    private final static String SERVERS_CREATE = "CREATE TABLE " + TABLE_SERVERS +
            " ( " + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
            + BASE_URL + " TEXT NOT NULL UNIQUE, "
            + SERVER_DEFAULT + " INT, "
            + SERVER_DURATION + " INT)";

    public static synchronized DBHelper getInstance(Context context) {

        // Use the application context, which will ensure that you
        // don't accidentally leak an Activity's context.
        // See this article for more information: http://bit.ly/6LRzfx
        if (sInstance == null) {
            sInstance = new DBHelper(context.getApplicationContext());
        }
        return sInstance;
    }

    private DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public void onCreate(SQLiteDatabase database) {
        database.execSQL(UPLOADS_CREATE);
        database.execSQL(SERVERS_CREATE);

        ContentValues cv = new ContentValues();
        cv.put(BASE_URL, "https://transfer.sh");
        cv.put(SERVER_DURATION, 14);
        cv.put(SERVER_DEFAULT, true);

        database.insert(TABLE_SERVERS, null, cv);
    }

    public void addServer(String baseUrl, int server_duration)
    {
        ContentValues cv = new ContentValues();
        cv.put(BASE_URL, baseUrl);
        cv.put(SERVER_DURATION, server_duration);
        cv.put(SERVER_DEFAULT, false);

        getWritableDatabase().insertOrThrow(TABLE_SERVERS, null, cv);
    }

    public long addUpload(String baseUrl, String token, String fpath) {
        return addUpload(baseUrl, token, fpath, -1, true);
    }

    public long addUpload(String baseUrl, String token, String fpath, long pid, boolean includeDate) {
        ContentValues cv = new ContentValues();
        cv.put(BASE_URL, baseUrl);
        cv.put(UPLOAD_TOKEN, token);
        cv.put(UPLOADED_FPATH, fpath);
        cv.put(COLUMN_PID, pid);
        if (includeDate) {
            cv.put(UPLOADED_DATE, System.currentTimeMillis() / 1000);
        }

        SQLiteDatabase database = getWritableDatabase();
        return database.insert(TABLE_UPLOADS, null, cv);
    }

    //http://stackoverflow.com/questions/27807659/query-a-sqlite-database-using-an-array-android
    public static String makePlaceholders(int len) {
        if (len < 1) {
            // It will lead to an invalid query anyway ..
            throw new RuntimeException("No placeholders");
        } else {
            StringBuilder sb = new StringBuilder(len * 2 - 1);
            sb.append("?");
            for (int i = 1; i < len; i++) {
                sb.append(",?");
            }
            return sb.toString();
        }
    }

    public void trimHistory() {
        String THESE_EXPIRED_MAIN = String.format("SELECT %1$s.%2$s, %3$s FROM %1$s" +
                " JOIN %4$s on %4$s.%5$s=%1$s.%5$s" +
                " WHERE strftime('%%s', 'now')-%6$s >= 24*60*60*%7$s",
                TABLE_UPLOADS, COLUMN_ID, UPLOADED_FPATH, TABLE_SERVERS,
                BASE_URL, UPLOADED_DATE, SERVER_DURATION);

        SQLiteDatabase db = getWritableDatabase();

        Cursor c = db.rawQuery(THESE_EXPIRED_MAIN, null);
        LinkedList<String> idHolders = new LinkedList<String>();

        for (int i=0;i<c.getCount();i++) {
            c.moveToPosition(i);
            String pid = String.valueOf(c.getLong(c.getColumnIndex(COLUMN_ID)));
            idHolders.add(pid);
            db.delete(TABLE_UPLOADS, "_pid = ?", new String[]{pid});
        }
        c.close();
        if (idHolders.size() > 0) {
            int count = idHolders.size();
            String whereClause = String.format("_id IN (%s)", makePlaceholders(idHolders.size()));
            String[] whereArgs = new String[count];
            for (int i = 0; i < count; i++) {
                whereArgs[i] = idHolders.get(i).toString();
            }
            db.delete(TABLE_UPLOADS, whereClause, whereArgs);
        }
    }

    public Cursor batchAsArchive(Long pid, boolean getZip) {
        String DOWN_CONCAT;
        if (getZip) {
            DOWN_CONCAT = "('(' || group_concat(("+UPLOAD_TOKEN+" || '/' || "+UPLOADED_FPATH+")) || ').zip' ) AS tokpath";
        }
        else {
            DOWN_CONCAT = "('(' || group_concat(("+UPLOAD_TOKEN+" || '/' || "+UPLOADED_FPATH+")) || ').tar.gz' ) AS tokpath";
        }
        String[] fields = {DOWN_CONCAT};
        String whereClause = "_pid = ?";
        String[] whereArgs = new String[]{String.valueOf(pid)};

        return getReadableDatabase().query(TABLE_UPLOADS, fields, whereClause, whereArgs, null, null, null, null);
    }

    public Cursor batchAsArchive(Long[] ids, boolean getZip) {
        String DOWN_CONCAT;
        if (getZip) {
            DOWN_CONCAT = "( "+BASE_URL+" || '/(' || group_concat(("+UPLOAD_TOKEN+" || '/' || "+UPLOADED_FPATH+")) || ').zip' ) AS tokpath";
        }
        else {
            DOWN_CONCAT = "( "+BASE_URL+" || '/(' || group_concat(("+UPLOAD_TOKEN+" || '/' || "+UPLOADED_FPATH+")) || ').tar.gz' ) AS tokpath";
        }
        String[] fields = {DOWN_CONCAT};
        String whereClause = String.format("_id IN (%s)", makePlaceholders(ids.length));
        String[] whereArgs = new String[ids.length];


        for (int i=0; i<whereArgs.length; i++) {
            whereArgs[i] = String.valueOf(ids[i]);
        }
        //group by the base URL to ensure we make downloadable zips
        return getReadableDatabase().query(TABLE_UPLOADS, fields, whereClause, whereArgs, BASE_URL, null, null, null);
    }

    public Cursor getAllGroups() {
        String MAKE_URL_EXPR = "CASE WHEN (" + UPLOADED_FPATH + " IS NULL ) " +
                "THEN ( "+ BASE_URL + " || '/' || " + UPLOAD_TOKEN + " || ' (multiple)' ) " +
                "ELSE ( "+ BASE_URL + " || '/' || " + UPLOAD_TOKEN + " || '/' || " + UPLOADED_FPATH +" ) " +
                "END AS complete_url";
        String BIG_HEADER = "CASE WHEN (" + UPLOADED_FPATH + " IS NULL ) " +
                "THEN '(multiple)'  " +
                "ELSE " + UPLOADED_FPATH +
                " END AS header";
        String PRETTY_DATE = "DATETIME(" + UPLOADED_DATE + ", 'unixepoch', 'localtime') AS dt";
        String[] fields = {COLUMN_ID, MAKE_URL_EXPR, BIG_HEADER, PRETTY_DATE, BASE_URL};
        String[] whereArgs = {"-1"};
        return getReadableDatabase().query(TABLE_UPLOADS, fields, "_pid = ?", whereArgs,
                null, null, UPLOADED_DATE+" DESC", null);
    }

    public Cursor getUploadsInGroup(long pid) {
        String MAKE_URL_EXPR = "( "+ BASE_URL + " || '/' || " + UPLOAD_TOKEN + " || '/' || " + UPLOADED_FPATH +" ) as complete_url";
        String[] fields = {COLUMN_ID, MAKE_URL_EXPR, UPLOADED_FPATH+" as header"};
        String[] whereArgs = {String.valueOf(pid)};

        return getReadableDatabase().query(TABLE_UPLOADS, fields, "_pid = ?", whereArgs,
                null, null, UPLOADED_DATE+" DESC", null);
    }

    public void setDefaultServer(long newID, long oldID)
    {
        if (newID == oldID) {
            return;
        }

        String whereClause = "_id = ?";
        ContentValues cv = new ContentValues();
        String[] whereArgs = new String[1];

        whereArgs[0] = String.valueOf(newID);
        cv.put(SERVER_DEFAULT, true);
        getWritableDatabase().update(TABLE_SERVERS, cv, whereClause, whereArgs);

        if (oldID != -1) {
            cv.clear();
            cv.put(SERVER_DEFAULT, false);
            whereArgs[0] = String.valueOf(oldID);
            getWritableDatabase().update(TABLE_SERVERS, cv, whereClause, whereArgs);
        }
    }

    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        //nothing for now
    }

    public Cursor getAllServers()
    {
        return getAllServers(true);
    }

    public Cursor getAllServers(boolean ordering) {
        String[] fields = {COLUMN_ID, BASE_URL, "(" + SERVER_DURATION + " || ' days' ) as expiry", SERVER_DEFAULT };
        String orderBy = null;
        if (ordering) {
            orderBy = SERVER_DEFAULT+" DESC";
        }
        return getReadableDatabase().query(TABLE_SERVERS, fields, null, null,
                null, null, orderBy);
    }

    public void deleteServer(long oldID)
    {
        String whereClause = "_id = ?";
        String[] whereArgs = new String[1];
        whereArgs[0] = String.valueOf(oldID);

        getWritableDatabase().delete(TABLE_SERVERS, whereClause, whereArgs);
    }
}
