package org.commcare.tasks;

import android.util.Log;

import org.commcare.CommCareApplication;
import org.commcare.tasks.templates.CommCareTask;
import org.commcare.utils.FileUtil;
import org.commcare.utils.FormUploadUtil;
import org.commcare.utils.SessionUnavailableException;
import org.commcare.views.notifications.NotificationMessageFactory;
import org.commcare.views.notifications.NotificationMessageFactory.StockMessages;
import org.commcare.views.notifications.ProcessIssues;
import org.javarosa.core.model.User;
import org.javarosa.core.services.locale.Localization;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * @author ctsims
 */
public abstract class SendTask<R> extends CommCareTask<Void, String, Boolean, R> {
    private String url;
    private Long[] results;

    private final File dumpDirectory;

    private static final String MALFORMED_FILE_CATEGORY = "malformed-file";

    public static final int BULK_SEND_ID = 12335645;

    // 5MB less 1KB overhead

    public SendTask(String url, File dumpDirectory) {
        this.url = url;
        this.taskId = SendTask.BULK_SEND_ID;
        this.dumpDirectory = dumpDirectory;
    }

    @Override
    protected void onProgressUpdate(String... values) {
        super.onProgressUpdate(values);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        super.onPostExecute(result);
        //These will never get Zero'd otherwise
        url = null;
        results = null;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(ProcessIssues.LoggedOut));
    }

    @Override
    protected Boolean doTaskBackground(Void... params) {

        publishProgress(Localization.get("bulk.form.send.start"));

        //sanity check
        if (!(dumpDirectory.isDirectory())) {
            return false;
        }

        File[] files = dumpDirectory.listFiles();
        int counter = 0;

        results = new Long[files.length];

        for (int i = 0; i < files.length; ++i) {
            //Assume failure
            results[i] = FormUploadUtil.FAILURE;
        }

        boolean allSuccessful = true;

        for (int i = 0; i < files.length; i++) {

            publishProgress(Localization.get("bulk.send.dialog.progress", new String[]{"" + (i + 1)}));

            File f = files[i];

            if (!(f.isDirectory())) {
                Log.e("send", "Encountered non form entry in file dump folder at path: " + f.getAbsolutePath());
                CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Send_MalformedFile, new String[]{null, f.getName()}, MALFORMED_FILE_CATEGORY));
                continue;
            }
            try {
                User user = CommCareApplication._().getSession().getLoggedInUser();
                results[i] = FormUploadUtil.sendInstance(counter, f, url, user);

                if (results[i] == FormUploadUtil.FULL_SUCCESS) {
                    FileUtil.deleteFileOrDir(f);
                } else if (results[i] == FormUploadUtil.TRANSPORT_FAILURE) {
                    allSuccessful = false;
                    publishProgress(Localization.get("bulk.send.transport.error"));
                    return false;
                } else {
                    allSuccessful = false;
                    CommCareApplication._().reportNotificationMessage(NotificationMessageFactory.message(StockMessages.Send_MalformedFile, new String[]{null, f.getName()}, MALFORMED_FILE_CATEGORY));
                    publishProgress(Localization.get("bulk.send.file.error", new String[]{f.getAbsolutePath()}));
                }
                counter++;
            } catch (SessionUnavailableException | FileNotFoundException fe) {
                Log.e("E", Localization.get("bulk.send.file.error", new String[]{f.getAbsolutePath()}), fe);
                publishProgress(Localization.get("bulk.send.file.error", new String[]{fe.getMessage()}));
            }
        }
        return allSuccessful;
    }
}