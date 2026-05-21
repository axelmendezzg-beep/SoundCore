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
            tvResult.text = "🧠 Detonando jsc() con retos criptográficos en la RAM..."

            thread {
                try {
                    // 1. Cargar la trilogía de los assets
                    val astringCode = assets.open("astring.js").bufferedReader().use { it.readText() }
                    val meriyahCode = assets.open("meriyah.js").bufferedReader().use { it.readText() }
                    val coreJsCode = assets.open("yt.solver.core.js").bufferedReader().use { it.readText() }
                    
                    val inicio = System.currentTimeMillis()
                    
                    QuickJs.create().use { quickJs ->
                        // 2. Inyectar todo el entorno en la RAM
                        quickJs.evaluate(astringCode)
                        quickJs.evaluate(meriyahCode)
                        quickJs.evaluate(coreJsCode)
                        
                        // 3. Clonamos el formato exacto que usa yt-dlp en Python
                        // Le pasamos un reto tipo 'n' de prueba para ver el descifrado del límite de velocidad
                        val payloadScript = """
                            (function() {
                                var mockData = {
                                    "type": "player",
                                    "player": "function(a){a=a.split('');var b=a.reverse();return b.join('')}", // Un player simulado limpio
                                    "requests": [
                                        { "type": "n", "challenges": ["N_RETO_DE_PRUEBA_A"] },
                                        { "type": "sig", "challenges": ["SIG_RETO_DE_PRUEBA_B"] }
                                    ],
                                    "output_preprocessed": true
                                };
                                
                                // Ejecutamos la función jsc que descubrimos en el head/tail
                                if (typeof jsc === 'function') {
                                    var response = jsc(mockData);
                                    return JSON.stringify(response, null, 2);
                                } else {
                                    return JSON.stringify({ error: "La función global jsc no se inicializó correctamente." });
                                }
                            })();
                        """.trimIndent()
                        
                        // 4. Corremos el descifrador
                        val resultadoString = quickJs.evaluate(payloadScript) as String
                        val tiempoTotal = System.currentTimeMillis() - inicio

                        runOnUiThread {
                            tvResult.text = "⚡ FUSILADO COMPLETO EN $tiempoTotal ms\n\nRespuesta de InnerTube Solver:\n$resultadoString"
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        tvResult.text = "❌ ERROR AL DETONAR ESCUDO:\n${e.message}"
                    }
                }
            }
        }
    }
}
