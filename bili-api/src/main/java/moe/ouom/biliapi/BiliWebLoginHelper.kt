package moe.ouom.biliapi

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract

class BiliWebLoginHelper {

    companion object {
        const val RESULT_COOKIE = "result_cookie_json"

        fun startLogin(
            activity: Activity,
            launcher: ActivityResultLauncher<Intent>
        ) {
            launcher.launch(Intent(activity, getBiliWebLoginActivityClass()))
        }

        fun startLoginWithResult(
            activity: androidx.activity.ComponentActivity,
            callback: (String?) -> Unit
        ) {
            val launcher = activity.registerForActivityResult(
                object : ActivityResultContract<Intent, String?>() {
                    override fun createIntent(context: Context, input: Intent): Intent {
                        return input
                    }

                    override fun parseResult(resultCode: Int, intent: Intent?): String? {
                        if (resultCode == Activity.RESULT_OK) {
                            return intent?.getStringExtra(RESULT_COOKIE)
                        }
                        return null
                    }
                }
            ) { json ->
                callback(json)
            }
            launcher.launch(Intent(activity, getBiliWebLoginActivityClass()))
        }

        private fun getBiliWebLoginActivityClass(): Class<*> {
            return try {
                Class.forName("com.qian.jianyin.BiliWebLoginActivity")
            } catch (e: ClassNotFoundException) {
                try {
                    Class.forName("moe.ouom.neriplayer.activity.BiliWebLoginActivity")
                } catch (e2: ClassNotFoundException) {
                    throw IllegalStateException(
                        "BiliWebLoginActivity not found. Please implement your own BiliWebLoginActivity " +
                        "that returns cookie via RESULT_COOKIE extra."
                    )
                }
            }
        }
    }
}