package com.foukas.dropbox2d.monetization;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoOpAdProviderTest {

    @Test
    void neverReportsAnAdReady() {
        assertFalse(new NoOpAdProvider().isRewardedAdReady());
    }

    @Test
    void showingAnAdAlwaysFailsImmediately() {
        NoOpAdProvider provider = new NoOpAdProvider();
        boolean[] failed = {false};
        boolean[] earned = {false};

        provider.showRewardedAd(new AdProvider.AdCallback() {
            @Override
            public void onRewardEarned() {
                earned[0] = true;
            }

            @Override
            public void onAdFailedOrCancelled() {
                failed[0] = true;
            }
        });

        assertTrue(failed[0], "NoOpAdProvider must never grant a reward");
        assertFalse(earned[0]);
    }
}
