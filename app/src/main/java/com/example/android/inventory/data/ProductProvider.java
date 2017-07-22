package com.example.android.inventory.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import com.example.android.inventory.data.ProductContract.ProductEntry;

public class ProductProvider extends ContentProvider {

    /** Tag for the log messages */
    public static final String LOG_TAG = ProductProvider.class.getSimpleName();

    /** URI matcher code for the content URI for the Products table */
    private static final int PRODUCTS = 100;

    /** URI matcher code for the content URI for a single Product in the table */
    private static final int PRODUCT_ID = 101;

    /**
     * UriMatcher object to match a content URI to a corresponding code.
     * The input passed into the constructor represents the code to return for the root URI.
     * It's common to use NO_MATCH as the input for this case.
     */
    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    // Static initializer. This is run the first time anything is called from this class.
    static {
        /*
         * The calls to addURI() go here, for all of the content URI patterns that the provider
         * should recognize. All paths added to the UriMatcher have a corresponding code to return
         * when a match is found.
         *
         * The content URI of the form "content://com.example.android.inventory/inventory" will
         * map to the integer code {@link #PRODUCTS}. This URI is used to provide access to
         * MULTIPLE rows of the inventory table.
         */
        uriMatcher.addURI(ProductContract.CONTENT_AUTHORITY, ProductContract.PATH_INVENTORY, PRODUCTS);

        /*
         * The content URI of the form "content://com.example.android.inventory/items/#"
         * will map to the integer code {@link #PRODUCT_ID}. This URI is used to provide access to ONE
         * single row of the inventory table.
         *
         * In this case, the "#" wildcard is used where "#" can be substituted for an integer.
         * For example, "content://com.example.android.inventory/items/3" matches, but
         * "content://com.example.android.inventory/inventory/items" (without a number at the end)
         * doesn't match.
         */
        uriMatcher.addURI(ProductContract.CONTENT_AUTHORITY, ProductContract.PATH_INVENTORY + "/#", PRODUCT_ID);
    }

    /** Database helper object */
    private ProductDbHelper dbHelper;

