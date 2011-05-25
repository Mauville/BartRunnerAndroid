package com.dougkeen.bart.data;

import java.util.HashMap;

import com.dougkeen.bart.Constants;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

public class BartContentProvider extends ContentProvider {

	private static final UriMatcher sUriMatcher;
	private static HashMap<String, String> sFavoritesProjectionMap;

	private static final int FAVORITES = 1;
	private static final int FAVORITE_ID = 2;

	/**
	 * The default sort order for events
	 */
	private static final String DEFAULT_SORT_ORDER = FavoritesColumns.FROM_STATION.string
			+ " DESC";

	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(Constants.AUTHORITY, "favorites", FAVORITES);
		sUriMatcher.addURI(Constants.AUTHORITY, "favorites/#", FAVORITE_ID);

		sFavoritesProjectionMap = new HashMap<String, String>();
		sFavoritesProjectionMap.put(FavoritesColumns._ID.string,
				FavoritesColumns._ID.string);
		sFavoritesProjectionMap.put(FavoritesColumns.FROM_STATION.string,
				FavoritesColumns.FROM_STATION.string);
		sFavoritesProjectionMap.put(FavoritesColumns.TO_STATION.string,
				FavoritesColumns.TO_STATION.string);
	}

	private DatabaseHelper mDatabaseHelper;

	@Override
	public boolean onCreate() {
		mDatabaseHelper = new DatabaseHelper(getContext());
		return true;
	}

	@Override
	public String getType(Uri uri) {
		int match = sUriMatcher.match(uri);
		if (match == FAVORITES) {
			return Constants.FAVORITE_CONTENT_TYPE;
		} else if (match == FAVORITE_ID) {
			return Constants.FAVORITE_CONTENT_ITEM_TYPE;
		} else {
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs,
			String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();

		String orderBy = sortOrder;

		int match = sUriMatcher.match(uri);

		if (match == FAVORITES) {
			qb.setTables(DatabaseHelper.FAVORITES_TABLE_NAME);
			qb.setProjectionMap(sFavoritesProjectionMap);
		} else if (match == FAVORITE_ID) {
			qb.setTables(DatabaseHelper.FAVORITES_TABLE_NAME);
			qb.setProjectionMap(sFavoritesProjectionMap);
			qb.appendWhere(FavoritesColumns._ID + " = "
					+ uri.getPathSegments().get(1));
		} else {
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		// If no sort order is specified use the default
		if (TextUtils.isEmpty(orderBy)) {
			orderBy = DEFAULT_SORT_ORDER;
		}

		// Get the database and run the query
		Cursor cursor = qb.query(db, projection, selection, selectionArgs,
				null, null, orderBy);

		// Tell the cursor what uri to watch, so it knows when its source data
		// changes
		cursor.setNotificationUri(getContext().getContentResolver(), uri);
		return cursor;
	}

	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		// TODO: Hook this up to the REST service?

		ContentValues values;
		if (initialValues != null) {
			values = new ContentValues(initialValues);
		} else {
			values = new ContentValues();
		}

		SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();

		// Validate the requested uri
		int match = sUriMatcher.match(uri);
		if (match == FAVORITES) {
			long rowId = -1;
			Cursor cursor = db
					.query(DatabaseHelper.FAVORITES_TABLE_NAME,
							new String[] { FavoritesColumns._ID.string },
							FavoritesColumns.FROM_STATION + "=? AND "
									+ FavoritesColumns.TO_STATION + "=?",
							new String[] {
									values.getAsString(FavoritesColumns.FROM_STATION.string),
									values.getAsString(FavoritesColumns.TO_STATION.string) },
							null,
							null,
							null);
			try {
				if (cursor.moveToFirst()) {
					rowId = cursor.getLong(0);
				}
			} finally {
				CursorUtils.closeCursorQuietly(cursor);
			}
			if (rowId < 0) {
				rowId = db.insert(DatabaseHelper.FAVORITES_TABLE_NAME,
						FavoritesColumns.FROM_STATION.string, values);
			}
			if (rowId > 0) {
				Uri eventUri = ContentUris.withAppendedId(
						Constants.FAVORITE_CONTENT_URI, rowId);
				getContext().getContentResolver().notifyChange(eventUri, null,
						false);
				return eventUri;
			}
		} else {
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		throw new SQLException("Failed to insert row into " + uri);
	}

	@Override
	public int update(Uri uri, ContentValues values, String where,
			String[] whereArgs) {
		return 0;
		// No updating supported yet
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		// TODO: Sync with REST service?
		SQLiteDatabase db = mDatabaseHelper.getWritableDatabase();
		int count;
		int match = sUriMatcher.match(uri);
		if (match == FAVORITES) {
			count = db.delete(DatabaseHelper.FAVORITES_TABLE_NAME, where,
					whereArgs);
		} else if (match == FAVORITE_ID) {
			String favoriteId = uri.getPathSegments().get(1);
			count = db.delete(DatabaseHelper.FAVORITES_TABLE_NAME,
					FavoritesColumns._ID + " = "
							+ favoriteId
							+ (!TextUtils.isEmpty(where) ? " AND (" + where
									+ ')' : ""), whereArgs);
		} else {
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}
}