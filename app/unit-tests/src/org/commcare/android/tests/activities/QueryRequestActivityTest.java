package org.commcare.android.tests.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import org.commcare.activities.QueryRequestActivity;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.mocks.ModernHttpRequesterMock;
import org.commcare.android.util.TestAppInstaller;
import org.commcare.dalvik.R;
import org.commcare.models.AndroidSessionWrapper;
import org.commcare.session.CommCareSession;
import org.javarosa.core.services.locale.Localization;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowToast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class QueryRequestActivityTest {
    @Before
    public void setup() {
        TestAppInstaller.installAppAndLogin(
                "jr://resource/commcare-apps/case_search_and_claim/profile.ccpr",
                "test", "123");
    }

    /**
     * Launch the query activity when a query datum isn't at the next needed
     * datum in the session
     */
    @Test
    public void launchQueryActivityAtWrongTimeTest() {
        Intent queryActivityIntent =
                new Intent(ApplicationProvider.getApplicationContext(), QueryRequestActivity.class);
        QueryRequestActivity queryRequestActivity =
                Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent)
                        .setup().get();

        assertEquals(Activity.RESULT_CANCELED,
                Shadows.shadowOf(queryRequestActivity).getResultCode());
        assertTrue(queryRequestActivity.isFinishing());
    }

    /**
     * Launch query activity, fill out query prompts, make request, and store
     * result in session.  Checks request URL is built correctly.
     */
    @Test
    public void makeSuccessfulQueryRequestTest() {
        setSessionCommand("patient-search");

        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200});
        ModernHttpRequesterMock.setExpectedUrls(
                new String[]{"https://www.fake.com/patient_search/?patient_id=123&name=francisco&device_id=000000000000000"});
        ModernHttpRequesterMock.setRequestPayloads(
                new String[]{"jr://resource/commcare-apps/case_search_and_claim/good-query-result.xml"});

        Intent queryActivityIntent =
                new Intent(ApplicationProvider.getApplicationContext(), QueryRequestActivity.class);
        QueryRequestActivity queryRequestActivity =
                Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent)
                        .setup().get();

        LinearLayout promptsLayout =
                queryRequestActivity.findViewById(R.id.query_prompts);
        EditText patientName = promptsLayout.getChildAt(0).findViewById(R.id.prompt_et);
        patientName.setText("francisco");
        EditText patientId = promptsLayout.getChildAt(1).findViewById(R.id.prompt_et);
        patientId.setText("123");

        Button queryButton = queryRequestActivity.findViewById(R.id.request_button);
        queryButton.performClick();

        assertEquals(Activity.RESULT_OK,
                Shadows.shadowOf(queryRequestActivity).getResultCode());
        assertTrue(queryRequestActivity.isFinishing());
    }

    /**
     * Make query whose response isn't in the correct format and check the
     * error message displayed
     */
    @Test
    public void makeQueryWithBadServerPayloadTest() {
        setSessionCommand("patient-search");

        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200});
        ModernHttpRequesterMock.setExpectedUrls(
                new String[]{"https://www.fake.com/patient_search/?patient_id=123&name=francisco&device_id=000000000000000"});
        ModernHttpRequesterMock.setRequestPayloads(
                new String[]{"jr://resource/commcare-apps/case_search_and_claim/bad-query-result.xml"});

        Intent queryActivityIntent =
                new Intent(ApplicationProvider.getApplicationContext(), QueryRequestActivity.class);

        ActivityController<QueryRequestActivity> controller =
                Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent).setup();
        QueryRequestActivity queryRequestActivity = controller.get();

        LinearLayout promptsLayout =
                queryRequestActivity.findViewById(R.id.query_prompts);
        EditText patientName = promptsLayout.getChildAt(0).findViewById(R.id.prompt_et);
        patientName.setText("francisco");
        EditText patientId = promptsLayout.getChildAt(1).findViewById(R.id.prompt_et);
        patientId.setText("123");

        Button queryButton =
                queryRequestActivity.findViewById(R.id.request_button);
        queryButton.performClick();

        TextView errorMessage = queryRequestActivity.findViewById(R.id.error_message);
        assertEquals(View.VISIBLE, errorMessage.getVisibility());
        String expectedErrorPart = Localization.get("query.response.format.error", "");
        assertTrue(((String)errorMessage.getText()).contains(expectedErrorPart));

        // serialize app state into bundle
        Bundle savedInstanceState = new Bundle();
        controller.saveInstanceState(savedInstanceState);

        // start new activity with serialized app state
        queryRequestActivity = Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent)
                .setup(savedInstanceState).get();

        // check that the error message is still there
        errorMessage = queryRequestActivity.findViewById(R.id.error_message);
        assertEquals(View.VISIBLE, errorMessage.getVisibility());
        assertTrue(((String)errorMessage.getText()).contains(expectedErrorPart));
    }

    /**
     * Start filling out query parameters and then 'rotate' the screen to see
     * if text entry is restored
     */
    @Test
    public void reloadQueryActivityStateTest() {
        setSessionCommand("patient-search");

        Intent queryActivityIntent =
                new Intent(ApplicationProvider.getApplicationContext(), QueryRequestActivity.class);

        ActivityController<QueryRequestActivity> controller =
                Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent)
                        .create().start().resume();
        QueryRequestActivity queryRequestActivity = controller.get();

        LinearLayout promptsLayout =
                queryRequestActivity.findViewById(R.id.query_prompts);
        EditText patientId = promptsLayout.getChildAt(1).findViewById(R.id.prompt_et);
        patientId.setText("123");

        // serialize app state into bundle
        Bundle savedInstanceState = new Bundle();
        controller.saveInstanceState(savedInstanceState);

        // start new activity with serialized app state
        queryRequestActivity = Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent)
                .setup(savedInstanceState).get();

        // check that the query prompts are filled out still
        promptsLayout = queryRequestActivity.findViewById(R.id.query_prompts);
        patientId = promptsLayout.getChildAt(1).findViewById(R.id.prompt_et);
        assertEquals("123", patientId.getText().toString());

        EditText patientName = promptsLayout.getChildAt(0).findViewById(R.id.prompt_et);
        assertEquals("", patientName.getText().toString());
    }

    /**
     * Make query with empty response, which should result in a toast, not advancing forward
     */
    @Test
    public void receiveEmptyQueryResultTest() {
        setSessionCommand("patient-search");

        ModernHttpRequesterMock.setResponseCodes(new Integer[]{200, 200, 200});
        ModernHttpRequesterMock.setExpectedUrls(
                new String[]{"https://www.fake.com/patient_search/?name=francisco&device_id=000000000000000"});
        ModernHttpRequesterMock.setRequestPayloads(
                new String[]{"jr://resource/commcare-apps/case_search_and_claim/empty-query-result-one-tag.xml",
                        "jr://resource/commcare-apps/case_search_and_claim/empty-query-result-two-tags.xml",
                        "jr://resource/commcare-apps/case_search_and_claim/single-query-result.xml"});

        Intent queryActivityIntent =
                new Intent(ApplicationProvider.getApplicationContext(), QueryRequestActivity.class);

        ActivityController<QueryRequestActivity> controller =
                Robolectric.buildActivity(QueryRequestActivity.class, queryActivityIntent)
                        .setup();
        QueryRequestActivity queryRequestActivity = controller.get();

        LinearLayout promptsLayout =
                queryRequestActivity.findViewById(R.id.query_prompts);
        EditText patientName = promptsLayout.getChildAt(0).findViewById(R.id.prompt_et);
        patientName.setText("francisco");

        Button queryButton =
                queryRequestActivity.findViewById(R.id.request_button);
        queryButton.performClick();

        Assert.assertEquals(Localization.get("query.response.empty"),
                ShadowToast.getTextOfLatestToast());

        queryButton.performClick();
        Assert.assertEquals(Localization.get("query.response.empty"),
                ShadowToast.getTextOfLatestToast());

        queryButton.performClick();
        assertEquals(Activity.RESULT_OK,
                Shadows.shadowOf(queryRequestActivity).getResultCode());
        assertTrue(queryRequestActivity.isFinishing());
    }

    private static void setSessionCommand(String command) {
        AndroidSessionWrapper sessionWrapper =
                CommCareApplication.instance().getCurrentSessionWrapper();
        CommCareSession session = sessionWrapper.getSession();
        session.setCommand(command);
    }
}
