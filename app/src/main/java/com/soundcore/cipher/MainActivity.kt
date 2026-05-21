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
            tvResult.text = "🧠 Cargando Trilogía Criptográfica (Astring + Meriyah + Core)..."

            thread {
                try {
                    // 1. Cargamos Astring (El reconstructor)
                    val astringCode = assets.open("astring.js").bufferedReader().use { it.readText() }
                    
                    // 2. Cargamos Meriyah (El desarmador)
                    val meriyahCode = assets.open("meriyah.js").bufferedReader().use { it.readText() }
                    
                    // 3. Cargamos el núcleo de yt-dlp
                    val coreJsCode = assets.open("yt.solver.core.js").bufferedReader().use { it.readText() }
                    
                    val inicio = System.currentTimeMillis()
                    
                    QuickJs.create().use { quickJs ->
                        // Inyectamos las dependencias globales en orden
                        quickJs.evaluate(astringCode)
                        quickJs.evaluate(meriyahCode)
                        quickJs.evaluate(coreJsCode)
                        
                        // Extraemos el ID del video
                        val videoId = url.substringAfter("v=").substringBefore("&")
                        
                        val payloadScript = """
                            (function() {
                                if (typeof sign === 'function') {
                                    return JSON.stringify({ status: "success", result: sign("$videoId") });
                                } else {
                                    return JSON.stringify({ 
                                        status: "ready", 
                                        message: "Ecosistema completo. Astring y Meriyah operativos en la RAM de Android." 
                                    });
                                }
                            })();
                        """.trimIndent()
                        
                        val resultadoJs = quickJs.evaluate(payloadScript) as String
                        val tiempoTotal = System.currentTimeMillis() - inicio

                        runOnUiThread {
                            tvResult.text = "✅ TRILOGÍA CARGADA EN RAM ($tiempoTotal ms)\n\nResultado:\n$resultadoJs"
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
