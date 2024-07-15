package com.example.mirror_aplication

// Importaciones necesarias para notificaciones y servicios
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

// Clase que extiende Service para realizar la captura de pantalla
class ScreenCaptureService : Service() {

    // Método que se llama cuando se crea el servicio
    override fun onCreate() {
        super.onCreate()
        // Crea el canal de notificación necesario para el servicio en primer plano
        createNotificationChannel()
        // Inicia el servicio en primer plano con una notificación
        startForegroundService()
    }

    // Método requerido al extender Service, retorna null ya que no es un servicio enlazado
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Método para crear el canal de notificación necesario en Android O y versiones posteriores
    private fun createNotificationChannel() {
        try {
            // Verifica si la versión de Android es Oreo (API 26) o superior
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Configura las propiedades del canal de notificación
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Servicio de Captura de Pantalla",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
                // Obtiene el servicio de notificación del sistema y crea el canal de notificación
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            }
        } catch (e: Exception) {
            // Maneja cualquier excepción que ocurra durante la creación del canal
            e.printStackTrace()
        }
    }

    // Método para iniciar el servicio en primer plano con una notificación
    private fun startForegroundService() {
        try {
            // Crea la notificación para el servicio en primer plano
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Servicio de captura de pantalla")
                .setSmallIcon(R.drawable.ic_notification)
                .build()

            // Inicia el servicio en primer plano con la notificación creada
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // Maneja cualquier excepción que ocurra durante el inicio del servicio en primer plano
            e.printStackTrace()
        }
    }

    // Objeto companion para definir constantes
    companion object {
        private const val CHANNEL_ID = "screen_capture_channel" // ID del canal de notificación
        private const val NOTIFICATION_ID = 1 // ID de la notificación
    }
}
