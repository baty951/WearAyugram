package su.kirian.wearayugram.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlin.system.exitProcess

/**
 * Mini "process phoenix" for account switching. TelegramClient and the
 * repositories are process-wide singletons bound to one TDLib database, so the
 * only reliable way to switch accounts is a full process restart.
 *
 * Runs in its own :restart process (see the manifest): it survives the main
 * process's exit, relaunches MainActivity (which spawns a fresh main process
 * reading the new account slot) and then kills itself.
 */
class RestartActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
        Runtime.getRuntime().exit(0)
    }

    companion object {
        fun restartApp(context: Context) {
            context.startActivity(
                Intent(context, RestartActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            exitProcess(0)
        }
    }
}
