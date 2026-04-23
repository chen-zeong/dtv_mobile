package dtv.mobile.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

object CrashFileLogger {
  @Volatile private var installed = false
  @Volatile private var handlingCrash = false

  fun install(context: Context) {
    if (installed) return
    installed = true

    val appContext = context.applicationContext
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      if (handlingCrash) {
        previous?.uncaughtException(thread, throwable) ?: run {
          Process.killProcess(Process.myPid())
          exitProcess(10)
        }
        return@setDefaultUncaughtExceptionHandler
      }

      handlingCrash = true
      runCatching { writeCrashFile(appContext, thread, throwable) }

      previous?.uncaughtException(thread, throwable) ?: run {
        Process.killProcess(Process.myPid())
        exitProcess(10)
      }
    }
  }

  private fun writeCrashFile(context: Context, thread: Thread, throwable: Throwable) {
    val dir = File(context.filesDir, "dtv-logs").apply { mkdirs() }
    val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    val file = File(dir, "crash-$ts-pid${Process.myPid()}.log")

    val header = buildString {
      append("time=")
      append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
      append('\n')
      append("pid=")
      append(Process.myPid())
      append(" tid=")
      append(Process.myTid())
      append(" thread=")
      append(thread.name)
      append('\n')
      append("process=")
      append(processName(context))
      append('\n')
      append("appVersion=")
      append(appVersionName(context))
      append('\n')
      append("sdk=")
      append(Build.VERSION.SDK_INT)
      append(" device=")
      append(Build.MANUFACTURER)
      append(' ')
      append(Build.MODEL)
      append(" abi=")
      append(Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
      append('\n')
      append("currentLog=")
      append(AppLog.currentLogFilePath().orEmpty())
      append('\n')
      append('\n')
    }

    file.writer().use { out ->
      out.write(header)
      out.write(stackTraceToString(throwable))
      out.write("\n")
      appendTailOfCurrentLog(out, maxLines = 250)
    }
  }

  private fun appendTailOfCurrentLog(out: Appendable, maxLines: Int) {
    val path = AppLog.currentLogFilePath() ?: return
    val f = File(path)
    if (!f.isFile) return

    val lines = runCatching { f.readLines() }.getOrNull() ?: return
    val tail = if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
    out.append("\n--- tail of dtv log (last ")
    out.append(tail.size.toString())
    out.append(" lines) ---\n")
    tail.forEach { line ->
      out.append(line)
      out.append('\n')
    }
  }

  private fun stackTraceToString(t: Throwable): String {
    val sw = StringWriter()
    PrintWriter(sw).use { pw -> t.printStackTrace(pw) }
    return sw.toString()
  }

  private fun processName(context: Context): String {
    if (Build.VERSION.SDK_INT >= 28) return android.app.Application.getProcessName()

    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val pid = Process.myPid()
    val list = am?.runningAppProcesses.orEmpty()
    return list.firstOrNull { it.pid == pid }?.processName.orEmpty()
  }

  @Suppress("DEPRECATION")
  private fun appVersionName(context: Context): String {
    val pm = context.packageManager ?: return ""
    val pkg = context.packageName ?: return ""
    return runCatching {
      val pi = pm.getPackageInfo(pkg, 0)
      pi.versionName.orEmpty()
    }.getOrDefault("")
  }
}

