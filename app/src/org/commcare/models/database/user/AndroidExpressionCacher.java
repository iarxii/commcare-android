package org.commcare.models.database.user;

import org.commcare.CommCareApplication;
import org.commcare.models.database.SqlStorage;
import org.javarosa.core.services.storage.ExpressionCacher;
import org.javarosa.xpath.CachedExpression;
import org.javarosa.xpath.InFormCacheableExpr;

/**
 * Created by amstone326 on 1/10/18.
 */

public class AndroidExpressionCacher extends ExpressionCacher {

    public AndroidExpressionCacher() {
    }

    @Override
    public int cache(InFormCacheableExpr expression, Object value) {
        CachedExpression cached = new CachedExpression(expression, value);
        getCacheStorage().write(cached);
        return cached.getID();
    }

    @Override
    public Object getCachedValue(int idOfStoredCache) {
        CachedExpression cached = getCacheStorage().read(idOfStoredCache);
        if (cached == null) {
            return null;
        } else {
            return cached.getEvalResult();
        }
    }

    private SqlStorage<CachedExpression> getCacheStorage() {
        return CommCareApplication.instance()
                .getUserStorage(CachedExpression.STORAGE_KEY, CachedExpression.class);
    }

}
