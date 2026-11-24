package com.prismo.escala

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/** Recebe o resultado do instalador e abre a tela de confirmação do Android. */
class InstalarReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmar: Intent? = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmar?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { ctx.startActivity(it) } catch (e: Exception) {}
                }
            }
            PackageInstaller.STATUS_SUCCESS -> { /* o app reinicia na nova versão */ }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "instalação cancelada"
                Toast.makeText(ctx, "Atualização não concluída: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
