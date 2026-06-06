package com.example.feesmanager.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object FileViewerHelper {

    suspend fun downloadAndOpenFile(context: Context, fileUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                // Determine file name from URL
                val ext = fileUrl.substringAfterLast('.', "").substringBefore('?')
                val fileName = "Document_${System.currentTimeMillis()}.${if (ext.isNotEmpty()) ext else "bin"}"
                
                // Use cache dir for temporary files
                val downloadsDir = File(context.cacheDir, "downloads")
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                
                val targetFile = File(downloadsDir, fileName)
                
                // Download file
                val url = URL(fileUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()
                
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Server returned HTTP ${connection.responseCode}", Toast.LENGTH_SHORT).show()
                    }
                    return@withContext
                }
                
                val input = connection.inputStream
                val output = targetFile.outputStream()
                
                input.copyTo(output)
                
                output.close()
                input.close()
                
                // View file
                withContext(Dispatchers.Main) {
                    val uri: Uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        targetFile
                    )
                    
                    val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                        ?: "application/octet-stream"
                        
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    val chooser = Intent.createChooser(intent, "Open with")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to open file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
