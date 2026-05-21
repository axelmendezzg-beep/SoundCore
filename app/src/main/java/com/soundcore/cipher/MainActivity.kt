package com.soundcore.cipher

import android.os.Bundle
import android.app.Activity
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import app.cash.quickjs.QuickJs
import kotlin.concurrent.thread

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val btnSolve = findViewById<Button>(R.id.btnSolve)
        val tvResult = findViewById<TextView>(R.id.tvResult)

        btnSolve.setOnClickListener {
            val url = etUrl.text.toString()
            tvResult.text = "🧠 Ejecutando algoritmo matemático en la RAM..."

            // Corremos la inyección en un hilo separado para no trabar la interfaz
            thread {
                try {
                    // 1. Cargamos el JS desde los assets
                    val jsCode = assets.open("yt.solver.core.js").bufferedReader().use { it.readText() }
                    
                    val inicio = System.currentTimeMillis()
                    
                    // 2. Ejecutamos el descifrador nativo
                    QuickJs.create().use { quickJs ->
                        quickJs.evaluate(jsCode)
                        
                        // Extraemos el ID del video del link
                        val videoId = url.substringAfter("v=").substringBefore("&")
                        
                        // Simulamos la inyección del stdin que hace yt-dlp
                        val payloadScript = """
                            (val() {
                                // Llamada de prueba al núcleo del core.js
                                if (typeof sign === 'function') {
                                    return JSON.stringify({ status: "success", result: sign("$videoId") });
                                } else {
                                    return JSON.stringify({ status: "error", message: "Función sign no encontrada en core.js" });
                                }
                            })();
                        """.trimIndent()
                        
                        val resultadoJs = quickJs.evaluate(payloadScript) as String
                        val tiempoTotal = System.currentTimeMillis() - inicio

                        runOnUiThread {
                            tvResult.text = "✅ RESUELTO EN $tiempoTotal ms\n\nResultado:\n$resultadoJs"
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvResult.text = "❌ ERROR CRÍTICO:\n${e.message}"
                    }
                }
            }
        }
    }
}
