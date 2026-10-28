package com.efxcreator.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import java.util.Locale

/** 앱에서 고를 수 있는 표시 언어. [AUTO] 는 시스템 언어를 따른다. */
enum class AppLanguage(val code: String, private val fixedLocale: Locale?, val nativeName: String) {
    AUTO("auto", null, ""),
    KO("ko", Locale.KOREAN, "한국어"),
    EN("en", Locale.ENGLISH, "English"),
    JA("ja", Locale.JAPANESE, "日本語"),
    ZH_CN("zh-CN", Locale.SIMPLIFIED_CHINESE, "简体中文");

    /** 이 언어가 가리키는 실제 로캘. AUTO 는 지원 언어 중 시스템 언어에 가장 가까운 것 */
    fun resolveLocale(): Locale = fixedLocale ?: LocaleHelper.systemSupportedLocale()

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: AUTO

        /** 시스템 언어에 해당하는 지원 언어 (자동일 때 실제로 쓰이는 언어) */
        fun system(): AppLanguage = when (LocaleHelper.systemSupportedLocale().language) {
            "ko" -> KO
            "ja" -> JA
            "zh" -> ZH_CN
            else -> EN
        }
    }
}

/**
 * 앱 표시 언어 적용.
 * 저장된 언어를 읽어 Application/Activity 의 Context 에 로캘을 씌운다(attachBaseContext).
 * 언어를 바꾸면 이미 만들어진 목록·상수 같은 정적 값까지 새로 만들어지도록 앱을 다시 시작한다.
 */
object LocaleHelper {

    private const val PREFS = "efx_creator_prefs"
    private const val KEY = "app_language"

    fun saved(context: Context): AppLanguage =
        AppLanguage.fromCode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.code).commit()
    }

    /**
     * 시스템 언어를 지원 언어에 맞춘다: 한국어/영어/일본어/중국어(모든 중국어는 간체 문자열 사용).
     * 그 밖의 언어는 영어로 보여 준다.
     */
    fun systemSupportedLocale(): Locale =
        when (Resources.getSystem().configuration.locales[0].language) {
            "ko" -> Locale.KOREAN
            "ja" -> Locale.JAPANESE
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.ENGLISH
        }

    /** [base] 에 저장된 언어의 로캘을 씌운 Context 를 돌려준다 (Application/Activity 의 attachBaseContext 에서 호출) */
    fun wrap(base: Context): Context {
        val locale = saved(base).resolveLocale()
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** 앱을 처음부터 다시 시작한다 (언어 변경 적용) */
    fun restartApp(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) ?: return
        val pending = android.app.PendingIntent.getActivity(
            activity, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        // 프로세스를 끝내면 같은 프로세스에서 막 띄운 새 화면도 함께 죽으므로,
        // 새 프로세스에서 앱을 다시 열 예약(알람)을 먼저 걸고 이 프로세스를 끝낸다.
        // 이렇게 해야 언어에 따라 만들어 둔 정적 값(목록 등)도 모두 새로 만들어진다.
        val alarm = activity.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        alarm.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 300L, pending)
        activity.finishAffinity()
        Handler(Looper.getMainLooper()).postDelayed({ Runtime.getRuntime().exit(0) }, 100L)
    }
}

/**
 * 화면 어디서든(Compose 밖, ViewModel 포함) 현재 언어의 문자열을 꺼내는 도우미.
 * 한국어 원문 문자열을 리소스(`R.string.*`)로 옮긴 코드가 사용한다.
 */
object Loc {
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context
    }

    fun str(@StringRes id: Int, vararg args: Any?): String =
        if (args.isEmpty()) appContext.getString(id) else appContext.getString(id, *args)
}
