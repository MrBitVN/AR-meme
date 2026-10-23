package com.antigravity.arfiltermeme.recorder

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MediaCaptureHelper {

    /**
     * Captures a high quality snapshot from the AR SurfaceView using PixelCopy
     * and saves it directly to the system Gallery / MediaStore.
     */
    fun capturePhoto(
        activity: Activity,
        targetView: View = activity.window.decorView,
        onSuccess: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val bitmap = Bitmap.createBitmap(targetView.width, targetView.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        targetView.getLocationInWindow(location)

        try {
            val rect = Rect(location[0], location[1], location[0] + targetView.width, location[1] + targetView.height)
            PixelCopy.request(
                activity.window,
                rect,
                bitmap,
                { copyResult ->
                    if (copyResult == PixelCopy.SUCCESS) {
                        saveBitmapToGallery(activity, bitmap, onSuccess, onError)
                    } else {
                        onError(IllegalStateException("PixelCopy failed with error code: $copyResult"))
                    }
                },
                Handler(Looper.getMainLooper())
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        onSuccess: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "AR_FILTER_$timeStamp.jpg"

        var outputStream: OutputStream? = null
        var uri: Uri? = null

        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ARFilters")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                onError(IllegalStateException("Failed to create MediaStore entry"))
                return
            }

            outputStream = context.contentResolver.openOutputStream(uri)
            if (outputStream == null) {
                onError(IllegalStateException("Failed to open output stream"))
                return
            }

            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            Handler(Looper.getMainLooper()).post {
                onSuccess(uri)
            }
        } catch (e: Exception) {
            uri?.let { context.contentResolver.delete(it, null, null) }
            onError(e)
        } finally {
            outputStream?.close()
        }
    }

    fun loadRecentPhotos(context: Context, limit: Int = 20): List<Uri> {
        val photos = mutableListOf<Uri>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DATA} LIKE ?"
        }
        val selectionArgs = arrayOf("%ARFilters%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idColumn)
                    val contentUri = android.content.ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    photos.add(contentUri)
                    count++
                }
            }
        } catch (_: Exception) {}
        return photos
    }

    fun loadThumbnail(context: Context, uri: Uri, targetSize: Int = 160): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val maxDim = maxOf(info.size.width, info.size.height)
                    if (maxDim > targetSize) {
                        decoder.setTargetSampleSize(maxDim / targetSize)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val orig = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                if (orig != null && (orig.width > targetSize || orig.height > targetSize)) {
                    Bitmap.createScaledBitmap(orig, targetSize, (targetSize * orig.height) / orig.width, true)
                } else {
                    orig
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun viewPhoto(context: Context, photoUri: Uri) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(photoUri, "image/*")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Không tìm thấy ứng dụng xem ảnh!", Toast.LENGTH_SHORT).show()
        }
    }

    fun sharePhoto(context: Context, photoUri: Uri) {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, photoUri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = android.content.Intent.createChooser(intent, "Chia sẻ ảnh AR Meme").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "Không thể mở ứng dụng chia sẻ!", Toast.LENGTH_SHORT).show()
        }
    }
}
