package org.commcare.android.database.user.models;

import org.commcare.android.storage.framework.Persisted;
import org.commcare.models.framework.Persisting;
import org.commcare.modern.database.Table;
import org.commcare.modern.models.EncryptedModel;
import org.commcare.modern.models.MetaField;
import org.javarosa.core.util.MD5;

/**
 * Represents the version of SessionStateDescriptor that exists in user databases up until CommCare
 * version 2.40; used to perform the necessary user db migration from V21 --> V22 in CommCare 2.41
 *
 */
@Table(SessionStateDescriptor.STORAGE_KEY)
public class SessionStateDescriptorV1 extends Persisted implements EncryptedModel {

    public static final String STORAGE_KEY = "android_cc_session";

    public static final String META_DESCRIPTOR_HASH = "descriptorhash";

    public static final String META_FORM_RECORD_ID = "form_record_id";

    @Persisting(1)
    @MetaField(value = META_FORM_RECORD_ID, unique = true)
    private int formRecordId = -1;

    @Persisting(2)
    private String sessionDescriptor = null;

    @MetaField(value = META_DESCRIPTOR_HASH)
    public String getHash() {
        return MD5.toHex(MD5.hash(sessionDescriptor.getBytes()));
    }

    @Override
    public boolean isEncrypted(String data) {
        return false;
    }

    @Override
    public boolean isBlobEncrypted() {
        return false;
    }

    public int getFormRecordId() {
        return formRecordId;
    }

    public String getSessionDescriptor() {
        return this.sessionDescriptor;
    }

}
