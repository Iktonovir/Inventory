package com.example.android.inventory.data;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import com.example.android.inventory.EditorActivity;

import com.example.android.inventory.data.ProductContract.ProductEntry;

/**
 * This class manages Cursor and ContentValues.
 */

public class Database {

    public EditorActivity.Product getProduct(Cursor cursor) {
        // if the cursor is null or there is less than 1 row in the cursor
        if (cursor == null || cursor.getCount() < 1)
            return null;

        /*
         * Proceed with moving to the first row of the cursor and reading data from it.
         * (This should be the only row in the cursor).
         */
        if (cursor.moveToFirst()) {
            // find the columns of Product attributes we're interested in:
            int nameColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_NAME);
            int priceColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_PRICE);
            int quantityColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_QUANTITY);
            int imageColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_IMAGE);

            // Extract out the value from the Cursor for the given column index
            String name = cursor.getString(nameColumnIndex);

            int quantity = cursor.getInt(quantityColumnIndex);
            int price = cursor.getInt(priceColumnIndex);

            String image = cursor.getString(imageColumnIndex);

            return new EditorActivity.Product(name, String.valueOf(price), String.valueOf(quantity), image);
        }
        return null;
    }

    public Loader<Cursor> getLoader(int id, Bundle args, EditorActivity activity, Uri currentProductUri) {
        /*
         * The editor displays all Product attributes.
         * Define a projection that contains all columns from the inventory table.
         */
        String[] projection = {
                ProductEntry._ID,
                ProductEntry.COLUMN_PRODUCT_NAME,
                ProductEntry.COLUMN_PRODUCT_PRICE,
                ProductEntry.COLUMN_PRODUCT_QUANTITY,
                ProductEntry.COLUMN_PRODUCT_IMAGE};

        // This loader will execute the ContentProvider's query method on a background thread
        return new CursorLoader(
                activity,           // Parent activity context
                currentProductUri, // Query the content URI for the current Product
                projection,     // Columns to include in the resulting Cursor
                null,           // No selection clause
                null,           // No selection arguments
                null);          // Default sort order
    }

    public Uri addProduct(ContentResolver contentResolver, EditorActivity.Product product) {


        return contentResolver.insert(ProductEntry.CONTENT_URI, getValues(product));
    }

    private boolean blankFields(EditorActivity.Product product) {
        /*
         * check if this is supposed to be a new Product and check if all the fields in the editor are
         * blank
         */
        return product == null && TextUtils.isEmpty(product.getName())
                && TextUtils.isEmpty(product.getPrice())
                && TextUtils.isEmpty(product.getQuantity())
                && TextUtils.isEmpty(product.getImageUri());
    }

    public int updateProduct(ContentResolver contentResolver, EditorActivity.Product product, Uri productUri) {
        return contentResolver.update(productUri, getValues(product), null, null);

    }

    private ContentValues getValues(EditorActivity.Product product) {
        /*
         * Read from fields. Use trim to eliminate leading or trailing white space
         */
        String nameString = product.getName();
        String priceString = product.getPrice();
        String quantityString = product.getQuantity();
        String imageString = product.getImageUri();

        if (blankFields(product)) return null;

        /*
         * Create a ContentValues object where column names are the keys, and Product attributes from
         * the editor are the values
         */
        ContentValues values = new ContentValues();
        values.put(ProductEntry.COLUMN_PRODUCT_NAME, nameString);
        values.put(ProductEntry.COLUMN_PRODUCT_PRICE, priceString);
        values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, quantityString);
        values.put(ProductEntry.COLUMN_PRODUCT_IMAGE, imageString);

        /*
         * If the quantity is not provided by the user, don't try to parse the string into an
         * integer value. Use 0 by default.
         */
        int quantity = 0;
        if (!TextUtils.isEmpty(quantityString)) {
            quantity = Integer.parseInt(quantityString);
        }
        values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, quantity);

        /*
         * If the price is not provided by the user, don't try to parse the string into an
         * integer value. Use 0 by default.
         */
        int price = 0;
        if (!TextUtils.isEmpty(priceString)) {
            price = Integer.parseInt(priceString);
        }
        values.put(ProductEntry.COLUMN_PRODUCT_PRICE, price);

        return values;
    }
}
