package org.commcare.logic;

import androidx.annotation.NonNull;

import org.commcare.google.services.analytics.FormAnalyticsHelper;
import org.commcare.utils.FileUtil;
import org.commcare.views.widgets.WidgetFactory;
import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.form.api.FormController;
import org.javarosa.form.api.FormEntryController;

import java.io.File;
import java.util.Date;

/**
 * Wrapper around FormController to handle Android-specific form entry actions
 */

public class AndroidFormController extends FormController implements PendingCalloutInterface {

    private FormIndex mPendingCalloutFormIndex = null;
    private boolean wasPendingCalloutCancelled;
    private FormIndex formIndexToReturnTo = null;
    private boolean formCompleteAndSaved = false;

    private FormAnalyticsHelper formAnalyticsHelper;

    public AndroidFormController(FormEntryController fec, boolean readOnly) {
        super(fec, readOnly);
        formAnalyticsHelper = new FormAnalyticsHelper();
    }

    @Override
    public FormIndex getPendingCalloutFormIndex() {
        return mPendingCalloutFormIndex;
    }

    @Override
    public void setPendingCalloutFormIndex(FormIndex pendingCalloutFormIndex) {
        wasPendingCalloutCancelled = false;
        mPendingCalloutFormIndex = pendingCalloutFormIndex;
    }

    @Override
    public boolean wasCalloutPendingAndCancelled(@NonNull FormIndex calloutFormIndex) {
        return wasPendingCalloutCancelled && calloutFormIndex.equals(mPendingCalloutFormIndex);
    }

    @Override
    public void setPendingCalloutAsCancelled() {
        wasPendingCalloutCancelled = true;
    }

    //CTS: Added this to protect the JR internal classes, although it's not awesome that
    //this ended up in the "logic" division.
    public WidgetFactory getWidgetFactory() {
        return new WidgetFactory(mFormEntryController.getModel().getForm(), this);
    }

    /**
     * Should be used to store the current form index if we are about to perform any actions that
     * may muck with its value, but will want to be able to restore the original one later
     */
    public void storeFormIndexToReturnTo() {
        this.formIndexToReturnTo = this.getFormIndex();
    }

    public void returnToStoredIndex() {
        if (this.formIndexToReturnTo != null) {
            jumpToIndex(this.formIndexToReturnTo);
        }
        this.formIndexToReturnTo = null;
    }

    public boolean isFormCompleteAndSaved() {
        return formCompleteAndSaved;
    }

    public void markCompleteFormAsSaved() {
        this.formCompleteAndSaved = true;
    }

    public FormAnalyticsHelper getFormAnalyticsHelper() {
        return formAnalyticsHelper;
    }

    public FormDef getFormDef() {
        return mFormEntryController.getModel().getForm();
    }
}
