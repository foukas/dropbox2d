package com.foukas.dropbox2d.monetization;

/**
 * Rewarded-ad integration point. NoOpAdProvider -- the only implementation
 * that exists right now -- always reports not-ready, so every UI hook
 * gated behind isRewardedAdReady() is entirely invisible/inactive. That's
 * deliberate: plumbing built ahead of having an ad network account, kept
 * hidden until real credentials exist, per explicit instruction rather
 * than speculative scope.
 *
 * To go live later: implement a real provider (e.g. backed by the AdMob
 * SDK), which needs `com.google.android.gms:play-services-ads` added to
 * android/build.gradle and a real AdMob App ID in AndroidManifest.xml
 * (a `<meta-data>` entry) -- neither exists yet since there's no AdMob
 * account. Swap the instantiation in DropGame.create(); nothing else in
 * this codebase depends on which implementation is active. Also needs a
 * consent flow (COPPA/ATT/GDPR, per the design doc's Open Questions)
 * before a real provider can legally serve ads -- there's nothing to
 * consent to yet with no ad SDK loaded, so that's deferred alongside it.
 */
public interface AdProvider {
    boolean isRewardedAdReady();

    void showRewardedAd(AdCallback callback);

    interface AdCallback {
        void onRewardEarned();

        void onAdFailedOrCancelled();
    }
}
