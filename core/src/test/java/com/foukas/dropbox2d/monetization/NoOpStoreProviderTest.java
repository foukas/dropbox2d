package com.foukas.dropbox2d.monetization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoOpStoreProviderTest {

    @Test
    void reportsNoProductsAvailable() {
        assertTrue(new NoOpStoreProvider().getAvailableProducts().isEmpty());
    }

    @Test
    void neverReportsAProductOwned() {
        assertFalse(new NoOpStoreProvider().isProductOwned("wrecking_ball_skin"));
    }

    @Test
    void purchaseAlwaysFailsImmediately() {
        NoOpStoreProvider provider = new NoOpStoreProvider();
        boolean[] completed = {false};
        boolean[] failed = {false};

        provider.purchase("wrecking_ball_skin", new StoreProvider.PurchaseCallback() {
            @Override
            public void onPurchaseComplete(String productId) {
                completed[0] = true;
            }

            @Override
            public void onPurchaseFailedOrCancelled(String productId) {
                failed[0] = true;
            }
        });

        assertTrue(failed[0], "NoOpStoreProvider must never complete a purchase");
        assertFalse(completed[0]);
    }
}
