package com.foukas.dropbox2d.monetization;

/** The default (and currently only) AdProvider -- always reports no ad is
 * ready and always fails immediately if asked to show one anyway. This is
 * what keeps every ad-gated hook in the game invisible until a real
 * provider is implemented and swapped in. */
public class NoOpAdProvider implements AdProvider {
    @Override
    public boolean isRewardedAdReady() {
        return false;
    }

    @Override
    public void showRewardedAd(AdCallback callback) {
        callback.onAdFailedOrCancelled();
    }
}
