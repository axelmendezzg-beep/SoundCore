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
            tvResult.text = "🧠 Cargando motores criptográficos (Meriyah + Core)..."

            thread {
                try {
                    // 1. Cargamos Meriyah (el parseador) de los assets
                    val meriyahCode = assets.open("meriyah.js").bufferedReader().use { it.readText() }
                    
                    // 2. Cargamos el resolvedor de yt-dlp
                    val coreJsCode = assets.open("yt.solver.core.js").bufferedReader().use { it.readText() }
                    
                    val inicio = System.currentTimeMillis()
                    
                    QuickJs.create().use { quickJs ->
                        // Inyectamos PRIMERO a Meriyah para que esté disponible globalmente
                        quickJs.evaluate(meriyahCode)
                        
                        // Inyectamos SEGUNDO el núcleo del descifrador
                        quickJs.evaluate(coreJsCode)
                        
                        // Extraemos el ID del video del enlace
                        val videoId = url.substringAfter("v=").substringBefore("&")
                        
                        // Script ejecutor de prueba
                        val payloadScript = """
                            (function() {
                                // Buscamos las funciones globales del solver
                                if (typeof sign === 'function') {
                                    return JSON.stringify({ status: "success", result: sign("$videoId") });
                                } else {
                                    return JSON.stringify({ 
                                        status: "loaded", 
                                        message: "Motores en línea. Meriyah activo. Buscando detonador del reto..." 
                                    });
                                }
                            })();
                        """.trimIndent()
                        
                        val resultadoJs = quickJs.evaluate(payloadScript) as String
                        val tiempoTotal = System.currentTimeMillis() - inicio

                        runOnUiThread {
                            tvResult.text = "✅ AMBOS MOTORES EN LÍNEA ($tiempoTotal ms)\n\nResultado:\n$resultadoJs"
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
