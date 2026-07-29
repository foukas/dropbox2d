package com.foukas.dropbox2d.monetization;

import java.util.List;

/**
 * In-app-purchase integration point (soft-currency/cosmetic products per
 * the design doc's monetization premise). Unlike AdProvider, this isn't
 * instantiated anywhere yet -- there's no shop screen in this game, and
 * building one just to host an always-empty product list (NoOpStoreProvider
 * reports zero products) would be scope creep for scaffolding that's
 * explicitly meant to stay inactive. This interface exists so the shape is
 * settled ahead of time; wire it in once a shop screen exists and real
 * Play Console products are configured (needs the Play Billing Library
 * added to android/build.gradle and products defined in Play Console --
 * neither exists yet).
 */
public interface StoreProvider {
    List<Product> getAvailableProducts();

    boolean isProductOwned(String productId);

    void purchase(String productId, PurchaseCallback callback);

    record Product(String id, String displayName, String priceLabel) {
    }

    interface PurchaseCallback {
        void onPurchaseComplete(String productId);

        void onPurchaseFailedOrCancelled(String productId);
    }
}
