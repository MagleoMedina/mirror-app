package com.example.mirror_aplication

// Importaciones necesarias para la funcionalidad de la actividad
import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    // Duración de la pantalla de presentación (4.5 segundos)
    private val SPLASH_DISPLAY_LENGTH = 4500L

    // Método que se llama cuando se crea la actividad
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Establece el diseño de la actividad de splash
        setContentView(R.layout.activity_splash)

        // Usa un Handler para retrasar la ejecución de un bloque de código
        Handler(Looper.getMainLooper()).postDelayed({
            // Obtiene las preferencias compartidas
            val sharedPreferences = getSharedPreferences("prefs", MODE_PRIVATE)
            // Verifica si es la primera vez que se ejecuta la aplicación
            val firstRun = sharedPreferences.getBoolean("firstRun", true)

            if (firstRun) {
                // Si es la primera vez, actualiza las preferencias compartidas
                val editor: SharedPreferences.Editor = sharedPreferences.edit()
                editor.putBoolean("firstRun", false)
                editor.apply()
            }

            // Crea un Intent para iniciar MainActivity
            val mainIntent = Intent(this@SplashActivity, MainActivity::class.java)
            // Inicia MainActivity
            startActivity(mainIntent)
            // Finaliza la actividad actual
            finish()
        }, SPLASH_DISPLAY_LENGTH) // Tiempo de espera antes de ejecutar el bloque de código
    }
}
