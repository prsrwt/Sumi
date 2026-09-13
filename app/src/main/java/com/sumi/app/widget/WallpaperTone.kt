package com.sumi.app.widget

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.core.graphics.ColorUtils

/**
 * Picks how milky the glass should be for whatever wallpaper is behind it.
 *
 * This is the only thing Android will tell a third-party app about the backdrop.
 * The pixels are unreachable: WallpaperManager.getDrawable() went behind a
 * signature-level permission in Android 13, and a live wallpaper has no still
 * image anyway. getWallpaperColors gives three representative colours with no
 * permission - enough to know whether we are drawing over something dark or
 * something light.
 */
object WallpaperTone {

    private const val LIGHT_WALLPAPER_LUMINANCE = 0.45

    fun styleFor(context: Context): GlassStyle =
        if (isWallpaperLight(context)) GlassStyle.OnLightWallpaper else GlassStyle.OnDarkWallpaper

    private fun isWallpaperLight(context: Context): Boolean {
        // getWallpaperColors arrived in 8.1; below that, assume dark.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return false

        val primary = try {
            WallpaperManager.getInstance(context)
                ?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
                ?.toArgb()
        } catch (_: SecurityException) {
            // Some OEM builds guard this more tightly than stock does.
            null
        } ?: return false

        return ColorUtils.calculateLuminance(primary) > LIGHT_WALLPAPER_LUMINANCE
    }
}
