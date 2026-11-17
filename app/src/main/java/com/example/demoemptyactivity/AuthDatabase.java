package com.example.demoemptyactivity;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteConstraintException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "input_users.db";
    private static final int DB_VERSION = 1;

    public AuthDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "username TEXT NOT NULL COLLATE NOCASE UNIQUE, " +
                        "salt BLOB NOT NULL, " +
                        "password_hash BLOB NOT NULL)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Na razie nie ma migracji, bo to pierwsza wersja bazy.
    }

    private byte[] hashPassword(String password, byte[] salt)
            throws Exception {

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                120000,
                256
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return factory.generateSecret(spec).getEncoded();

        } finally {
            spec.clearPassword();
        }
    }

    public boolean register(String username, String password) {

        if (username == null || password == null) return false;

        username = username.trim();

        if (username.length() < 3 || username.length() > 30) return false;
        if (password.length() < 8) return false;

        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            byte[] passwordHash = hashPassword(password, salt);

            ContentValues values = new ContentValues();
            values.put("username", username);
            values.put("salt", salt);
            values.put("password_hash", passwordHash);

            SQLiteDatabase db = getWritableDatabase();
            long result = db.insertOrThrow("users", null, values);

            return result != -1;

        } catch (SQLiteConstraintException e) {
            return false;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean login(String username, String password) {

        if (username == null || password == null) return false;

        SQLiteDatabase db = getReadableDatabase();

        try (Cursor cursor = db.query(
                "users",
                new String[]{"salt", "password_hash"},
                "username = ?",
                new String[]{username.trim()},
                null, null, null)) {

            if (!cursor.moveToFirst()) {
                return false;
            }

            byte[] salt = cursor.getBlob(0);
            byte[] savedHash = cursor.getBlob(1);

            byte[] enteredHash = hashPassword(password, salt);

            return MessageDigest.isEqual(savedHash, enteredHash);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}