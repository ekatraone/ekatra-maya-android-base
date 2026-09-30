package org.ekatra.alfred.worker

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates integrity enforcement for model downloads.
 *
 * This test intentionally downloads a non-model text file while claiming it is the
 * qwen2.5-0.5b model. The SHA-256 check must fail and the worker should return retry.
 */
@RunWith(AndroidJUnit4::class)
class ModelDownloadWorkerIntegrityTest {

    @Test
    fun tamperedPayload_returnsRetryDueToHashMismatch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val input = Data.Builder()
            .putString(ModelDownloadWorker.KEY_MODEL_URL, "https://raw.githubusercontent.com/github/gitignore/main/README.md")
            .putString(ModelDownloadWorker.KEY_MODEL_FILENAME, "tampered-qwen-test.gguf")
            .putString(ModelDownloadWorker.KEY_MODEL_ID, "qwen2.5-0.5b-instruct")
            .build()

        val worker = TestListenableWorkerBuilder<ModelDownloadWorker>(context)
            .setInputData(input)
            .build()

        val result = worker.startWork().get()
        assertNotNull(result)
        assertEquals(ListenableWorker.Result.retry()::class.java, result::class.java)

        // Cleanup any temporary artifacts created during the test.
        File(context.filesDir, "models/tampered-qwen-test.gguf").delete()
        File(context.filesDir, "models/tampered-qwen-test.gguf.tmp").delete()
    }
}
