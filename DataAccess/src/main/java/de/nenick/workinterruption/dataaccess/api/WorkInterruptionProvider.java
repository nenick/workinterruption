package de.nenick.workinterruption.dataaccess.api;

import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.HashMap;

import de.nenick.workinterruption.dataaccess.database.SQLiteHelper;
import de.nenick.workinterruption.dataaccess.database.TaskTable;


public class WorkInterruptionProvider extends ContentProvider implements ContentProvider.PipeDataWriter<Cursor> {

    /** A projection map used to select columns from the database */
    private static HashMap<String, String> stasksProjectionMap;

    /** Standard projection for the interesting columns of a normal task. */
    private static final String[] READ_TASK_PROJECTION = new String[] {
            TaskTable._ID,               // Projection position 0, the task's id
            TaskTable.COL_CATEGORY,  // Projection position 1, the task's content
            TaskTable.COL_STARTED, // Projection position 2, the task's title
    };

    private static final int READ_task_task_INDEX = 1;
    private static final int READ_task_TITLE_INDEX = 2;

    /*
     * Constants used by the Uri matcher to choose an action based on the pattern
     * of the incoming URI
     */

    // The incoming URI matches the tasks URI pattern
    private static final int TASKS = 1;

    // The incoming URI matches the task ID URI pattern
    private static final int TASK_ID = 2;

    /** A UriMatcher instance  */
    private static final UriMatcher sUriMatcher;

    // Handle to a new DatabaseHelper.
    private SQLiteHelper mOpenHelper;


    /** A block that instantiates and sets static objects */
    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        // Add a pattern that routes URIs terminated with "tasks" to a TASKS operation
        sUriMatcher.addURI(WorkInterruption.AUTHORITY, WorkInterruption.PATH_TASK, TASKS);
        // Add a pattern that routes URIs terminated with "tasks" plus an integer
        // to a task ID operation
        sUriMatcher.addURI(WorkInterruption.AUTHORITY, WorkInterruption.PATH_TASK + "/#", TASK_ID);

        /* Creates and initializes a projection map that returns all columns */

