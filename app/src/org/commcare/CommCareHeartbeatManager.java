package org.commcare;

import org.commcare.core.interfaces.HttpResponseProcessor;
import org.commcare.network.HttpRequestGenerator;
import org.commcare.preferences.CommCareServerPreferences;
import org.commcare.utils.SessionUnavailableException;
import org.javarosa.core.io.StreamsUtil;
import org.javarosa.core.model.User;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by amstone326 on 4/13/17.
 */

public class CommCareHeartbeatManager {

    private static final long ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;

    private static final String TEST_RESPONSE =
            "{\"latest_apk_version\":{\"value\":\"2.36.1\"},\"latest_ccz_version\":{\"value\":\"75\", \"force_by_date\":\"2017-05-01\"}}";


    public static void startHeartbeatCommunications() {
        TimerTask heartbeatTimerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    requestHeartbeat(CommCareApplication.instance().getSession().getLoggedInUser());
                } catch (SessionUnavailableException e) {
                    // There is no active user session, so we can't send this request right now
                    // TODO: change the timer to try again sooner
                }
            }
        };

        (new Timer()).schedule(heartbeatTimerTask, new Date(), ONE_DAY_IN_MS);
    }

    private static void requestHeartbeat(User currentUser) {
        CommCareApp currentApp = CommCareApplication.instance().getCurrentApp();
        String urlString = currentApp.getAppPreferences().getString(
                CommCareServerPreferences.PREFS_HEARTBEAT_URL_KEY, null);
        if (urlString == null) {
            // This app was generated before the heartbeat URL started being included, so we
            // can't make the request
            return;
        }

        HttpRequestGenerator requester = new HttpRequestGenerator(currentUser);
        try {
            InputStream is = new BufferedInputStream(requester.simpleGet(new URL(urlString)));
            String responseAsString = StreamsUtil.inputStreamToByteArray(is).toString();
            JSONObject jsonResponse = new JSONObject(responseAsString);
            parseHeartbeatResponse(jsonResponse);
        } catch (IOException e) {
            System.out.println("IO error while processing heartbeat response");
        } catch (JSONException e) {
            System.out.println("Heartbeat response was not properly-formed JSON");
        }
    }

    public static void parseTestHeartbeatResponse() throws JSONException {
        parseHeartbeatResponse(new JSONObject(TEST_RESPONSE));
    }

    private static void parseHeartbeatResponse(JSONObject responseAsJson) {
        try {
            if (responseAsJson.has("latest_apk_version")) {
                JSONObject latestApkVersionInfo = responseAsJson.getJSONObject("latest_apk_version");
                parseUpdateToPrompt(latestApkVersionInfo, true);
            }
        } catch (JSONException e) {
            System.out.println("Latest apk version object not formatted properly");
        }

        try {
            if (responseAsJson.has("latest_ccz_version")) {
                JSONObject latestCczVersionInfo = responseAsJson.getJSONObject("latest_ccz_version");
                parseUpdateToPrompt(latestCczVersionInfo, false);
            }
        } catch (JSONException e) {
            System.out.println("Latest ccz version object not formatted properly");
        }

    }

    private static void parseUpdateToPrompt(JSONObject latestVersionInfo, boolean isForApk) {
        try {
            if (latestVersionInfo.has("value")) {
                String versionValue = latestVersionInfo.getString("value");
                String forceByDate = null;
                if (latestVersionInfo.has("force_by_date")) {
                     forceByDate = latestVersionInfo.getString("force_by_date");
                }
                UpdateToPrompt updateToPrompt = new UpdateToPrompt(versionValue, forceByDate, isForApk);
                updateToPrompt.registerWithSystem();
            }
        } catch (JSONException e) {
            System.out.println("Encountered malformed json while parsing an UpdateToPrompt");
        }
    }

}
