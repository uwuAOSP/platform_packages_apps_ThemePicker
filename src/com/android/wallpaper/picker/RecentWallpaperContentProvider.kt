/*
 * SPDX-FileCopyrightText: The uwuAOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.wallpaper.picker

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import com.android.wallpaper.module.RecentWallpaperStore
import java.io.FileNotFoundException

/** ContentProvider used by Launcher3 and WallpaperPicker2 for recent wallpaper quick switching. */
class RecentWallpaperContentProvider : ContentProvider() {

    private lateinit var store: RecentWallpaperStore

    override fun onCreate(): Boolean {
        store = RecentWallpaperStore(requireNotNull(context).applicationContext)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val columns = projection ?: DEFAULT_COLUMNS
        val cursor = MatrixCursor(columns)
        if (uri.pathSegments.firstOrNull() != PATH_LIST_RECENTS) {
            return cursor
        }
        val destination = destinationFor(uri.pathSegments.getOrNull(1))
        store.getRecentWallpapers(destination).forEach { entry ->
            cursor.addRow(
                columns.map { column ->
                    when (column) {
                        COLUMN_ID -> entry.id
                        COLUMN_PLACEHOLDER_COLOR -> entry.placeholderColor
                        COLUMN_LAST_UPDATED -> entry.lastUpdated
                        COLUMN_TITLE -> entry.title
                        COLUMN_COMPONENT -> entry.component
                        else -> null
                    }
                }.toTypedArray()
            )
        }
        return cursor
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (uri.pathSegments.firstOrNull() != PATH_THUMBNAIL) {
            throw FileNotFoundException("Unknown recent wallpaper path: $uri")
        }
        val entry = store
            .getRecentWallpapers(
                if (uri.getQueryParameter(DESTINATION) == DESTINATION_LOCK) {
                    WallpaperManager.FLAG_LOCK
                } else {
                    WallpaperManager.FLAG_SYSTEM
                }
            )
            .firstOrNull { it.id == uri.pathSegments.getOrNull(1) }
            ?: throw FileNotFoundException("Recent wallpaper not found: $uri")
        val file = store.fileFor(entry)?.takeIf { it.isFile }
            ?: throw FileNotFoundException("Recent wallpaper thumbnail not found: $uri")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        if (uri.pathSegments.firstOrNull() != PATH_SET_WALLPAPER) return 0
        val wallpaperId = values?.getAsString(COLUMN_ID) ?: return 0
        val destination = destinationFor(values.getAsString(COLUMN_SCREEN))
        val entry = store.getRecentWallpapers(destination).firstOrNull { it.id == wallpaperId }
            ?: return 0
        val wallpaperManager = WallpaperManager.getInstance(requireNotNull(context))
        val result = try {
            when {
                !TextUtils.isEmpty(entry.component) -> {
                    val component = ComponentName.unflattenFromString(entry.component!!)
                        ?: return 0
                    try {
                        val method = wallpaperManager.javaClass.getMethod(
                            "setWallpaperComponentWithFlags",
                            ComponentName::class.java,
                            Int::class.javaPrimitiveType,
                        )
                        method.invoke(wallpaperManager, component, destination)
                    } catch (_: NoSuchMethodException) {
                        wallpaperManager.setWallpaperComponent(component)
                    }
                    true
                }
                store.fileFor(entry)?.isFile == true -> {
                    store.fileFor(entry)!!.inputStream().use { input ->
                        wallpaperManager.setStream(input, null, true, destination) > 0
                    }
                }
                else -> false
            }
        } catch (_: Exception) {
            false
        }
        if (result) {
            store.markApplied(destination, wallpaperId)
            requireNotNull(context).contentResolver.notifyChange(LIST_URI, null)
        }
        return if (result) 1 else 0
    }

    override fun getType(uri: Uri): String = when (uri.pathSegments.firstOrNull()) {
        PATH_THUMBNAIL -> "image/png"
        else -> "vnd.android.cursor.dir/vnd.uwuaosp.wallpaper"
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun destinationFor(value: String?): Int = when (value) {
        DESTINATION_LOCK -> WallpaperManager.FLAG_LOCK
        DESTINATION_BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        else -> WallpaperManager.FLAG_SYSTEM
    }

    companion object {
        private const val PATH_LIST_RECENTS = "list_recent"
        private const val PATH_THUMBNAIL = "thumb"
        private const val PATH_SET_WALLPAPER = "set_recent_wallpaper"
        private const val DESTINATION = "destination"
        private const val DESTINATION_LOCK = "lock_screen"
        private const val DESTINATION_BOTH = "all_screens"
        private const val COLUMN_ID = "id"
        private const val COLUMN_PLACEHOLDER_COLOR = "placeholder_color"
        private const val COLUMN_LAST_UPDATED = "last_updated"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_SCREEN = "screen"
        private const val COLUMN_COMPONENT = "component"
        private val BASE_URI = Uri.parse("content://com.google.android.apps.wallpaper.recents")
        private val LIST_URI = BASE_URI.buildUpon().appendPath(PATH_LIST_RECENTS).build()
        private val DEFAULT_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_PLACEHOLDER_COLOR,
            COLUMN_LAST_UPDATED,
            COLUMN_TITLE,
            COLUMN_COMPONENT,
        )
    }
}
