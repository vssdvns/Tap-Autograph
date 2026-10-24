package com.tapautograph.app

import android.app.Application
import android.app.KeyguardManager
import android.content.Context
import android.os.Looper
import android.os.PowerManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@LooperMode(LooperMode.Mode.PAUSED)
class TapStartupTest {
    private val app:Application get()=RuntimeEnvironment.getApplication()
    @Before fun setup(){
        AutographSharing.checkout.stop();AutographSharing.card.disarm();WalletTap.resetCooldown();WalletTap.enable(app,true)
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(false)
        shadowOf(app.getSystemService(Context.POWER_SERVICE) as PowerManager).setIsInteractive(true)
        LocalRepository(app).write(SavedData(autograph=Autograph(aspect=1.8f,strokes=listOf(InkStroke(listOf(InkPoint(.1f,.2f,0),InkPoint(.8f,.7f,200)))))))
    }
    @After fun cleanup(){AutographSharing.checkout.stop();WalletTap.enable(app,false);WalletTap.resetCooldown()}
    @Test fun cooldownDoesNotBlockAnotherCheckoutForAMinuteAndFavoritesAreBounded(){
        WalletTap.cooldown();assertFalse(WalletTap.canStart())
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMillis(1501))
        assertTrue(WalletTap.canStart())
        WalletTap.clearFavorites(app)
        repeat(6){assertTrue(WalletTap.favorite(app,"design-$it"))}
        assertFalse(WalletTap.favorite(app,"seventh"));assertEquals(6,WalletTap.favorites(app).size)
        WalletTap.forgetFavorite(app,"design-0");assertTrue(WalletTap.favorite(app,"seventh"))
        WalletTap.clearFavorites(app)
    }
    @Test fun coldSelectWaitsForPacketAndArmsWithoutOpeningWallet(){
        val controller=Robolectric.buildService(AutographHceService::class.java).create();val service=controller.get()
        assertNull(service.processCommandApdu(AutographApdu.SELECT,null))
        repeat(200){if(!AutographSharing.checkout.active){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}}
        assertTrue(AutographSharing.checkout.active);assertEquals(0,AutographSharing.checkout.state())
        assertNull(shadowOf(app).nextStartedActivity)
        controller.destroy()
    }
    @Test fun lockedDeviceNeverStartsOrLeaksAutograph(){
        val controller=Robolectric.buildService(AutographHceService::class.java).create()
        shadowOf(app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).setIsDeviceLocked(true)
        assertArrayEquals(byteArrayOf(0x69,0x85.toByte()),controller.get().processCommandApdu(AutographApdu.SELECT,null))
        assertFalse(AutographSharing.checkout.active);controller.destroy()
    }
    @Test fun deactivatedColdSelectCannotArmStaleLink(){
        val controller=Robolectric.buildService(AutographHceService::class.java).create();val service=controller.get()
        assertNull(service.processCommandApdu(AutographApdu.SELECT,null));service.onDeactivated(0)
        repeat(40){Thread.sleep(5);shadowOf(Looper.getMainLooper()).idle()}
        assertFalse(AutographSharing.checkout.active);controller.destroy()
    }
}
