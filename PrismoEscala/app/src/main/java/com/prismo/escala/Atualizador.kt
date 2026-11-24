package com.prismo.escala

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Verifica a última versão publicada no GitHub Releases e instala o APK. */
object Atualizador {

    private fun numeros(v: String) = v.trim().removePrefix("v").removePrefix("V")
        .split(".").map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun maisNova(remota: String, local: String): Boolean {
        val a = numeros(remota); val b = numeros(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val d = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }

    private fun abrir(url: String): HttpURLConnection {
        var u = url
        repeat(6) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "PrismoEscala-App")
            c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream, */*")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: return c
                c.disconnect()
                u = URL(URL(u), loc).toString()
            } else return c
        }
        throw Exception("Muitos redirecionamentos")
    }

    /** Retorna um JSON {ok, nova, versao, notas, url, tamanho} para o JS. */
    fun verificar(repo: String, versaoLocal: String, fim: (String) -> Unit) {
        thread {
            val r = JSONObject()
            try {
                val c = abrir("https://api.github.com/repos/$repo/releases/latest")
                if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode}")
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val tag = j.optString("tag_name")
                val assets = j.optJSONArray("assets")
                var url = ""; var tam = 0L
                if (assets != null) for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", true)) {
                        url = a.optString("browser_download_url"); tam = a.optLong("size"); break
                    }
                }
                r.put("ok", true)
                r.put("versao", tag.removePrefix("v").removePrefix("V"))
                r.put("notas", j.optString("body"))
                r.put("url", url)
                r.put("tamanho", tam)
                r.put("nova", url.isNotEmpty() && maisNova(tag, versaoLocal))
            } catch (e: Exception) {
                r.put("ok", false); r.put("erro", e.message ?: "erro")
            }
            fim(r.toString())
        }
    }

    /** Baixa o APK e abre o instalador do Android. */
    fun baixarEInstalar(act: Activity, url: String, progresso: (Int) -> Unit, erro: (String) -> Unit) {
        thread {
            try {
                val arq = File(act.cacheDir, "atualizacao.apk")
                val c = abrir(url)
                if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode}")
                val total = c.contentLengthLong
                var lido = 0L; var ultimo = -1
                c.inputStream.use { ins ->
                    arq.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = ins.read(buf); if (n < 0) break
                            out.write(buf, 0, n); lido += n
                            if (total > 0) {
                                val p = (lido * 100 / total).toInt()
                                if (p != ultimo) { ultimo = p; progresso(minOf(p, 99)) }
                            }
                        }
                    }
                }
                progresso(100)
                instalar(act, arq)
            } catch (e: Exception) {
                erro("Falha ao baixar: ${e.message ?: "verifique sua internet"}")
            }
        }
    }

    private fun instalar(act: Activity, arq: File) {
        val pi = act.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(act.packageName)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("app.apk", 0, arq.length()).use { out ->
                arq.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val intent = Intent(act, InstalarReceiver::class.java).setAction("com.prismo.escala.INSTALAR")
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val status = PendingIntent.getBroadcast(act, id, intent, flags)
            s.commit(status.intentSender)
        }
    }
}
