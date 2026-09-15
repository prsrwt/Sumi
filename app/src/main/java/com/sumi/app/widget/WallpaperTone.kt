package com.sumi.app.widget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.core.graphics.ColorUtils

/**
 * Picks the glass and ink for whatever wallpaper is behind the widget.
 *
 * This is the only thing Android will tell a third-party app about the backdrop.
 * The pixels are unreachable: WallpaperManager.getDrawable() went behind a
 * signature-level permission in Android 13, and a live wallpaper has no still
 * image anyway. getWallpaperColors needs no permission.
 *
 * On Android 12 and later it also carries the system's own verdict on whether
 * dark text reads on this wallpaper, the same hint the launcher uses to colour
 * its icon labels, so Sumi simply follows it. Older versions only give colours,
 * so there the brightness of the main colour decides.
 *
 * Android does not tell widgets when the wallpaper changes, so a new wallpaper
 * shows its new glass on the widget's next redraw: when its face changes, at
 * midnight, or when Sumi is opened and closed.
 */
object WallpaperTone {

    private const val LIGHT_WALLPAPER_LUMINANCE = 0.45

    fun styleFor(context: Context): GlassStyle =
        if (isWallpaperLight(context)) GlassStyle.OnLightWallpaper else GlassStyle.OnDarkWallpaper

    private fun isWallpaperLight(context: Context): Boolean {
        // getWallpaperColors arrived in 8.1; below that, assume dark.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return false

        val colors = try {
            WallpaperManager.getInstance(context)?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        } catch (_: SecurityException) {
            // Some OEM builds guard this more tightly than stock does.
            null
        } ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        }
        return ColorUtils.calculateLuminance(colors.primaryColor.toArgb()) > LIGHT_WALLPAPER_LUMINANCE
    }
}
