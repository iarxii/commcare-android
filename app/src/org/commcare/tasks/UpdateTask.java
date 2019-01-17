package org.commcare.tasks;

import android.content.Context;
import android.support.v4.util.Pair;

import org.commcare.CommCareApp;
import org.commcare.CommCareApplication;
import org.commcare.dalvik.R;
import org.commcare.engine.resource.AndroidResourceManager;
import org.commcare.engine.resource.AppInstallStatus;
import org.commcare.engine.resource.ResourceInstallUtils;
import org.commcare.engine.resource.installers.LocalStorageUnavailableException;
import org.commcare.logging.DataChangeLog;
import org.commcare.logging.DataChangeLogger;
import org.commcare.resources.model.InstallCancelled;
import org.commcare.resources.model.InvalidResourceException;
import org.commcare.resources.model.Resource;
import org.commcare.resources.model.ResourceTable;
import org.commcare.resources.model.TableStateListener;
import org.commcare.resources.model.UnresolvedResourceException;
import org.commcare.util.LogTypes;
import org.commcare.utils.AndroidCommCarePlatform;
import org.commcare.views.dialogs.PinnedNotificationWithProgress;
import org.javarosa.core.services.Logger;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.xml.util.UnfullfilledRequirementsException;

import java.util.Vector;

