package com.foukas.dropbox2d.monetization;

import java.util.Collections;
import java.util.List;

/** Always reports zero products and fails any purchase attempt. Matches
 * NoOpAdProvider's pattern: a real StoreProvider swaps in later without
 * anything else in the codebase needing to change. */
public class NoOpStoreProvider implements StoreProvider {
    @Override
    public List<Product> getAvailableProducts() {
        return Collections.emptyList();
    }

    @Override
    public boolean isProductOwned(String productId) {
        return false;
    }

    @Override
    public void purchase(String productId, PurchaseCallback callback) {
        callback.onPurchaseFailedOrCancelled(productId);
    }
}
