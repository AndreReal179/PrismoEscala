package com.prismo.escala

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONObject
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var exportPendente: String? = null
    private var abaPendente: String? = null
    private var paginaPronta = false

    companion object {
        private const val REQ_EXPORT = 21
        private const val REQ_IMPORT = 22
        private const val REQ_NOTIF = 23
        private const val PREFS_DADOS = "dados"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF0A0E16.toInt()
        window.navigationBarColor = 0xFF0A0E16.toInt()
        Agendador.criarCanais(this)

        web = WebView(this)
        web.setBackgroundColor(0xFF0A0E16.toInt())
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = true
            textZoom = 100
        }
        web.overScrollMode = WebView.OVER_SCROLL_NEVER
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val url = req.url.toString()
                if (url.startsWith("file://")) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, req.url)) } catch (e: Exception) {}
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                paginaPronta = true
                abrirAbaPendente()
            }
        }
        web.addJavascriptInterface(Ponte(), "Android")
        setContentView(web)
        abaPendente = intent?.getStringExtra("aba")
        web.loadUrl("file:///android_asset/www/index.html")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        abaPendente = intent.getStringExtra("aba")
        if (paginaPronta) abrirAbaPendente()
    }

    private fun abrirAbaPendente() {
        val aba = abaPendente
        abaPendente = null
        if (!aba.isNullOrBlank()) web.evaluateJavascript("window.abrirAba&&window.abrirAba(${JSONObject.quote(aba)})", null)
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.evaluateJavascript("window.aoVoltarAoApp&&window.aoVoltarAoApp()", null)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        web.evaluateJavascript("(window.voltar&&window.voltar())?'1':'0'") { r ->
            if (r == null || !r.contains("1")) finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        web.evaluateJavascript("window.aoPermissao&&window.aoPermissao()", null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_EXPORT) {
            val conteudo = exportPendente
            exportPendente = null
            var ok = false
            if (resultCode == RESULT_OK && uri != null && conteudo != null) {
                try {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(conteudo.toByteArray(Charsets.UTF_8)) }
                    ok = true
                } catch (e: Exception) {}
            }
            if (resultCode == RESULT_OK) web.evaluateJavascript("window.aoExportar&&window.aoExportar($ok)", null)
        } else if (requestCode == REQ_IMPORT && resultCode == RESULT_OK && uri != null) {
            try {
                val txt = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                web.evaluateJavascript("window.importarBackup(${JSONObject.quote(txt)})", null)
            } catch (e: Exception) {
                Toast.makeText(this, "Não foi possível ler o arquivo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    inner class Ponte {
        private val ctx: Context get() = this@MainActivity

        @JavascriptInterface
        fun agendar(json: String) { thread { Agendador.agendar(applicationContext, json) } }

        @JavascriptInterface
        fun salvarDados(json: String) {
            getSharedPreferences(PREFS_DADOS, MODE_PRIVATE).edit().putString("estado", json).apply()
        }

        @JavascriptInterface
        fun lerDados(): String = getSharedPreferences(PREFS_DADOS, MODE_PRIVATE).getString("estado", "") ?: ""

        @JavascriptInterface
        fun notificacoesPermitidas(): Boolean {
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
            return getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }

        @JavascriptInterface
        fun pedirPermissaoNotificacao() {
            runOnUiThread {
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                    getSharedPreferences(PREFS_DADOS, MODE_PRIVATE).getBoolean("pediuNotif", false)) {
                    abrirConfigNotificacoes()
                } else if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    getSharedPreferences(PREFS_DADOS, MODE_PRIVATE).edit().putBoolean("pediuNotif", true).apply()
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
                } else if (!getSystemService(NotificationManager::class.java).areNotificationsEnabled()) {
                    abrirConfigNotificacoes()
                }
            }
        }

        private fun abrirConfigNotificacoes() {
            try {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun podeAlarmeExato(): Boolean =
            Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

        @JavascriptInterface
        fun abrirAlarmesExatos() {
            if (Build.VERSION.SDK_INT >= 31) runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                } catch (e: Exception) {}
            }
        }

        @JavascriptInterface
        fun bateriaOtimizada(): Boolean =
            !getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        @SuppressLint("BatteryLife")
        @JavascriptInterface
        fun pedirSemOtimizacao() {
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (e: Exception) {}
                }
            }
        }

        @JavascriptInterface
        fun testarNotificacao() {
            Agendador.mostrar(ctx, 7, "Prismo Escala ✅", "As notificações estão funcionando. Você será avisado do plantão e da hora de dormir.", "plantao")
        }

        @JavascriptInterface
        fun vibrar() {
            try {
                getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
            } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun exportar(json: String, nome: String) {
            runOnUiThread {
                exportPendente = json
                val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, nome)
                }
                try { startActivityForResult(i, REQ_EXPORT) } catch (e: Exception) {
                    Toast.makeText(ctx, "Não foi possível abrir o seletor de arquivos", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun importar() {
            runOnUiThread {
                val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                try { startActivityForResult(i, REQ_IMPORT) } catch (e: Exception) {}
            }
        }

        @JavascriptInterface
        fun verificarAtualizacao(repo: String) {
            Atualizador.verificar(repo, versao()) { json ->
                runOnUiThread { web.evaluateJavascript("window.aoAtualizacao&&window.aoAtualizacao($json)", null) }
            }
        }

        @JavascriptInterface
        fun podeInstalar(): Boolean = packageManager.canRequestPackageInstalls()

        @JavascriptInterface
        fun abrirPermissaoInstalar() {
            runOnUiThread {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                } catch (e: Exception) {}
            }
        }

        @JavascriptInterface
        fun atualizar(url: String) {
            Atualizador.baixarEInstalar(this@MainActivity, url,
                { p -> runOnUiThread { web.evaluateJavascript("window.aoProgressoAtualizacao&&window.aoProgressoAtualizacao($p)", null) } },
                { m -> runOnUiThread { web.evaluateJavascript("window.aoErroAtualizacao&&window.aoErroAtualizacao(${JSONObject.quote(m)})", null) } })
        }

        @JavascriptInterface
        fun versao(): String = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
    }
}