        // Creates a new projection map instance. The map returns a column name
        // given a string. The two are usually equal.
        stasksProjectionMap = new HashMap<String, String>();
        stasksProjectionMap.put(TaskTable._ID, TaskTable._ID);
        stasksProjectionMap.put(TaskTable.COL_CATEGORY, TaskTable.COL_CATEGORY);
        stasksProjectionMap.put(TaskTable.COL_STARTED, TaskTable.COL_STARTED);
        stasksProjectionMap.put(TaskTable.COL_DURATION, TaskTable.COL_DURATION);
    }

    /**
     * Initializes the provider by creating a new DatabaseHelper. onCreate() is called
     * automatically when Android creates the provider in response to a resolver request from a
     * client.
     */
    @Override
    public boolean onCreate() {
        mOpenHelper = new SQLiteHelper(getContext());
        return true;
    }

    /**
     * This method is called when a client calls
     * {@link android.content.ContentResolver#query(Uri, String[], String, String[], String)}.
     * Queries the database and returns a cursor containing the results.
     *
     * @return A cursor containing the results of the query. The cursor exists but is empty if
     * the query returns no results or an exception occurs.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        // Constructs a new query builder and sets its table name
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(TaskTable.TABLE_NAME);

        /* Choose the projection and adjust the "where" clause based on URI pattern-matching. */
        switch (sUriMatcher.match(uri)) {
            // If the incoming URI is for tasks, chooses the tasks projection
            case TASKS:
                qb.setProjectionMap(stasksProjectionMap);
                break;

           /* If the incoming URI is for a single task identified by its ID, chooses the
            * task ID projection, and appends "_ID = <taskID>" to the where clause, so that
            * it selects that single task
            */
            case TASK_ID:
                qb.setProjectionMap(stasksProjectionMap);
                qb.appendWhere(
                        TaskTable._ID +    // the name of the ID column
                                "=" +
                                // the position of the task ID itself in the incoming URI
                                uri.getPathSegments().get(WorkInterruption.Task.PATH_POSITION_TASK_ID));
                break;

            default:
                // If the URI doesn't match any of the known patterns, throw an exception.
                throw new IllegalArgumentException("Unknown URI " + uri);
        }


        String orderBy;
        // If no sort order is specified, uses the default
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = WorkInterruption.Task.DEFAULT_SORT_ORDER;
        } else {
            // otherwise, uses the incoming sort order
            orderBy = sortOrder;
        }

        // Opens the database object in "read" mode, since no writes need to be done.
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();

       /*
        * Performs the query. If no problems occur trying to read the database, then a Cursor
        * object is returned; otherwise, the cursor variable contains null. If no records were
        * selected, then the Cursor object is empty, and Cursor.getCount() returns 0.
        */
        Cursor c = qb.query(
                db,            // The database to query
                projection,    // The columns to return from the query
                selection,     // The columns for the where clause
                selectionArgs, // The values for the where clause
                null,          // don't group the rows
                null,          // don't filter by row groups
                orderBy        // The sort order
        );

        // Tells the Cursor what URI to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    /**
     * This is called when a client calls {@link android.content.ContentResolver#getType(Uri)}.
     * Returns the MIME data type of the URI given as a parameter.
     *
     * @param uri The URI whose MIME type is desired.
     * @return The MIME type of the URI.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public String getType(Uri uri) {

        /* Chooses the MIME type based on the incoming URI pattern */
        switch (sUriMatcher.match(uri)) {

            // If the pattern is for tasks returns the general content type.
            case TASKS:
                return WorkInterruption.Task.CONTENT_TYPE;

            // If the pattern is for task IDs, returns the task ID content type.
            case TASK_ID:
                return WorkInterruption.Task.CONTENT_ITEM_TYPE;

            // If the URI pattern doesn't match any permitted patterns, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }


    /**
     * This describes the MIME types that are supported for opening a task
     * URI as a stream.
     */
    static ClipDescription TASK_STREAM_TYPES = new ClipDescription(null,
            new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN });

    /**
     * Returns the types of available data streams.  URIs to specific tasks are supported.
     * The application can convert such a task to a plain text stream.
     *
     * @param uri the URI to analyze
     * @param mimeTypeFilter The MIME type to check for. This method only returns a data stream
     * type for MIME types that match the filter. Currently, only text/plain MIME types match.
     * @return a data stream MIME type. Currently, only text/plan is returned.
     * @throws IllegalArgumentException if the URI pattern doesn't match any supported patterns.
     */
    @Override
    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        /**
         *  Chooses the data stream type based on the incoming URI pattern.
         */
        switch (sUriMatcher.match(uri)) {

            // If the pattern is for tasks return null. Data streams are not
            // supported for this type of URI.
            case TASKS:
                return null;

            // If the pattern is for task IDs and the MIME filter is text/plain, then return
            // text/plain
            case TASK_ID:
                return TASK_STREAM_TYPES.filterMimeTypes(mimeTypeFilter);

            // If the URI pattern doesn't match any permitted patterns, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }


    /**
     * Returns a stream of data for each supported stream type. This method does a query on the
     * incoming URI, then uses
     * {@link android.content.ContentProvider#openPipeHelper(Uri, String, Bundle, Object,
     * PipeDataWriter)} to start another thread in which to convert the data into a stream.
     *
     * @param uri The URI pattern that points to the data stream
     * @param mimeTypeFilter A String containing a MIME type. This method tries to get a stream of
     * data with this MIME type.
     * @param opts Additional options supplied by the caller.  Can be interpreted as
     * desired by the content provider.
     * @return AssetFileDescriptor A handle to the file.
     * @throws FileNotFoundException if there is no file associated with the incoming URI.
     */
    @Override
    public AssetFileDescriptor openTypedAssetFile(Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {

        // Checks to see if the MIME type filter matches a supported MIME type.
        String[] mimeTypes = getStreamTypes(uri, mimeTypeFilter);

        // If the MIME type is supported
        if (mimeTypes != null) {

            // Retrieves the task for this URI. Uses the query method defined for this provider,
            // rather than using the database query method.
            Cursor c = query(
                    uri,                    // The URI of a task
                    READ_TASK_PROJECTION,   // Gets a projection containing the task's ID, title,
                    // and contents
                    null,                   // No WHERE clause, get all matching records
                    null,                   // Since there is no WHERE clause, no selection criteria
                    null                    // Use the default sort order (modification date,
                    // descending
            );


            // If the query fails or the cursor is empty, stop
            if (c == null || !c.moveToFirst()) {

                // If the cursor is empty, simply close the cursor and return
                if (c != null) {
                    c.close();
                }

                // If the cursor is null, throw an exception
                throw new FileNotFoundException("Unable to query " + uri);
            }

            // Start a new thread that pipes the stream data back to the caller.
            return new AssetFileDescriptor(
                    openPipeHelper(uri, mimeTypes[0], opts, c, this), 0,
                    AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        // If the MIME type is not supported, return a read-only handle to the file.
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    /**
     * Implementation of {@link android.content.ContentProvider.PipeDataWriter}
     * to perform the actual work of converting the data in one of cursors to a
     * stream of data for the client to read.
     */
    @Override
    public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType,
                                Bundle opts, Cursor c) {
        // We currently only support conversion-to-text from a single task entry,
        // so no need for cursor data type checking here.
        FileOutputStream fout = new FileOutputStream(output.getFileDescriptor());
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(fout, "UTF-8"));
            pw.println(c.getString(READ_task_TITLE_INDEX));
            pw.println("");
            pw.println(c.getString(READ_task_task_INDEX));
        } catch (UnsupportedEncodingException e) {
            Log.w(getClass().getName(), e);
        } finally {
            c.close();
            if (pw != null) {
                pw.flush();
            }
            try {
                fout.close();
            } catch (IOException e) {
            }
        }
    }


    /**

     */
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        // Validates the incoming URI. Only the full provider URI is allowed for inserts.
        if (sUriMatcher.match(uri) != TASKS) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if(initialValues == null) {
            throw new IllegalArgumentException("Missing values for URI " + uri);
        }

        // Hold the extended record's values.
        ContentValues values = new ContentValues(initialValues);

        // If the values map doesn't contain the creation date, sets the value to the current time.
        if (values.containsKey(TaskTable.COL_STARTED) == false) {
            values.put(TaskTable.COL_STARTED, Calendar.getInstance().getTimeInMillis());
        }

        // If the values map doesn't contain task text, sets the value to an empty string.
        if (values.containsKey(TaskTable.COL_CATEGORY) == false) {
            throw new IllegalArgumentException("Missing value for category");
        }

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        // Performs the insert and returns the ID of the new task.
        long rowId = db.insert(
                TaskTable.TABLE_NAME,        // The table to insert into.
                TaskTable.COL_CATEGORY,  // A hack, SQLite sets this column value to null
                // if values is empty.
                values                           // A map of column names, and the values to insert
                // into the columns.
        );

        // If the insert succeeded, the row ID exists.
        if (rowId > 0) {
            // Creates a URI with the task ID pattern and the new row ID appended to it.
            Uri contentUri = ContentUris.withAppendedId(WorkInterruption.Task.CONTENT_ID_URI_BASE, rowId);

            // Notifies observers registered against this provider that the data changed.
            getContext().getContentResolver().notifyChange(contentUri, null);
            return contentUri;
        }

        // If the insert didn't succeed, then the rowID is <= 0. Throws an exception.
        throw new SQLException("Failed to insert row into " + uri);
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#delete(Uri, String, String[])}.
     * Deletes records from the database. If the incoming URI matches the task ID URI pattern,
     * this method deletes the one record specified by the ID in the URI. Otherwise, it deletes a
     * a set of records. The record or records must also match the input selection criteria
     * specified by where and whereArgs.
     *
     * If rows were deleted, then listeners are notified of the change.
     * @return If a "where" clause is used, the number of rows affected is returned, otherwise
     * 0 is returned. To delete all rows and get a row count, use "1" as the where clause.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalWhere;

        int count;

        // Does the delete based on the incoming URI pattern.
        switch (sUriMatcher.match(uri)) {

            // If the incoming pattern matches the general pattern for tasks, does a delete
            // based on the incoming "where" columns and arguments.
            case TASKS:
                count = db.delete(
                        TaskTable.TABLE_NAME,  // The database table name
                        where,                     // The incoming where clause column names
                        whereArgs                  // The incoming where clause values
                );
                break;

            // If the incoming URI matches a single task ID, does the delete based on the
            // incoming data, but modifies the where clause to restrict it to the
            // particular task ID.
            case TASK_ID:
                /*
                 * Starts a final WHERE clause by restricting it to the
                 * desired task ID.
                 */
                finalWhere =
                        TaskTable._ID +                              // The ID column name
                                " = " +                                          // test for equality
                                uri.getPathSegments().                           // the incoming task ID
                                        get(WorkInterruption.Task.PATH_POSITION_TASK_ID)
                ;

                // If there were additional selection criteria, append them to the final
                // WHERE clause
                if (where != null) {
                    finalWhere = finalWhere + " AND " + where;
                }

                // Performs the delete.
                count = db.delete(
                        TaskTable.TABLE_NAME,  // The database table name.
                        finalWhere,                // The final WHERE clause
                        whereArgs                  // The incoming where clause values.
                );
                break;

            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null);

        // Returns the number of rows deleted.
        return count;
    }

    /**
     * This is called when a client calls
     * {@link android.content.ContentResolver#update(Uri,ContentValues,String,String[])}
     * Updates records in the database. The column names specified by the keys in the values map
     * are updated with new data specified by the values in the map. If the incoming URI matches the
     * task ID URI pattern, then the method updates the one record specified by the ID in the URI;
     * otherwise, it updates a set of records. The record or records must match the input
     * selection criteria specified by where and whereArgs.
     * If rows were updated, then listeners are notified of the change.
     *
     * @param uri The URI pattern to match and update.
     * @param values A map of column names (keys) and new values (values).
     * @param where An SQL "WHERE" clause that selects records based on their column values. If this
     * is null, then all records that match the URI pattern are selected.
     * @param whereArgs An array of selection criteria. If the "where" param contains value
     * placeholders ("?"), then each placeholder is replaced by the corresponding element in the
     * array.
     * @return The number of rows updated.
     * @throws IllegalArgumentException if the incoming URI pattern is invalid.
     */
    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {

        // Opens the database object in "write" mode.
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        String finalWhere;

        // Does the update based on the incoming URI pattern
        switch (sUriMatcher.match(uri)) {

            // If the incoming URI matches the general tasks pattern, does the update based on
            // the incoming data.
            case TASKS:

                // Does the update and returns the number of rows updated.
                count = db.update(
                        TaskTable.TABLE_NAME, // The database table name.
                        values,                   // A map of column names and new values to use.
                        where,                    // The where clause column names.
                        whereArgs                 // The where clause column values to select on.
                );
                break;

            // If the incoming URI matches a single task ID, does the update based on the incoming
            // data, but modifies the where clause to restrict it to the particular task ID.
            case TASK_ID:
                // From the incoming URI, get the task ID
                String taskId = uri.getPathSegments().get(WorkInterruption.Task.PATH_POSITION_TASK_ID);

                /*
                 * Starts creating the final WHERE clause by restricting it to the incoming
                 * task ID.
                 */
                finalWhere =
                        TaskTable._ID +                              // The ID column name
                                " = " +                                          // test for equality
                                uri.getPathSegments().                           // the incoming task ID
                                        get(WorkInterruption.Task.PATH_POSITION_TASK_ID)
                ;

                // If there were additional selection criteria, append them to the final WHERE
                // clause
                if (where !=null) {
                    finalWhere = finalWhere + " AND " + where;
                }


                // Does the update and returns the number of rows updated.
                count = db.update(
                        TaskTable.TABLE_NAME, // The database table name.
                        values,                   // A map of column names and new values to use.
                        finalWhere,               // The final WHERE clause to use
                        // placeholders for whereArgs
                        whereArgs                 // The where clause column values to select on, or
                        // null if the values are in the where argument.
                );
                break;
            // If the incoming pattern is invalid, throws an exception.
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        /*Gets a handle to the content resolver object for the current context, and notifies it
         * that the incoming URI changed. The object passes this along to the resolver framework,
         * and observers that have registered themselves for the provider are notified.
         */
        getContext().getContentResolver().notifyChange(uri, null);

        // Returns the number of rows updated.
        return count;
    }

    /**
     * A test package can call this to get a handle to the database underlying Provider,
     * so it can insert test data into the database. The test case class is responsible for
     * instantiating the provider in a test context; {@link android.test.ProviderTestCase2} does
     * this during the call to setUp()
     *
     * @return a handle to the database helper object for the provider's data.
     */
    SQLiteHelper getOpenHelperForTest() {
        return mOpenHelper;
    }
}