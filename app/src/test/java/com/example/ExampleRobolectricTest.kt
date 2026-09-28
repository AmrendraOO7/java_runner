package com.example

import android.content.Context
import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.JavaIdeViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Java Runner", appName)
  }

  @Test
  @Config(sdk = [35])
  fun `run clears previous terminal output`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = JavaIdeViewModel(application)

    viewModel.sendTerminalInput("stale terminal output")
    assertTrue(viewModel.terminalEntries.value.any { it.text == "stale terminal output" })

    viewModel.runCode()
    assertFalse(viewModel.terminalEntries.value.any { it.text == "stale terminal output" })
    viewModel.stopExecution()
  }

  @Test
  @Config(sdk = [35])
  fun `default project folder is persisted`() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = JavaIdeViewModel(application)
    val previousFolder = viewModel.defaultProjectFolder.value
    val selectedFolder = Uri.parse("content://example.documents/tree/projects")

    viewModel.setDefaultProjectFolder(selectedFolder)
    val restoredViewModel = JavaIdeViewModel(application)

    assertEquals(selectedFolder, restoredViewModel.defaultProjectFolder.value)
    viewModel.setDefaultProjectFolder(previousFolder)
  }
}
