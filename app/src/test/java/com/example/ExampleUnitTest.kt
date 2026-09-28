package com.example

import com.example.compiler.DiagnosticSeverity
import com.example.compiler.JavaExecutionEngine
import com.example.compiler.JavaSyntaxAnalyzer
import com.example.compiler.extractMainMethodScript
import com.example.model.TerminalStreamType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testSyntaxAnalyzerBracketMismatch() {
    val analyzer = JavaSyntaxAnalyzer()
    val codeWithBrokenBrackets = """
      public class Test {
          public static void main(String[] args) {
              int x = 10;
          // missing closing brace
    """.trimIndent()

    val result = analyzer.analyze(codeWithBrokenBrackets, "Test.java")
    assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.ERROR })
  }

  @Test
  fun testSyntaxAnalyzerCleanCode() {
    val analyzer = JavaSyntaxAnalyzer()
    val cleanCode = """
      public class Clean {
          public static void main(String[] args) {
              System.out.println("Hello, World!");
          }
      }
    """.trimIndent()

    val result = analyzer.analyze(cleanCode, "Clean.java")
    assertEquals(0, result.diagnostics.count { it.severity == DiagnosticSeverity.ERROR })
    assertTrue(result.hasMainMethod)
    assertEquals("Clean", result.primaryClassName)
  }

  @Test
  fun testMainMethodWrapperIsConvertedToInterpreterScript() {
    val source = """
      import java.util.*;
      public class Main {
          public static void main(String[] input) {
              System.out.println("}");
          }
      }
    """.trimIndent()

    val script = extractMainMethodScript(source) ?: error("main method was not extracted")

    assertTrue(script.contains("import java.util.*;"))
    assertTrue(script.contains("void __javaProgramMain(String[] input)"))
    assertTrue(script.contains("System.out.println(\"}\");"))
    assertFalse(script.contains("class Main"))
  }

  @Test
  fun testJavaExecutionEngineExecutesClassHelperMethod() = runBlocking {
    val engine = JavaExecutionEngine()
    val output = StringBuilder()
    var finishedExitCode = -1

    val code = """
      import java.net.Inet6Address;
      import java.net.InetAddress;
      public class ReverseTest {
          public static void main(String[] args) {
              InetAddress address = InetAddress.getByName("::1");
              System.out.println(getVersion((Inet6Address) address));
          }
          private static String getVersion(Inet6Address address) {
              return "IPv6";
          }
      }
    """.trimIndent()

    engine.execute(
        code = code,
        initialStdin = "",
        onOutput = { entry ->
          if (entry.type == TerminalStreamType.STDOUT) {
            output.append(entry.text).append("\n")
          }
        },
        onErrorDiagnostic = {},
        onFinished = { exitCode, _ ->
          finishedExitCode = exitCode
        }
    )

    assertEquals(0, finishedExitCode)
    assertTrue(output.toString().contains("IPv6"))
  }

  @Test
  fun testJavaExecutionEngineExecution() = runBlocking {
    val engine = JavaExecutionEngine()
    val output = StringBuilder()
    var finishedExitCode = -1

    val code = """
      public class Hello {
          public static void main(String[] args) {
              System.out.println("Computed: " + (5 * 6));
          }
      }
    """.trimIndent()

    engine.execute(
        code = code,
        initialStdin = "",
        onOutput = { entry ->
          if (entry.type == TerminalStreamType.STDOUT) {
            output.append(entry.text).append("\n")
          }
        },
        onErrorDiagnostic = {},
        onFinished = { exitCode, _ ->
          finishedExitCode = exitCode
        }
    )

    assertEquals(0, finishedExitCode)
    assertTrue(output.toString().contains("Computed: 30"))
  }

  @Test
  fun testJavaExecutionEngineStdinScanner() = runBlocking {
    val engine = JavaExecutionEngine()
    val output = StringBuilder()
    var finishedExitCode = -1

    val code = """
      import java.util.Scanner;
      public class StdinTest {
          public static void main(String[] args) {
              Scanner sc = new Scanner(System.in);
              int num = sc.nextInt();
              System.out.println("Double: " + (num * 2));
          }
      }
    """.trimIndent()

    engine.execute(
        code = code,
        initialStdin = "21",
        onOutput = { entry ->
          if (entry.type == TerminalStreamType.STDOUT) {
            output.append(entry.text).append("\n")
          }
        },
        onErrorDiagnostic = {},
        onFinished = { exitCode, _ ->
          finishedExitCode = exitCode
        }
    )

    assertEquals(0, finishedExitCode)
    assertTrue(output.toString().contains("Double: 42"))
  }

  @Test
  fun testFileStarterTemplateGeneration() {
    val mainContent = com.example.ui.files.FileStarterTemplate.STANDARD_MAIN.generateContent("MyTest")
    assertTrue(mainContent.contains("public class MyTest"))
    assertTrue(mainContent.contains("public static void main(String[] args)"))

    val scannerContent = com.example.ui.files.FileStarterTemplate.SCANNER_INPUT.generateContent("InputTest")
    assertTrue(scannerContent.contains("import java.util.Scanner;"))
    assertTrue(scannerContent.contains("public class InputTest"))
  }
}
