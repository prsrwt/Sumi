package com.sumi.app.widget

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.core.graphics.ColorUtils

/**
 * Picks the glass treatment to suit whatever wallpaper is behind the widget.
 *
 * This is the only thing Android will tell a third-party app about the backdrop.
 * The pixels themselves are unreachable: WallpaperManager.getDrawable() went
 * behind a signature-level permission in Android 13, and a live wallpaper has no
 * still image to read in the first place. Screen capture could get the pixels,
 * but demanding MediaProjection or an accessibility service to decorate a panel
 * is not a trade worth offering anyone.
 *
 * getWallpaperColors gives three representative colours and costs no permission.
 * No pixels, but enough to know whether we are drawing on something light or
 * something dark, which is the difference between the glass reading as glass and
 * disappearing entirely.
 */
object WallpaperTone {

    /**
     * Above this, the wallpaper is bright enough that light glass with white
     * marks would wash out, so the dark treatment is used instead.
     */
    private const val LIGHT_WALLPAPER_LUMINANCE = 0.45

    fun themeFor(context: Context): GlassTheme =
        if (isWallpaperLight(context)) {
            GlassTheme.OnLightWallpaper
        } else {
            GlassTheme.OnDarkWallpaper
        }

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
