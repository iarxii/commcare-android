package org.commcare.android.logging;

import android.support.v4.util.Pair;

import org.commcare.android.javarosa.AndroidLogEntry;
import org.commcare.android.javarosa.AndroidLogger;
import org.commcare.android.util.SessionStateUninitException;
import org.commcare.dalvik.application.CommCareApp;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.commcare.suite.model.Profile;
import org.javarosa.core.util.externalizable.DeserializationException;
import org.javarosa.core.util.externalizable.ExtUtil;
import org.javarosa.core.util.externalizable.PrototypeFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Log entry for xpath errors, capturing the expression, session, and cc app version.
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class XPathErrorEntry extends AndroidLogEntry {
    public static final String STORAGE_KEY = "XPATH_ERROR";
    private int appVersion;
    private String appId;
    private String expression;
    private String sessionFramePath;
    private String userId;

    public XPathErrorEntry() {
        // for externalization
    }

    protected XPathErrorEntry(String expression, String errorMessage) {
        super(AndroidLogger.TYPE_ERROR_CONFIG_STRUCTURE, errorMessage, new Date());

        if (expression == null) {
            this.expression = "";
        } else {
            this.expression = expression;
        }
        this.sessionFramePath = getCurrentSession();
        this.userId = CommCareApplication._().getCurrentUserId();
        Pair<Integer, String> appVersionAndId = lookupCurrentAppVersionAndId();
        this.appVersion = appVersionAndId.first;
        this.appId = appVersionAndId.second;
    }

    private static String getCurrentSession() {
        CommCareSession currentSession;
        try {
            currentSession = CommCareApplication._().getCurrentSession();
            return currentSession.getFrame().toString();
        } catch (SessionStateUninitException e) {
            return "";
        }
    }

    private Pair<Integer, String> lookupCurrentAppVersionAndId() {
        CommCareApp app = CommCareApplication._().getCurrentApp();

        if (app != null) {
            Profile profile = app.getCommCarePlatform().getCurrentProfile();
            return new Pair<>(profile.getVersion(), profile.getUniqueId());
        }

        return new Pair<>(-1, "");
    }

    public String getExpression() {
        return expression;
    }

    public String getSessionPath() {
        return sessionFramePath;
    }

    public int getAppVersion() {
        return appVersion;
    }

    public String getAppId() {
        return appId;
    }

    public String getUserId() {
        return userId;
    }

    @Override
    public String toString() {
        return getTime().toString() + " | " +
                getMessage() + " caused by " + expression +
                "\n" + "session: " + sessionFramePath;
    }

    @Override
    public void readExternal(DataInputStream in, PrototypeFactory pf)
            throws IOException, DeserializationException {
        super.readExternal(in, pf);

        appVersion = ExtUtil.readInt(in);
        appId = ExtUtil.readString(in);
        expression = ExtUtil.readString(in);
        sessionFramePath = ExtUtil.readString(in);
        userId = ExtUtil.readString(in);
    }

    @Override
    public void writeExternal(DataOutputStream out) throws IOException {
        super.writeExternal(out);

        ExtUtil.writeNumeric(out, appVersion);
        ExtUtil.writeString(out, appId);
        ExtUtil.writeString(out, expression);
        ExtUtil.writeString(out, sessionFramePath);
        ExtUtil.writeString(out, userId);
    }
}