    @Override
    public boolean onCreate() {

        dbHelper = new ProductDbHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {

        // Get readable database.
        SQLiteDatabase database = dbHelper.getReadableDatabase();

        // This cursor will hold the result of the query.
        Cursor cursor;

        // Figure out if the URI matcher can match the URI to a specific code.
        int match = uriMatcher.match(uri);

        switch (match) {
            case PRODUCTS:
                /*
                 * For the PRODUCTS code, query the inventory table directly with the given
                 * projection, selection, selection arguments, and sort order. The cursor could
                 * contain multiple rows of the inventory table.
                 */
                cursor = database.query(ProductEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;

            case PRODUCT_ID:
                /*
                 * For the PRODUCT_ID code, extract out the ID from the URI.
                 * For an example URI such as:
                 * "content://com.example.android.inventory/items/3", the selection will be
                 * "_id=?" and the selection argument will be a String array containing the actual
                 * ID of 3 in this case.
                 *
                 * For every "?" in the selection, we need to have an element in the selection
                 * arguments that will fill in the "?". Since we have 1 question mark in the
                 * selection, we have 1 String in the selection arguments' String array.
                 */
                selection = ProductEntry._ID + "=?";

                selectionArgs = new String[]{
                        String.valueOf(ContentUris.parseId(uri))
                };

                /*
                 * This will perform a query on the inventory table where the _id equals 3 to return
                 * a Cursor containing that row of the table.
                 */
                cursor = database.query(ProductEntry.TABLE_NAME, projection, selection, selectionArgs,
                        null, null, sortOrder);
                break;
            default:
                throw new IllegalArgumentException("Cannot query unknown URI " + uri);
        }

        /*
         * Set notification URI on the Cursor, so we know what content URI the Cursor was created
         * for. If the data at this URI changes, then we know we need to update the Cursor.
         */
        cursor.setNotificationUri(getContext().getContentResolver(), uri);

        // Return the cursor
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {

        final int match = uriMatcher.match(uri);

        switch (match) {
            case PRODUCTS:
                return insertProduct(uri, contentValues);
            default:
                throw new IllegalArgumentException("Insertion is not supported for " + uri);
        }
    }

    /*
     * Insert a Product into the database with the given content values. Return the new content URI
     * for that specific row in the database.
     */
    private Uri insertProduct(Uri uri, ContentValues values) {

        // Check that the name is not null:
        String name = values.getAsString(ProductEntry.COLUMN_PRODUCT_NAME);

        if (name == null)
            throw new IllegalArgumentException("Product requires a name.");

        // Check that the quantity is not less than 0:
        Integer quantity = values.getAsInteger(ProductEntry.COLUMN_PRODUCT_QUANTITY);

        if (quantity != null && !ProductEntry.quantityPositive(quantity))
            throw new IllegalArgumentException("Negative product quantity not allowed.");

        // Check that the price is valid:
        Integer price = values.getAsInteger(ProductEntry.COLUMN_PRODUCT_PRICE);

        if (price != null && !ProductEntry.quantityPositive(price))
            throw new IllegalArgumentException("Negative product price not allowed.");

        // Get writable database:
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        // Insert the new Product with the given values.
        long id = database.insert(ProductEntry.TABLE_NAME, null, values);

        // if the ID is -1, then the insertion failed. Log an error and return null.
        if (id == -1) {
            Log.e(LOG_TAG, "Failed to insert row for " + uri);
            return null;
        }

        // Notify all listeners that the data has changed for the Product content URI
        getContext().getContentResolver().notifyChange(uri, null);

        // Return the new URI with the ID (of the newly inserted row) appended at the end
        return ContentUris.withAppendedId(uri, id);
    }

    @Override
    public int update(Uri uri, ContentValues contentValues,
                      String selection, String[] selectionArgs) {
        final int match = uriMatcher.match(uri);
        switch (match) {
            case PRODUCTS:
                return updateProduct(uri, contentValues, selection, selectionArgs);
            case PRODUCT_ID:
                /*
                 * for the PRODUCT_ID code, extract out the ID from the URI, so we know which row to
                 * update. Selection will be "_id=?" and selection arguments will be a String array
                 * containing the actual ID.
                 */
                selection = ProductEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                return updateProduct(uri, contentValues, selection, selectionArgs);
            default:
                throw new IllegalArgumentException("Update is not supported for " + uri);
        }
    }

    /*
    * Update the products in the database with the given content values. Apply the changes
    * to the rows specified in the selection and selection arguments (which could be 0 or 1 or more
    * products). Return the number of rows that were successfully updated.
    */
    private int updateProduct(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        /*
         * If the {@link ItemEntry#COLUMN_PRODUCT_NAME} key is present,
         * check that the name value is not null:
         */
        if (values.containsKey(ProductEntry.COLUMN_PRODUCT_NAME)) {
            String name = values.getAsString(ProductEntry.COLUMN_PRODUCT_NAME);
            if (name == null)
                throw new IllegalArgumentException("Product name required.");
        }

        /*
         * If the {@link ItemEntry#COLUMN_PRODUCT_PRICE} key is present,
         * check that the price value is not negative:
         */
        if (values.containsKey(ProductEntry.COLUMN_PRODUCT_PRICE)) {
            Integer price = values.getAsInteger(ProductEntry.COLUMN_PRODUCT_PRICE);
            if (price != null && !ProductEntry.quantityPositive(price))
                throw new IllegalArgumentException("Price has to be a positive value.");
        }

        // If there are no values to update, then don't try to update the database:
        if (values.size() == 0)
            return 0;

        // Otherwise, get writable database to update the data.
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        // Perform the update on the database and get the number of rows affected
        int rowsUpdated = database.update(ProductEntry.TABLE_NAME, values, selection, selectionArgs);

        /*
         * If 1 or more rows were updated,
         * then notify all listeners that the data at the given URI has changed:
         */
        if (rowsUpdated != 0)
            getContext().getContentResolver().notifyChange(uri, null);

        //Return the number of rows updated
        return rowsUpdated;

    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // Get writable database:
        SQLiteDatabase database = dbHelper.getWritableDatabase();

        // track the number of rows that were deleted
        int rowsDeleted;

        final int match = uriMatcher.match(uri);
        switch (match) {
            case PRODUCTS:
                // Delete all rows that match the selection and selection args:
                rowsDeleted = database.delete(ProductEntry.TABLE_NAME, selection, selectionArgs);
                break;
            case PRODUCT_ID:
                // Delete a single row given by the ID in the URI
                selection = ProductEntry._ID + "=?";
                selectionArgs = new String[]{String.valueOf(ContentUris.parseId(uri))};
                rowsDeleted = database.delete(ProductEntry.TABLE_NAME, selection, selectionArgs);
                break;
            default:
                throw new IllegalArgumentException("Deletion is not supported for " + uri);
        }

        /*
         * If 1 or more rows were deleted,
         * then notify all listeners that the data at the given URI was changed
         */
        if (rowsDeleted != 0)
            getContext().getContentResolver().notifyChange(uri, null);

        // Return the number of rows deleted:
        return rowsDeleted;
    }

    @Override
    public String getType(Uri uri) {
        final int match = uriMatcher.match(uri);
        switch (match) {
            case PRODUCTS:
                return ProductEntry.CONTENT_LIST_TYPE;
            case PRODUCT_ID:
                return ProductEntry.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalStateException("Unknown URI " + uri + " with match " + match);
        }
    }
}
