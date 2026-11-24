package com.prismo.escala

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmeReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Agendador.mostrar(
            ctx,
            intent.getIntExtra("id", 1),
            intent.getStringExtra("titulo") ?: "Prismo Escala",
            intent.getStringExtra("texto") ?: "",
            intent.getStringExtra("canal") ?: "plantao",
            intent.getStringExtra("aba") ?: ""
        )
    }
}
