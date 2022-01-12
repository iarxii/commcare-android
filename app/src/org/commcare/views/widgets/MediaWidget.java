package org.commcare.views.widgets;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.commcare.activities.components.FormEntryInstanceState;
import org.commcare.dalvik.R;
import org.commcare.logic.PendingCalloutInterface;
import org.commcare.util.LogTypes;
import org.commcare.utils.FileExtensionNotFoundException;
import org.commcare.utils.FileUtil;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.StringUtils;
import org.commcare.utils.UriToFilePath;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.IntegerData;
import org.javarosa.core.model.data.InvalidData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.form.api.FormEntryPrompt;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import androidx.annotation.NonNull;

/**
 * Generic logic for capturing or choosing audio/video/image media
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public abstract class MediaWidget extends QuestionWidget {
    private static final String TAG = MediaWidget.class.getSimpleName();

    protected static final String CUSTOM_TAG = "custom";

    protected Button mCaptureButton;
    protected Button mPlayButton;
    protected Button mChooseButton;
    protected String mBinaryName;

    protected final PendingCalloutInterface pendingCalloutInterface;
    protected final String mInstanceFolder;

    private int oversizedMediaSize;

    protected String recordedFileName;
    protected String customFileTag;
    protected String destMediaPath;

    public MediaWidget(Context context, FormEntryPrompt prompt,
                       PendingCalloutInterface pendingCalloutInterface) {
        super(context, prompt);

        this.pendingCalloutInterface = pendingCalloutInterface;

        mInstanceFolder =
                FormEntryInstanceState.mFormRecordPath.substring(0,
                        FormEntryInstanceState.mFormRecordPath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);
        initializeButtons();
        setupLayout();
    }


    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == View.VISIBLE) {
            loadAnswerFromDataModel();
        }
    }

    private void loadAnswerFromDataModel() {
        mBinaryName = mPrompt.getAnswerText();
        if (mBinaryName != null) {
            reloadFile();
        } else {
            checkForOversizedMedia(mPrompt.getAnswerValue());
            togglePlayButton(false);
        }
    }

    protected void reloadFile() {
        togglePlayButton(true);
        File f = new File(mInstanceFolder + mBinaryName);
        checkFileSize(f);
    }

    protected void togglePlayButton(boolean enabled) {
        mPlayButton.setEnabled(enabled);
    }

    protected abstract void initializeButtons();

    protected void setupLayout() {
        addView(mCaptureButton);
        addView(mChooseButton);
        addView(mPlayButton);
    }

    @Override
    public IAnswerData getAnswer() {
        if (oversizedMediaSize > 0) {
            // media was too big to upload, set answer as invalid data to
            // allow showing the user a proper warning message.
            return new InvalidData("", new IntegerData(oversizedMediaSize));
        } else if (mBinaryName != null) {
            return new StringData(mBinaryName);
        }
        return null;
    }

    /**
     * @return whether the media file passes the size check
     */
    private boolean ifMediaSizeChecks(String binaryPath) {
        File source = new File(binaryPath);
        boolean isTooLargeToUpload = checkFileSize(source);
        if (isTooLargeToUpload) {
            oversizedMediaSize = (int)source.length() / (1024 * 1024);
            return false;
        } else {
            oversizedMediaSize = -1;
            return true;
        }
    }

    /**
     * @return whether the media file has a valid extension
     */
    private boolean ifMediaExtensionChecks(String binaryPath) {
        String extension = FileUtil.getExtension(recordedFileName);
        if (!FormUploadUtil.isSupportedMultimediaFile(binaryPath)) {
            Toast.makeText(getContext(),
                    Localization.get("form.attachment.invalid"),
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, String.format(
                    "Could not save file with URI %s because of bad extension %s.",
                    binaryPath,
                    extension
            ));
            return false;
        }
        return true;
    }

    @Override
    public void clearAnswer() {
        deleteMedia();
        togglePlayButton(false);
    }

    private void deleteMedia() {
        File f = new File(mInstanceFolder + mBinaryName);
        if (!f.delete()) {
            Log.e(TAG, "Failed to delete " + f);
        }

        mBinaryName = null;
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mCaptureButton.setOnLongClickListener(l);
        mChooseButton.setOnLongClickListener(l);
        mPlayButton.setOnLongClickListener(l);
    }

    @Override
    public void unsetListeners() {
        super.unsetListeners();

        mCaptureButton.setOnLongClickListener(null);
        mChooseButton.setOnLongClickListener(null);
        mPlayButton.setOnLongClickListener(null);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();

        mCaptureButton.cancelLongPress();
        mChooseButton.cancelLongPress();
        mPlayButton.cancelLongPress();
    }

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    public void setBinaryData(Object binaryURI) {
        // delete any existing media
        if (mBinaryName != null) {
            deleteMedia();
        }

        String binaryPath;
        try {
            binaryPath = createFilePath(binaryURI);
        } catch (FileExtensionNotFoundException e) {
            showToast("form.attachment.invalid.extension");
            Logger.exception("Error while saving media ", e);
            return;
        } catch (IOException e) {
            e.printStackTrace();
            showToast("form.attachment.copy.fail");
            Logger.exception("Error while saving media ", e);
            return;
        }

        if(!ifMediaSizeChecks(binaryPath) && !ifMediaExtensionChecks(binaryPath)){
            // if file check fails and we have already copied the file to destMediaPath, delete it
            if (!destMediaPath.isEmpty()) {
                new File(destMediaPath).delete();
            }
            return;
        }

        File newMedia;

        // otherwise we have already copied the file at newPath during createFilePath
        if (destMediaPath.isEmpty()) {
            recordedFileName = FileUtil.getFileName(binaryPath);
            copyRecordedFileToDestination(binaryPath);
        }

        newMedia = new File(destMediaPath);
        if (newMedia.exists()) {
            showToast("form.attachment.success");
        }

        mBinaryName = newMedia.getName();
    }

    private void copyRecordedFileToDestination(String binaryPath) {
        String extension = FileUtil.getExtension(recordedFileName);
        destMediaPath = mInstanceFolder + System.currentTimeMillis() + customFileTag + "." + extension;

        // Copy to destMediaPath
        File source = new File(binaryPath);
        try {
            FileUtil.copyFile(source, new File(destMediaPath));
        } catch (IOException e) {
            showToast("form.attachment.copy.fail");
            Logger.exception(LogTypes.TYPE_MAINTENANCE, e);
            e.printStackTrace();
        }
    }

    /**
     * If file is chosen by user, the file selection intent will return an URI
     * If file is auto-selected after recording_fragment, then the recordingfragment will provide a string file path
     * Set value of customFileTag if the file is a recent recording from the RecordingFragment
     */
    private String createFilePath(Object binaryuri) throws IOException {
        String path = "";
        destMediaPath = "";
        if (binaryuri instanceof Uri) {
            // Need to make a copy of file using uri, so might as well copy to final destination path directly
            InputStream inputStream = getContext().getContentResolver().openInputStream((Uri)binaryuri);
            recordedFileName = FileUtil.getFileName(getContext(), (Uri)binaryuri);
            destMediaPath = mInstanceFolder + System.currentTimeMillis() + "." + FileUtil.getExtension(recordedFileName);
            FileUtil.copyFile(inputStream, new File(destMediaPath));
            path = destMediaPath;
            customFileTag = "";
        } else {
            path = (String)binaryuri;
            customFileTag = CUSTOM_TAG;
        }
        return path;
    }

    protected void playMedia(String mediaType) {
        Intent i = new Intent("android.intent.action.VIEW");
        File mediaFile = new File(mInstanceFolder + mBinaryName);
        Uri mediaUri = FileUtil.getUriForExternalFile(getContext(), mediaFile);
        i.setDataAndType(mediaUri, mediaType);

        UriToFilePath.grantPermissionForUri(getContext(), i, mediaUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            getContext().startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(getContext(),
                    StringUtils.getStringSpannableRobust(getContext(),
                            R.string.activity_not_found,
                            "play " + mediaType.substring(0, mediaType.indexOf("/"))),
                    Toast.LENGTH_SHORT).show();
        }
    }
}