/**
 * Stages an update for the seated app in the background. Does not perform
 * actual update. If the user opens the Update activity, this task will report
 * its progress to that activity.  Enforces the constraint that only one
 * instance is ever running.
 *
 * Will be cancelled on user logout, but can still run if no user is logged in.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class UpdateTask
        extends SingletonTask<String, Integer, ResultAndError<AppInstallStatus>>
        implements TableStateListener, InstallCancelled {

    private static UpdateTask singletonRunningInstance = null;
    private static final Object lock = new Object();

    private final AndroidResourceManager resourceManager;
    private final CommCareApp app;

    private PinnedNotificationWithProgress pinnedNotificationProgress = null;
    private Context ctx;
    private String profileRef;
    private boolean wasTriggeredByAutoUpdate = false;
    private boolean taskWasCancelledByUser = false;
    private int currentProgress = 0;
    private int maxProgress = 0;
    private int authority;

    private UpdateTask() {
        TAG = UpdateTask.class.getSimpleName();
        app = CommCareApplication.instance().getCurrentApp();
        AndroidCommCarePlatform platform = app.getCommCarePlatform();
        authority = Resource.RESOURCE_AUTHORITY_REMOTE;

        resourceManager =
                new AndroidResourceManager(platform);

        resourceManager.setUpgradeListeners(this, this);
    }

    public static UpdateTask getNewInstance() {
        synchronized (lock) {
            if (singletonRunningInstance == null) {
                singletonRunningInstance = new UpdateTask();
                return singletonRunningInstance;
            } else {
                throw new IllegalStateException("An instance of " + TAG + " already exists.");
            }
        }
    }

    public static UpdateTask getRunningInstance() {
        synchronized (lock) {
            if (singletonRunningInstance != null &&
                    singletonRunningInstance.getStatus() == Status.RUNNING) {
                return singletonRunningInstance;
            }
            return null;
        }
    }

    /**
     * Attaches pinned notification with a progress bar the task, which will
     * report updates to and close down the notification.
     *
     * @param ctx For launching notification and localizing text.
     */
    public void startPinnedNotification(Context ctx) {
        this.ctx = ctx;

        pinnedNotificationProgress =
                new PinnedNotificationWithProgress(ctx, "updates.pinned.download",
                        "updates.pinned.progress", R.drawable.update_download_icon);
    }

    @Override
    protected final ResultAndError<AppInstallStatus> doInBackground(String... params) {
        profileRef = params[0];

        setupUpdate();

        try {
            return new ResultAndError<>(stageUpdate());
        } catch (InvalidResourceException e) {
            ResourceInstallUtils.logInstallError(e,
                    "Structure error ocurred during install|");
            return new ResultAndError<>(AppInstallStatus.UnknownFailure, buildCombinedErrorMessage(e.resourceName, e.getMessage()));
        } catch (LocalStorageUnavailableException e) {
            ResourceInstallUtils.logInstallError(e,
                    "Couldn't install file to local storage|");
            return new ResultAndError<>(AppInstallStatus.NoLocalStorage, e.getMessage());
        } catch (UnfullfilledRequirementsException e) {
            ResourceInstallUtils.logInstallError(e,
                    "App resources are incompatible with this device|");
            String error;
            if (e.isVersionMismatchException()) {
                error = Localization.get("update.version.mismatch", new String[]{e.getRequiredVersionString(), e.getAvailableVesionString()});
                error += " ";
                if (e.getRequirementType() == UnfullfilledRequirementsException.RequirementType.MAJOR_APP_VERSION) {
                    error += Localization.get("update.major.mismatch");
                } else if (e.getRequirementType() == UnfullfilledRequirementsException.RequirementType.MINOR_APP_VERSION) {
                    error += Localization.get("update.minor.mismatch");
                }
            } else {
                error = e.getMessage();
            }
            return new ResultAndError<>(AppInstallStatus.IncompatibleReqs, error);
        } catch (UnresolvedResourceException e) {
            return new ResultAndError<>(ResourceInstallUtils.processUnresolvedResource(e), e.getMessage());
        } catch (Exception e) {
            ResourceInstallUtils.logInstallError(e,
                    "Unknown error ocurred during install|");
            return new ResultAndError<>(AppInstallStatus.UnknownFailure, e.getMessage());
        }
    }

    private void setupUpdate() {
        ResourceInstallUtils.recordUpdateAttemptTime(app);

        if (wasTriggeredByAutoUpdate) {
            ResourceInstallUtils.recordAutoUpdateStart(app);
        }

        resourceManager.incrementUpdateAttempts();

        Logger.log(LogTypes.TYPE_RESOURCES,
                "Beginning install attempt for profile " + profileRef);
    }

    private AppInstallStatus stageUpdate() throws UnfullfilledRequirementsException,
            UnresolvedResourceException {
        Resource profile = resourceManager.getMasterProfile();
        boolean appInstalled = (profile != null &&
                profile.getStatus() == Resource.RESOURCE_STATUS_INSTALLED);

        if (!appInstalled) {
            return AppInstallStatus.UnknownFailure;
        }

        String profileRefWithParams =
                ResourceInstallUtils.addParamsToProfileReference(profileRef);

        return resourceManager.checkAndPrepareUpgradeResources(profileRefWithParams, authority);
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);

        if (pinnedNotificationProgress != null) {
            pinnedNotificationProgress.handleTaskUpdate(values);
        }
    }

    @Override
    protected void onPostExecute(ResultAndError<AppInstallStatus> resultAndError) {
        super.onPostExecute(resultAndError);

        if (resultAndError.data.equals(AppInstallStatus.UpdateStaged)) {
            DataChangeLogger.log(new DataChangeLog.CommCareAppUpdateStaged());
        }

        if (!resultAndError.data.isUpdateInCompletedState()) {
            resourceManager.processUpdateFailure(resultAndError.data, ctx, wasTriggeredByAutoUpdate);
        } else if (wasTriggeredByAutoUpdate) {
            // auto-update was successful or app was up-to-date.
            ResourceInstallUtils.recordAutoUpdateCompletion(app);
        }

        if (pinnedNotificationProgress != null) {
            pinnedNotificationProgress.handleTaskCompletion(resultAndError);
        }
    }

    @Override
    protected void onCancelled(ResultAndError<AppInstallStatus> resultAndError) {
        super.onCancelled(resultAndError);

        if (taskWasCancelledByUser && wasTriggeredByAutoUpdate) {
            // task may have been cancelled by logout, in which case we want
            // to keep trying to auto-update upon logging in again.
            ResourceInstallUtils.recordAutoUpdateCompletion(app);
        }
        taskWasCancelledByUser = false;

        if (pinnedNotificationProgress != null) {
            pinnedNotificationProgress.handleTaskCancellation();
        }

        resourceManager.upgradeCancelled();
    }

    @Override
    public void clearTaskInstance() {
        synchronized (lock) {
            singletonRunningInstance = null;
        }
    }

    /**
     * Calculate and report the resource install progress a table has made.
     */
    @Override
    public void compoundResourceAdded(ResourceTable table) {
        Vector<Resource> resources =
                AndroidResourceManager.getResourceListFromProfile(table);

        currentProgress = 0;
        for (Resource r : resources) {
            int resourceStatus = r.getStatus();
            if (resourceStatus == Resource.RESOURCE_STATUS_UPGRADE ||
                    resourceStatus == Resource.RESOURCE_STATUS_INSTALLED) {
                currentProgress += 1;
            }
        }
        maxProgress = resources.size();
        incrementProgress(currentProgress, maxProgress);
    }

    @Override
    public void simpleResourceAdded() {
        incrementProgress(++currentProgress, maxProgress);
    }

    @Override
    public void incrementProgress(int complete, int total) {
        this.publishProgress(complete, total);
    }

    /**
     * Allows resource installation process to check if this task was cancelled
     */
    @Override
    public boolean wasInstallCancelled() {
        return isCancelled();
    }

    public int getProgress() {
        return currentProgress;
    }

    public int getMaxProgress() {
        return maxProgress;
    }

    /**
     * Register task as triggered by auto update; used to determine retry behaviour.
     */
    public void setAsAutoUpdate() {
        wasTriggeredByAutoUpdate = true;
    }

    public void setLocalAuthority() {
        authority = Resource.RESOURCE_AUTHORITY_LOCAL;
    }

    /**
     * Record that task cancellation was triggered by user, not the app logging
     * out. Useful for knowing if an auto-update should resume or not upon next
     * login.
     */
    public void cancelWasUserTriggered() {
        taskWasCancelledByUser = true;
    }

    public static boolean isCombinedErrorMessage(String message) {
        return message != null && message.startsWith("||");
    }

    /**
     * Put both the resource in question and a detailed error message into one string for
     * ease of transport, which will be split out later and formatted into a user-readable
     * pinned notification
     */
    private static String buildCombinedErrorMessage(String head, String tail) {
        return "||" + head + "==" + tail;
    }

    public static Pair<String, String> splitCombinedErrorMessage(String message) {
        String[] splitMessage = message.split("==", 2);
        return Pair.create(splitMessage[0].substring(2), splitMessage[1]);
    }

}
