package com.prismo.escala

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray

/** Agenda e exibe as notificações calculadas pela interface (JS). */
object Agendador {
    private const val PREFS = "agendador"
    private const val K_LISTA = "lista"
    private const val K_IDS = "ids"

    fun criarCanais(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val canais = listOf(
            NotificationChannel("plantao", "Plantões", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos antes e no fim do plantão"
            },
            NotificationChannel("sono", "Alerta de sono", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Hora de dormir na véspera do plantão"
            },
            NotificationChannel("eventos", "Serviços e consultas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes de serviços extras, consultas e compromissos"
            },
            NotificationChannel("checklist", "Checklist", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Checklist de entrada e de saída do expediente"
            },
            NotificationChannel("resumo", "Resumos", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Resumo da véspera e da manhã"
            }
        )
        nm.createNotificationChannels(canais)
    }

    private fun intentAlarme(ctx: Context, id: Int, titulo: String = "", texto: String = "", canal: String = "plantao", aba: String = ""): Intent =
        Intent(ctx, AlarmeReceiver::class.java).apply {
            action = "com.prismo.escala.ALARME.$id"
            putExtra("id", id)
            putExtra("titulo", titulo)
            putExtra("texto", texto)
            putExtra("canal", canal)
            putExtra("aba", aba)
        }

    @Synchronized
    fun agendar(ctx: Context, json: String) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val am = ctx.getSystemService(AlarmManager::class.java)

        // cancela os anteriores
        prefs.getString(K_IDS, "")!!.split(",").filter { it.isNotBlank() }.forEach { s ->
            val id = s.toIntOrNull() ?: return@forEach
            val pi = PendingIntent.getBroadcast(
                ctx, id, intentAlarme(ctx, id),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) { am.cancel(pi); pi.cancel() }
        }

        val agora = System.currentTimeMillis()
        val arr = try { JSONArray(json) } catch (e: Exception) { JSONArray() }
        val ids = mutableListOf<Int>()
        val exato = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

        for (i in 0 until minOf(arr.length(), 450)) {
            val o = arr.optJSONObject(i) ?: continue
            val t = o.optLong("t")
            if (t <= agora) continue
            val id = o.optInt("id")
            val pi = PendingIntent.getBroadcast(
                ctx, id,
                intentAlarme(ctx, id, o.optString("titulo"), o.optString("texto"), o.optString("canal", "plantao"), o.optString("aba", "")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                if (exato) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
                ids.add(id)
            } catch (e: Exception) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
                ids.add(id)
            }
        }
        prefs.edit().putString(K_LISTA, json).putString(K_IDS, ids.joinToString(",")).apply()
    }

    /** Reagenda a última lista salva (após reiniciar o celular, atualizar o app etc.). */
    fun reagendar(ctx: Context) {
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(K_LISTA, null) ?: return
        agendar(ctx, json)
    }

    fun mostrar(ctx: Context, id: Int, titulo: String, texto: String, canal: String, aba: String = "") {
        criarCanais(ctx)
        val abrir = PendingIntent.getActivity(
            ctx, id, Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("aba", aba),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, canal)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF6D7CFF.toInt())
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setCategory(if (canal == "eventos") Notification.CATEGORY_EVENT else Notification.CATEGORY_REMINDER)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: SecurityException) { /* sem permissão */ }
    }
}
