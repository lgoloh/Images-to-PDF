package swati4star.createpdf.activity;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentSender;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.icu.text.IDNA;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.dd.morphingbutton.MorphingButton;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveClient;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResourceClient;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.OpenFileActivityOptions;
import com.google.android.gms.drive.events.ListenerToken;
import com.google.android.gms.drive.events.OpenFileCallback;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.SearchableField;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.zhihu.matisse.Matisse;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

import butterknife.BindView;
import swati4star.createpdf.R;

import static swati4star.createpdf.util.Constants.REQUEST_SELECT_IMAGE;
import static swati4star.createpdf.util.Constants.REQUEST_SIGN_IN;
import static swati4star.createpdf.util.Constants.RESULT;
import static swati4star.createpdf.util.StringUtils.showSnackbar;


public class GoogleSignInControl extends AppCompatActivity implements  View.OnClickListener,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "Drive Open Error";
    private GoogleSignInOptions mSignInOptions;
    private GoogleSignInClient mSignInClient;
    private GoogleSignInAccount mAccount;
    private MorphingButton mGoogleDrive;
    private DriveClient mDriveClient;  //handles high-level drive functions like create file, open file, sync
    private DriveResourceClient mResourceClient;  //handles access to drive files and resources
    private ArrayList<String> mMimeTypes = new ArrayList<>();
    private String mFileName;
    private ArrayList<String> mImageUri = new ArrayList<>();
    private Bitmap mImageFile;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_google_sign_in_control);
        //configurations for building the google sign in client
        mSignInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Drive.SCOPE_FILE)
                .requestScopes(Drive.SCOPE_APPFOLDER)
                .build();
        //initializes the google sign in client with the configurations
        mSignInClient = GoogleSignIn.getClient(this, mSignInOptions);
        mGoogleDrive = (MorphingButton) findViewById(R.id.google_button);
        mMimeTypes.add("image/jpeg");
        mMimeTypes.add("image/png");
        mMimeTypes.add("image/gif");
        mMimeTypes.add("image/svg+xml");

        mGoogleDrive.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int button = v.getId();
        if (button == R.id.google_button) {
            signIn();
        }

    }

    /**
     * Begins the sign in intent
     */
    public void signIn() {
        Intent signInIntent = mSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, REQUEST_SIGN_IN);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_SIGN_IN:
                if (data != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                    task.addOnCompleteListener(new OnCompleteListener<GoogleSignInAccount>() {
                        @Override
                        public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                            if (task.isSuccessful()) {
                                mAccount = task.getResult();
                                handleSignInResult(mAccount);
                            }
                        }
                    });
                }
                break;

            case REQUEST_SELECT_IMAGE:
                if (data != null) {
                    onFileSelected(data);
                }
        }
    }

    /**
     * Handles the result of the Sign In Intent by initializing the Drive Client of the users account
     * @param account
     */
    private void handleSignInResult(GoogleSignInAccount account) {
        //Log.d("test", account.getDisplayName());
        initializeDriveClient(account);
    }

    /**
     * Initializes the Drive client/Resource client of the users account
     * These are required for high level Drive functions like accessing and opening a file
     * @param mAccount
     */
    private void initializeDriveClient(GoogleSignInAccount mAccount) {
        mDriveClient = Drive.getDriveClient(this, mAccount);
        mResourceClient = Drive.getDriveResourceClient(this, mAccount);
        configureDriveDisplay();
    }

    /**
     * Set the configurations for the drive file picker
     * Configurations: Activity name, Selectable file types
     */
    private void configureDriveDisplay() {
        OpenFileActivityOptions.Builder displayHandler = new OpenFileActivityOptions.Builder();
        displayHandler.setActivityTitle("Select Image to convert: ");
        displayHandler.setMimeType(mMimeTypes);
        OpenFileActivityOptions displayOptions = displayHandler.build();
        driveDisplay(displayOptions);
    }

    /**
     * Displays the Drive File Picker Activity using option configurations
     * @param options
     */
    private void driveDisplay(OpenFileActivityOptions options) {
        Task<IntentSender> openTask = mDriveClient.newOpenFileActivityIntentSender(options);
        openTask.addOnCompleteListener(new OnCompleteListener<IntentSender>() {
            @Override
            public void onComplete(@NonNull Task<IntentSender> task) {
                if (task.isSuccessful()) {
                    IntentSender intentSender = openTask.getResult();
                    try {
                        startIntentSenderForResult(intentSender, REQUEST_SELECT_IMAGE, null,
                                0, 0, 0);
                    } catch (IntentSender.SendIntentException e) {
                        Log.e("test4", "Unable to display file picker", e);

                    }
                }
            }
        });
    }

    /**
     * Called in onActivityResult when the user selects a file in the Drive Picker activity
     * Gets the id of the image from the intent data
     * @param data
     */
    private void onFileSelected(Intent data) {
        DriveId driveId = data.getParcelableExtra(OpenFileActivityOptions.EXTRA_RESPONSE_DRIVE_ID);
        imageDownload(driveId);
    }

    /**
     * Returns the content uri of a bitmap
     * @param context
     * @param image
     * @return
     */

    private Uri getImageURI(Context context, Bitmap image) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(context.getContentResolver(), image,  mFileName, null);
        return Uri.parse(path);
    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult result) {
        Log.e(TAG, "Unable to Open Drive");
        showSnackbar((Activity) getApplicationContext(), "Could not connect to Google Drive");

    }

    /**
     * Used to create an intent that will launch this activity from the ImageToPDF fragment
     * @param context
     * @return
     */
    public static Intent getStartIntent(Context context) {
        Intent intent = new Intent(context, GoogleSignInControl.class);
        return intent;
    }

    /**
     * Logs the user out of their google account
     */
    public void logOut() {
        mSignInClient.signOut();
    }

    /**
     * Takes the ID f the drive image file and downloads its contents
     * Uses a download progress listener to handle long running downloads
     * Gets the image uri after download and returns ti to ImageToPDF fragment in an intent
     * @param id
     */

    public void imageDownload(DriveId id) {
        DriveFile imageFile = id.asDriveFile();
        Task<Metadata> fileMetadataTask = mResourceClient.getMetadata(imageFile);
        fileMetadataTask.addOnSuccessListener(new OnSuccessListener<Metadata>() {
            @Override
            public void onSuccess(Metadata metadata) {
                Metadata fileMetadata = fileMetadataTask.getResult();
                mFileName = fileMetadata.getOriginalFilename();
            }
        });

        OpenFileCallback driveOpenFileCallback  = new OpenFileCallback() {

            @Override
            public void onContents(@NonNull DriveContents contents) {
                try {
                    mImageFile = BitmapFactory.decodeStream(contents.getInputStream());
                    mResourceClient.discardContents(contents);
                    Uri contentUri = getImageURI(getBaseContext(), mImageFile);
                    String uriPath = getRealPathFromUri(getBaseContext(), contentUri);
                    mImageUri.add(uriPath);
                    Intent returnIntent = new Intent();
                    returnIntent.putStringArrayListExtra(RESULT, mImageUri);
                    setResult(Activity.RESULT_OK, returnIntent);
                    finish();
                } catch (Exception e) {
                    onError(e);
                }

            }

            @Override
            public void onError(@NonNull Exception e) {
                showSnackbar((Activity) getApplicationContext(), "File not downloaded");
                finish();
            }

            @Override
            public void onProgress(long bytesDownloaded, long bytesExpected) {

            }


        };

        mResourceClient.openFile(imageFile, DriveFile.MODE_READ_ONLY, driveOpenFileCallback);

    }

    /**
     * Gets real path of image from content uri of image
     * @param context
     * @param contentUri
     * @return
     */
    public static String getRealPathFromUri(Context context, Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        try (Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null)) {
            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            return cursor.getString(columnIndex);
        }
    }


}
