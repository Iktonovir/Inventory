package com.example.android.inventory;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.example.android.inventory.data.ProductContract.ProductEntry;

/**
 * Displays list of Products that were entered and stored in the app.
 */
public class CatalogActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    /** Identifier for the product data loader */
    private static final int PRODUCT_LOADER = 0;

    /** Adapter for the ListView */
    private ProductCursorAdapter cursorAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);

        // Setup FAB to open EditorActivity
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CatalogActivity.this, EditorActivity.class);
                startActivity(intent);
            }
        });

        // Find the ListView which will be populated with the Product data
        ListView productListView = (ListView) findViewById(R.id.list);

        // find and set empty view on the ListView, so that it only shows when the list has 0 items.
        View emptyView = findViewById(R.id.empty_view);
        productListView.setEmptyView(emptyView);

        /*
         * Setup an Adapter to create a list item for each row of inventory data in the Cursor.
         * There is no inventory data yet (until the loader finishes) so pass in null of the Cursor
         */
        cursorAdapter = new ProductCursorAdapter(this, null);
        productListView.setAdapter(cursorAdapter);

        // Setup the item click listener
        productListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                // Create new intent to go to {@link EditorActivity}
                Intent intent = new Intent(CatalogActivity.this, EditorActivity.class);

                 /*
                  * form the content URI that represents the specific Product that was clicked on
                  * by appending the "id" (passed as input to this method) onto the
                  * {@link ItemEntry#CONTENT_URI}.
                  * For example, the URI would be
                  * "content://com.example.android.inventory/items/2"
                  * if the Product with ID 2 was clicked on.
                  */
                Uri currentProductUri = ContentUris.withAppendedId(ProductEntry.CONTENT_URI, id);

                // Set the URI on the data field of the intent
                intent.setData(currentProductUri);

                // Launch the {@link EditorActivity} to display the data for the current Product.
                startActivity(intent);
            }
        });

        // Kick off the loader
        getSupportLoaderManager().initLoader(PRODUCT_LOADER, null, this);

    }

    /**
     * The onSale method hooks up the onSale button in list_item.xml to CatalogActivity.
     */
    public void onSale(View view) {
        ContentResolver resolver = getContentResolver();
        int currentItemId = view.getId();

        Uri uri = Uri.parse("content://com.example.android.inventory/items/"
                + String.valueOf(currentItemId));

        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) cursor.moveToFirst();

        int quantityColumnIndex = 0;

        if (cursor != null)
            quantityColumnIndex = cursor.getColumnIndex(ProductEntry.COLUMN_PRODUCT_QUANTITY);

        String value = null;

        if (cursor != null) value = cursor.getString(quantityColumnIndex);

        if (cursor != null) cursor.close();

        int quantity = Integer.parseInt(value);

        ContentValues values = new ContentValues();

        // subtracts 1 from Quantity and display in the TextView
        values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, quantity - 1);

        // Negative quantity of products is not allowed:
        if (quantity <= 0) {
            // inform the user
            Toast.makeText(this, getString(R.string.cannot_have_negative_quanity),
                    Toast.LENGTH_SHORT).show();

            // set the quantity to 0 and display
            values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, 0);

        }

        resolver.update(uri, values, null, null);

    }

    /**
     * Helper method to insert hardcoded Product data into the database. For debugging purposes only.
     */
    private void insertProduct() {
        /*
         * Create a ContentValues object where column names are the keys,
         * and a Product's attributes are the values.
         */
        ContentValues values = new ContentValues();
        values.put(ProductEntry.COLUMN_PRODUCT_NAME, "Mugs");
        values.put(ProductEntry.COLUMN_PRODUCT_QUANTITY, 17);
        values.put(ProductEntry.COLUMN_PRODUCT_PRICE, 8);

        /*
         * Insert a new row for the Product into the provider using the ContentResolver.
         * Use the {@link ItemEntry#CONTENT_URI} to indicate that we want to insert into the
         * inventory database table. Receive the new content URI that will allow us to access the
         * Product's data in the future.
         */
        Uri newUri = getContentResolver().insert(ProductEntry.CONTENT_URI, values);
    }

    /**
     * Helper method to delete all Products in the database
     */
    private void deleteInventory() {

        int rowsDeleted = getContentResolver().delete(ProductEntry.CONTENT_URI, null, null);

        Log.v("CatalogActivity", rowsDeleted + " rows deleted from inventory");

        // Display Delete toast message.
        switch (rowsDeleted) {
            // no rows deleted toast message:
            case 0:
                Toast.makeText(this, getString(R.string.editor_delete_product_failed),
                        Toast.LENGTH_SHORT).show();
                break;
            // rows successfully deleted toast message:
            default:
                Toast.makeText(this, rowsDeleted + getString(R.string.rows_deleted),
                        Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu options from the res/menu/menu_catalog.xml file.
        // This adds menu items to the app bar.
        getMenuInflater().inflate(R.menu.menu_catalog, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // User clicked on a menu option in the app bar overflow menu
        switch (item.getItemId()) {
            // Respond to a click on the "Insert dummy data" menu option
            case R.id.action_insert_dummy_data:
                insertProduct();
                return true;
            // Respond to a click on the "Delete all entries" menu option
            case R.id.action_delete_all_entries:
                deleteInventory();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Define a projection that specifies the columns from the table we care about.
        String[] projection = {
                ProductEntry._ID,
                ProductEntry.COLUMN_PRODUCT_NAME,
                ProductEntry.COLUMN_PRODUCT_PRICE,
                ProductEntry.COLUMN_PRODUCT_QUANTITY };

        // This loader will execute the ContentProvider's query method on a background thread
        return new CursorLoader(this,  // Parent activity context
                ProductEntry.CONTENT_URI, // Provider content URI to query
                projection,            // Columns to include in the resulting Cursor
                null,                  // No selection clause
                null,                  // No selection arguments
                null);                 // Default sort order
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Update {@link ProductCursorAdapter} with this new cursor containing updated Product data
        cursorAdapter.swapCursor(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Callback called when the data needs to be deleted.
        cursorAdapter.swapCursor(null);
    }
}
