package com.example.android.inventory;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.v4.app.NavUtils;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.inventory.data.Database;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Allows user to create a new Product or edit an existing one.
 */
public class EditorActivity extends AppCompatActivity implements
        LoaderManager.LoaderCallbacks<Cursor> {

    /** Identifier for the Product data loader */
    private static final int EXISTING_INVENTORY_LOADER = 0;

    /** Content URI for the existing Product */
    private Uri currentProductUri;

    /** EditText field to enter Product name */
    private EditText nameEditText;

    /** EditText field to enter product price */
    private EditText priceEditText;

    private static final String FILE_PROVIDER_AUTHORITY =
            "com.example.inventory.data.ProductContract.ItemEntry";

    private static final int IMAGE_REQUEST = 0;

    /** Product Image variables */
    private ImageView productImage;
    private Bitmap bitmap;
    private Uri uri;
    private boolean galleryImage = false;
    private String uriString;

    private TextView productQuantityText;

    private Database database = new Database();

    /**
     * Boolean flag that keeps track of whether the Product has been edited (true) or not (false)
     */
    private boolean productHasChanged = false;

    /**
     * OnTouchListener that listens for any user touches on a View, implying that they are modifying
     * the view, and we accordingly change the productHasChanged boolean to true.
     */
    private View.OnTouchListener touchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            productHasChanged = true;
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);

        // Find all relevant views that we will need to read user input from:
        nameEditText = (EditText) findViewById(R.id.edit_product_name);
        priceEditText = (EditText) findViewById(R.id.edit_product_price);

        ImageButton plusButton = (ImageButton) findViewById(R.id.plus);
        ImageButton minusButton = (ImageButton) findViewById(R.id.minus);
        ImageButton addImageButton = (ImageButton) findViewById(R.id.add_photo);

        productQuantityText = (TextView) findViewById(R.id.quantity);
        productImage = (ImageView) findViewById(R.id.product_image);

        /*
         * Setup OnTouchListeners on all the input fields, so we can determine if the user has
         * touched or modified them. This will let us know if there are unsaved changes or not, if
         * the user tries to leave the editor without saving.
         */
        nameEditText.setOnTouchListener(touchListener);
        priceEditText.setOnTouchListener(touchListener);

        plusButton.setOnTouchListener(touchListener);
        minusButton.setOnTouchListener(touchListener);
        addImageButton.setOnTouchListener(touchListener);

        newProduct();
    }

    private void newProduct() {

        Intent intent = getIntent();
        currentProductUri = intent.getData();
        uri = intent.getData();

        // A new Product is created if the intent doesn't contain a Product content URI
        if (currentProductUri == null) {
            // Change app bar to "Add Product"
            setTitle(getString(R.string.editor_activity_new_product));

            /*
             * Invalidate the options menu, so the "Delete" menu option can be hidden as it doesn't
             * make sense to have the option to delete a Product that hasn't been created yet.
             */
            invalidateOptionsMenu();
        } else {
            // Or we have an existing Product. Change app bar to "Edit Product"
            setTitle(getString(R.string.editor_activity_edit_product));

            /*
             * Initialize a loader to read the Product data from the database and display the current
             * values in the editor
             */
            getLoaderManager().initLoader(EXISTING_INVENTORY_LOADER, null, this);
        }
    }

    public void decreaseQuantity(View view) {

        // Parse String in the database to int, subtract 1, convert back to String
        String quantity = productQuantityText.getText().toString().trim();

        int q = Integer.parseInt(quantity);

        if (q > 0) {
            q--;
            String finalQuantity = String.valueOf(q);
            productQuantityText.setText(finalQuantity);
        } else
            Toast.makeText(this, getString(R.string.cannot_have_negative_quanity),
                    Toast.LENGTH_SHORT).show();
    }

    public void increaseQuantity(View view) {

        // Parse String in the database to int, add 1, convert back to String
        String quantity = productQuantityText.getText().toString().trim();

        int q = Integer.parseInt(quantity);

        q++;

        String finalQuantity = String.valueOf(q);

        productQuantityText.setText(finalQuantity);
    }

    public void addPhoto(View view) {

        Intent intent;
        intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Choose Photo"), IMAGE_REQUEST);
    }

    // When the Order button is clicked:
    public void createEmail(View view) {

        String subject = getString(R.string.order_summary_subject);

        String message = getString(R.string.restock_products_email)
                + nameEditText.getText().toString().trim() + "\n";

        message = message + getString(R.string.quantity_email)
                + productQuantityText.getText().toString().trim();

        Intent sendOrder = new Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                "mailto", "example@gmail.com", null));

        sendOrder.putExtra(Intent.EXTRA_SUBJECT, subject);
        sendOrder.putExtra(Intent.EXTRA_TEXT, message);

        if (sendOrder.resolveActivity(getPackageManager()) != null)
            startActivity(Intent.createChooser(sendOrder, "Send email..."));
    }

    public void onSale(View view) {

        Toast.makeText(this, R.string.sale_button_pressed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCodes, Intent resultData) {

        if (resultCodes == Activity.RESULT_OK && resultData != null) {
            uri = resultData.getData();
            bitmap = getBitmapFromCurrentProductURI(uri);
            productImage.setImageBitmap(bitmap);
            uriString = getShareableImageUri().toString();
            galleryImage = true;

        }
    }

    private Bitmap getBitmapFromCurrentProductURI(Uri uri) {

        ParcelFileDescriptor parcelFileDescriptor = null;

        try {

            parcelFileDescriptor = getContentResolver().openFileDescriptor(uri, "r");
            FileDescriptor fileDescriptor = null;

            if (parcelFileDescriptor != null)
                fileDescriptor = parcelFileDescriptor.getFileDescriptor();

            Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);

            if (parcelFileDescriptor != null) parcelFileDescriptor.close();

            return image;

        } catch (Exception e) {

            return null;

        } finally {

            try {

                if (parcelFileDescriptor != null) parcelFileDescriptor.close();

            } catch (IOException e) {

                e.printStackTrace();
            }
        }
    }

    public Uri getShareableImageUri() {

        Uri imagesUri;

        if (galleryImage) {
            String filename = PathFinder();
            savingInFile(getCacheDir(), filename, bitmap, Bitmap.CompressFormat.JPEG, 100);
            File imagesFile = new File(getCacheDir(), filename);

            imagesUri = FileProvider.getUriForFile(this, FILE_PROVIDER_AUTHORITY, imagesFile);
        } else {

            imagesUri = uri;
        }
        return imagesUri;
    }

    public String PathFinder() {

        Cursor returnCursor =
                getContentResolver().query
                        (uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);

        if (returnCursor != null) returnCursor.moveToFirst();

        String fileNames = null;

        if (returnCursor != null) fileNames = returnCursor.getString
                (returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));

        if (returnCursor != null) returnCursor.close();

        return fileNames;
    }

    public boolean savingInFile(File dir, String fileName, Bitmap bm, Bitmap.CompressFormat format,
                                int quality) {

        File imagesFile = new File(dir, fileName);

        FileOutputStream fileOutputStream = null;

        try {

            fileOutputStream = new FileOutputStream(imagesFile);
            bm.compress(format, quality, fileOutputStream);
            fileOutputStream.close();

            return true;

        } catch (IOException e) {

            if (fileOutputStream != null) try {

                fileOutputStream.close();

            } catch (IOException e1) {

                e1.printStackTrace();
            }
        }
        return false;
    }

    private void saveProduct() {
        /*
         * Read from input fields.
         * Use trim to eliminate leading or trailing white space.
         * Validate strings to make sure that user included all the required data.
         */
        String nameString = nameEditText.getText().toString().trim();
        if (nameString.matches("")){
            Toast.makeText(this, R.string.product_name_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String priceString = priceEditText.getText().toString().trim();
        if (priceString.matches("")){
            Toast.makeText(this, R.string.price_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String quantityString = productQuantityText.getText().toString().trim();
        if (quantityString.matches("")){
            Toast.makeText(this, R.string.product_requires_quantity, Toast.LENGTH_SHORT).show();
            return;
        }

        String imageString = uriString;
        if (imageString == null){
            Toast.makeText(this, R.string.product_requires_image, Toast.LENGTH_SHORT).show();
            return;
        }

        Product newProduct = new Product(nameString, priceString, quantityString, imageString);

        if (currentProductUri == null) {
            // Insert a new Product into the provider, returning the content URI for the new Product

            Uri uri = database.addProduct(getContentResolver(), newProduct);

            // Show a toast message depending on whether or not the insertion was successful
            // If the new content URI is null, then there was an error with insertion.
            // Otherwise, the insertion wsa successful and we can display a toast to inform user.
            if (uri == null) Toast.makeText(this, getString(R.string.editor_insert_failed),
                    Toast.LENGTH_SHORT).show();

            else Toast.makeText(this, getString(R.string.editor_insert_successful),
                    Toast.LENGTH_SHORT).show();

        } else {
            /*
             * Otherwise this is an EXISTING Product, so update the Product with content URI:
             * currentProductUri and pass in the new ContentValues. Pass in null for the selection and
             * selection args because currentProductUri will already identify the correct row in the
             * database that we want to modify.
             */
            int rowsAffected = database.updateProduct(getContentResolver(), newProduct, currentProductUri);

            // Show a toast message depending on whether or not the update was successful:
            switch (rowsAffected) {
                case 0:
                    // If no rows were affected, then there was an error with the update:
                    Toast.makeText(this, getString(R.string.editor_insert_failed),
                            Toast.LENGTH_SHORT).show();
                    break;
                default:
                    // Otherwise, the update was successful and we can display a toast:
                    Toast.makeText(this, getString(R.string.editor_insert_successful),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the menu options from the res/menu/menu_editor.xml file.
        // This adds menu items to the app bar.
        getMenuInflater().inflate(R.menu.menu_editor, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        super.onPrepareOptionsMenu(menu);

        // if this is a new Product, hide the "Delete" menu item:
        if (currentProductUri == null) {
            MenuItem menuitem = menu.findItem(R.id.action_delete);
            menuitem.setVisible(false);
        }
        return true;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        /*
         * The editor shows all Product attributes, therefore,
         * define a projection that contains all columns from the inventory table.
         */
        return database.getLoader(id, args, this, currentProductUri);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        /*
         * Proceed with moving to the first row of the cursor and reading data from it.
         * This should be the only row in the cursor.
         */
        Product product = database.getProduct(cursor);
        setProduct(product);
    }

    public void setProduct(Product product) {

        if (product == null)
            return;

        String itemUri = product.getImageUri();
        if (itemUri != null) {

            Uri imgUri = Uri.parse(product.getImageUri());
            productImage.setImageURI(imgUri);
        }

        // Update the view on the screen with the values from the database
        nameEditText.setText(product.getName());
        priceEditText.setText(product.getPrice());
        productQuantityText.setText(product.getQuantity());

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

        // If the loader is invalidated, clear out all the data from the input fields:
        setProduct(new Product());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // User clicks on a menu option in the app bar overflow menu
        switch (item.getItemId()) {

            // respond to a click on the "Save" menu option
            case R.id.action_save:
                // save Product to database
                saveProduct();
                // exit activity
                finish();
                return true;

            // Respond to a click on the "Delete" menu option
            case R.id.action_delete:
                // Pop up confirmation dialog for deletion
                showDeleteConfirmationDialog();
                return true;

            // Respond to a click on the "Up" arrow button in the app bar
            case android.R.id.home:
                /*
                 * If the Product hasn't changed, continue by navigating to parent activity,
                 * in this case, the {@link CatalogActivity}:
                 */
                if (!productHasChanged) {
                    NavUtils.navigateUpFromSameTask(EditorActivity.this);
                    return true;
                }
                /*
                 * Otherwise if there are unsaved changes, setup a dialog to warn the user. Create
                 * a click listener to handle the user confirming that changes should be discarded.
                 */
                DialogInterface.OnClickListener discardButtonClickListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // user clicks "discard" button, navigate to parent activity:
                                NavUtils.navigateUpFromSameTask(EditorActivity.this);
                            }
                        };

                // show a dialog that notifies the user they have unsaved changes:
                showUnsavedChangesDialog(discardButtonClickListener);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showUnsavedChangesDialog(
            DialogInterface.OnClickListener discardButtonClickListener) {

        /*
         * Create an AlertDialog.Builder and set the message and click listeners for the positive
         * and negative buttons on the dialog
         */
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.unsaved_changes_dialog_msg);
        builder.setPositiveButton(R.string.discard, discardButtonClickListener);

        builder.setNegativeButton(R.string.keep_editing, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int id) {

                /*
                 * User clicked the "Keep editing" button, so dismiss the dialog and continue
                 * editing the Product.
                 */
                if (dialog != null) dialog.dismiss();
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    // Prompt the user to confirm that they want to delete this Product:
    private void showDeleteConfirmationDialog() {
        /*
         * Create an AlertDialog.Builder and set the message, and click listeners for the positive
         * and negative button on the dialog:
         */
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.delete_dialog_msg);

        builder.setPositiveButton(R.string.action_delete, new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int id) {

                // User clicked the "Delete" button, so delete the Product
                deleteProduct();
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                /*
                 * User clicked the "Cancel" button, so dismiss the dialog and continue editing the
                 * Product
                 */
                if (dialog != null)
                    dialog.dismiss();
            }
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void deleteProduct() {
        // Only perform the delete if this is an existing Product.
        if (currentProductUri != null) {
            /*
             * Call the ContentResolver to delete the Product at the given content URI.
             * Pass in null for the selection and selection args because the currentProductUri
             * content URI already identifies the Product that we want.
             */
            int rowsDeleted = getContentResolver().delete(currentProductUri, null, null);

            // Show a toast message depending on whether or not the delete was successful.
            if (rowsDeleted == 0)
                // if no rows were deleted, then there was an error with the delete:
                Toast.makeText(this, getString(R.string.editor_delete_product_failed),
                        Toast.LENGTH_SHORT).show();
                // Otherwise, the delete was successful:
            else Toast.makeText(this, getString(R.string.editor_delete_product_successful),
                    Toast.LENGTH_SHORT).show();
        }
        // Close the activity
        finish();
    }

    public static class Product {

        private String name = "";
        private String price = "";
        private String quantity = "";
        private String imageUri = "";

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPrice() {
            return this.price;
        }

        private void setPrice(String price) {
            this.price = price;
        }

        public String getQuantity() {
            return this.quantity;
        }

        public void setQuantity(String quantity) {
            this.quantity = quantity;
        }

        public String getImageUri() {
            return this.imageUri;
        }

        private void setImageUri(String imageUri) {
            this.imageUri = imageUri;
        }

        public Product(String name, String price, String quantity, String imageUri) {
            setName(name);
            setPrice(price);
            setQuantity(quantity);
            setImageUri(imageUri);
        }

        public Product() {
        }
    }
}

