//nombre del package

package com.example.mirror_aplication

// Suprime advertencias específicas del compilador
import android.annotation.SuppressLint

// Clases para la creación y manejo de canales de notificaciones
import android.app.NotificationChannel
import android.app.NotificationManager

// Clases para manejar eventos de uso y estadísticas de uso
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager

// Clases para el contexto de la aplicación y manejo de intentos
import android.content.Context
import android.content.Intent

// Clases para el manejo y creación de bitmaps (imágenes)
import android.graphics.Bitmap
import android.graphics.BitmapFactory

// Clase para manejar el formato de píxeles
import android.graphics.PixelFormat

// Clases para manejar displays virtuales y de hardware
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay

// Clases para la lectura de imágenes y proyección de medios
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

// Clases para manejar versiones de Build y Bundle para almacenar datos
import android.os.Build
import android.os.Bundle

// Clases para manejar hilos y bucles de ejecución
import android.os.Handler
import android.os.Looper

// Clase para acceder a configuraciones del sistema
import android.provider.Settings

// Clases para manejo de métricas de pantalla y registro de logs
import android.util.DisplayMetrics
import android.util.Log

// Clases para elementos de la interfaz de usuario como botones, textos e imágenes
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

// Clases para actividades de componentes y manejo de notificaciones de compatibilidad
import androidx.activity.ComponentActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

// Clases para manejar streams de datos de entrada y salida, y excepciones de E/S
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException

// Clases para manejo de sockets de red e interfaces de red
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

// Clases para manejar ejecutores de hilos y colas de tareas
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

// Extensiones para facilitar el uso de hilos concurrentes en Kotlin
import kotlin.concurrent.thread
import kotlin.system.exitProcess


class MainActivity : ComponentActivity() {

    //Componentes de la interfaz
    private lateinit var ipShow: TextView
    private lateinit var ipInput: EditText
    private lateinit var roleSelect: TextView
    private lateinit var imageView: ImageView
    private lateinit var startAButton: Button
    private lateinit var startBButton: Button
    private lateinit var stopButton: Button

    //Rol y varaibles de permiso
    private var role: String? = null
    private val REQUEST_CODE_CAPTURE_PERM = 1234//Permiso para capturar la pantalla
    private val REQUEST_CODE_USAGE_STATS = 5678//Permiso para acceder a las estadísticas de uso
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var imageReader: ImageReader

    //Servicios e hilos
    private lateinit var screenCaptureService: ScreenCaptureService
    private val imageQueue = LinkedBlockingQueue<Bitmap>()//Cola para almacenar las imagenes
    private val executorService = Executors.newFixedThreadPool(4) //Grupo de subprocesos para operaciones de red (grupo de hilos)
    private var lastForegroundAppPackage: String?= null//Realiza un seguimiento de la última aplicación en primer plano a la que se accedió
    private var imageListener: ImageListener? = null
    private var notificationListener: NotificationListener? = null
    private var isServiceStopped = false // Flag para verificar si el servicio ha sido detenido


    // --- Definicion de funciones ---

    // Inicializa la actividad
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializa los componentes de la interfaz
        initUI()

        // Muestra la dirección IP local
        ipShow.text = getLocalIpAddress()

        // Crea el canal de notificación para Android 8.0 o mas
        createNotificationChannel()

        //Inicia el cliente
        startAButton.setOnClickListener { onStartClientButtonClick() }

        //Inicia el Servidor
        startBButton.setOnClickListener { onStartServerButtonClick() }

        stopButton.setOnClickListener { onStopButtonClick() }

        //Inicializa el servicio de captura de pantalla
        screenCaptureService = ScreenCaptureService()


    }

    // Componentes de la interfaz
    private fun initUI() {
        ipShow = findViewById(R.id.ip_show)
        ipInput = findViewById(R.id.ip_input)
        roleSelect = findViewById(R.id.role_select)
        imageView = findViewById(R.id.image_view)
        startAButton = findViewById(R.id.start_a)
        startBButton = findViewById(R.id.start_b)
        stopButton = findViewById(R.id.stop_button)
    }


    // Funciones para manejar los eventos del cliente
    private fun onStartClientButtonClick() {
        roleSelect.text = "Cliente"//Cambia el texto del rol
        val serverIp = ipInput.text.toString()//Obtiene la ip del servidor
        if (serverIp.isEmpty()) {//Verifica que la ip no este vacia
            Toast.makeText(
                this, "Por favor ingrese la IP del servidor (Telefono B)", Toast.LENGTH_SHORT
            ).show()
        } else {
            startClient(serverIp)//inicia el cliente

        }
    }

    // //Funciones para manejar los eventos del servidor
    private fun onStartServerButtonClick() {
        roleSelect.text = "Servidor"
        val clientIp = ipInput.text.toString()
        if (clientIp.isEmpty()) {//Verifica que la ip no este vacia
            Toast.makeText(
                this, "Por favor ingrese la IP del cliente (Telefono A)", Toast.LENGTH_SHORT
            ).show()
        } else {
            if (checkUsageStatsPermission() && checkOverlayPermission()) {
                startService(Intent(this, ScreenCaptureService::class.java))
                startScreenCapture()//Inicia la captura de pantalla
            } else {
                requestPermissions()
            }
        }
    }

    // Verifica si el cliente o el servidor están activos
    private fun isClientOrServerActive(): Boolean {
        val roleText = roleSelect.text.toString().trim()
        return roleText == "Cliente" || roleText == "Servidor"
    }

   // Funciones para manejar los eventos de cierre de la aplicacion
   private fun onStopButtonClick() {
       if (isServiceStopped) {
           Toast.makeText(this, "El servicio ya está detenido", Toast.LENGTH_SHORT).show()
           return
       }

       if (!isClientOrServerActive()) {
           Toast.makeText(this, "No hay servicio a detener", Toast.LENGTH_SHORT).show()
           return
       }

       isServiceStopped = true

       val serviceStopped = stopScreenCaptureService() || stopImageListener() || stopNotificationListener()

       if (serviceStopped) {
           Toast.makeText(this, "Servicio detenido con éxito", Toast.LENGTH_SHORT).show()
           resetApp()
       }
   }



    // Reinicia la aplicación
    private fun resetApp() {
        // Oculta el ImageView
        runOnUiThread {
            imageView.visibility = ImageView.GONE
        }

        // Reinicia la actividad actual
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()

    }


    // Funciones para detener el servicio de captura de pantalla
    private fun stopScreenCaptureService() :Boolean {
        if (!isClientOrServerActive()) {
            return false
        }
        stopService(Intent(this, ScreenCaptureService::class.java))
        mediaProjection?.stop()
        virtualDisplay?.release()
        mediaProjection = null
        virtualDisplay = null
        return true
    }

    // Funciones para detener los listeners de imágenes y notificaciones
    private fun stopImageListener():Boolean {
        if (!isClientOrServerActive()) {
            return false
        }
        imageListener?.interrupt()
        imageListener = null
        return true
    }

    private fun stopNotificationListener():Boolean {
        if (!isClientOrServerActive()) {
            return false
        }
        notificationListener?.interrupt()
        notificationListener = null
        return true
    }

    // Verifica si la aplicación tiene permiso para acceder a las estadísticas de uso
    private fun checkUsageStatsPermission(): Boolean {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()//Tiempo actual en milisegundos
        val beginTime = endTime - 1000 * 60 // Últimos 60 segundos
        val usageEvents =
            usageStatsManager.queryEvents(beginTime, endTime)//Consulta las estadísticas de uso
        return usageEvents.hasNextEvent()//Verifica si hay eventos
    }

    // Verifica si la aplicación tiene permiso para superponer sobre otras aplicaciones
    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {// Verifica la versión de Android
            Settings.canDrawOverlays(this)//Verifica si la aplicación tiene permiso para superponer
        } else {
            true
        }
    }

    // Solicita los permisos necesarios si no están concedidos
    private fun requestPermissions() {
        if (!checkUsageStatsPermission()) {
            startActivityForResult(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), REQUEST_CODE_USAGE_STATS
            )//Solicita el permiso para acceder a las estadísticas de uso
        }
        if (!checkOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")//Solicita el permiso para superponer
            )
            startActivityForResult(
                intent, REQUEST_CODE_CAPTURE_PERM
            )//Inicia la actividad para solicitar el permiso
        }
    }

    // Inicia la captura de pantalla
    private fun startScreenCapture() {
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager//Obtiene el servicio de captura de pantalla
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_CAPTURE_PERM
        )//Inicia la actividad para solicitar la captura de pantalla
    }

    // Maneja los resultados de las solicitudes de permisos
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CAPTURE_PERM) {// Verifica si la solicitud es para la captura de pantalla
            if (resultCode == RESULT_OK && data != null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(
                    resultCode, data
                )//Obtiene la proyección capturada
                setupVirtualDisplay()//Configura el VirtualDisplay para la captura de pantalla
                startServer()//Inicia el servidor
                startAppDetection()//Inicia el servicio de detección de aplicaciones en primer plano
            } else {
                Toast.makeText(this, "Servicio de Screen Cast Denegado", Toast.LENGTH_SHORT)
                    .show()//Muestra un mensaje de error si el usuario cancela la captura de pantalla
            }
        }
    }

    // Configura el VirtualDisplay para la captura de pantalla
    private fun setupVirtualDisplay() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)//Obtiene las medidas de la pantalla

        val width = metrics.widthPixels / 2 // Divide la pantalla en dos
        val height = metrics.heightPixels / 2 // Divide la pantalla en dos

        imageReader = ImageReader.newInstance(
            width, height, PixelFormat.RGBA_8888, 2
        )//Crea el ImageReader para la captura de pantalla
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )//Crea el VirtualDisplay para la captura de pantalla

        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
            )//Crea un bitmap para la captura de pantalla

            bitmap.copyPixelsFromBuffer(buffer)//Copia los datos del buffer al bitmap
            image.close()//Cierra la imagen

            // Add bitmap to the queue for processing
            try {
                sendImage(bitmap)//envia la imagen del servidor
                // imageQueue.put(bitmap)//con delay
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, Handler(Looper.getMainLooper()))//Ejecuta el VirtualDisplay en un hilo
    }

    // Envia la imagen al servidor
    private fun sendImage(bitmap: Bitmap) {
        if (isServiceStopped) return
        val clientIp = ipInput.text.toString()
        executorService.execute {//Ejecuta la tarea en un hilo
            try {
                Log.d("MainActivity", "Conectando al cliente: $clientIp")
                Socket(clientIp, 8081).use { socket ->
                    DataOutputStream(socket.getOutputStream()).use { outputStream ->
                        val byteArrayOutputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
                        val byteArray = byteArrayOutputStream.toByteArray()
                        if (byteArray.isNotEmpty()) {// Verifica si el arreglo de bytes no está vacío
                            Log.d(
                                "MainActivity", "Enviando imagen de tamaño: ${byteArray.size}"
                            )//envia la imagen al servidor
                            outputStream.writeInt(byteArray.size)
                            outputStream.write(byteArray)
                            outputStream.flush()
                            Log.d("MainActivity", "Imagen enviada satisfactoriamente")
                        } else {
                            Log.e("MainActivity", "Byte array is empty")
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("MainActivity", "IOException enviando una imagen", e)
            }
        }
    }

    // Inicia el cliente
    private fun startClient(serverIp: String) {
        executorService.execute {
            try {
                val socket = Socket(serverIp, 8080)
                val outputStream = DataOutputStream(socket.getOutputStream())
                val localIp = getLocalIpAddress()
                outputStream.writeUTF(localIp)
                outputStream.flush()
                socket.close()

                imageListener = ImageListener().apply { start() }
                notificationListener = NotificationListener().apply { start() }

            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // Hilo que escucha conexiones entrantes y recibe imágenes
    private inner class ImageListener : Thread() {
        override fun run() {
            try {
                val serverSocket = ServerSocket(8081)
                while (!isInterrupted) {
                    val imageSocket = serverSocket.accept()
                    if (isServiceStopped) return
                    executorService.execute {
                        try {
                            DataInputStream(imageSocket.getInputStream()).use { inputStream ->
                                while (!isInterrupted) {
                                    val length = try {
                                        inputStream.readInt()
                                    } catch (e: EOFException) {
                                        break
                                    }
                                    if (length > 0) {
                                        val byteArray = ByteArray(length)
                                        inputStream.readFully(byteArray)
                                        val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                                        if (bitmap != null) {
                                            runOnUiThread {
                                                imageView.setImageBitmap(bitmap)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        } finally {
                            try {
                                imageSocket.close()
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // Hilo que escucha conexiones entrantes y recibe notificaciones
    private inner class NotificationListener : Thread() {
        override fun run() {
            try {
                val serverSocket = ServerSocket(8082)
                while (!isInterrupted) {
                    val notificationSocket = serverSocket.accept()
                    if (isServiceStopped) return
                    executorService.execute {
                        try {
                            val inputStream = DataInputStream(notificationSocket.getInputStream())
                            val message = inputStream.readUTF()
                            runOnUiThread {
                                showNotification(message)
                            }
                        } catch (e: IOException) {
                            e.printStackTrace()
                        } finally {
                            notificationSocket.close()
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // Inicia el servidor
    private fun startServer() {
        executorService.execute {
            try {
                val serverSocket = ServerSocket(8080)//Crea un socket para la conexión
                while (true) {
                    val clientSocket = serverSocket.accept()
                    if (isServiceStopped) return@execute
                    val inputStream = DataInputStream(clientSocket.getInputStream())
                    val clientIp = inputStream.readUTF()
                    sendImagesToClient(clientIp)
                    inputStream.close()
                    clientSocket.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // Envia las imágenes al cliente
    private fun sendImagesToClient(clientIp: String)  {
        executorService.execute {
            while (true) {
                val bitmap = imageQueue.take()// Obtiene una imagen de la cola
                try {
                    val socket = Socket(clientIp, 8081)//Crea un socket para la conexión
                    val outputStream = DataOutputStream(socket.getOutputStream())
                    val byteArrayOutputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
                    val byteArray = byteArrayOutputStream.toByteArray()
                    outputStream.writeInt(byteArray.size)
                    outputStream.write(byteArray)
                    outputStream.flush()
                    socket.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }

        }

    }

    // Obtiene la dirección IP local
    private fun getLocalIpAddress(): String? {
        try {
            val en = NetworkInterface.getNetworkInterfaces()//Obtiene las interfaces de red
            while (en.hasMoreElements()) {//Recorre las interfaces
                val intf = en.nextElement() as NetworkInterface//Obtiene la interfaz actual
                val enumIpAddr =
                    intf.inetAddresses//Obtiene los direcciones IP de la interfaz actual
                while (enumIpAddr.hasMoreElements()) {//Recorre las direcciones IP de la interfaz actual
                    val inetAddress = enumIpAddr.nextElement()//Obtiene la dirección IP actual
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {//Verifica si la dirección IP es una dirección IPv4 y no es un loopback
                        return inetAddress.hostAddress//Obtiene la dirección IP local
                    }
                }
            }
        } catch (ex: SocketException) {
            Log.e("Direccion IP", ex.toString())
        }
        return "IP no disponible"
    }

    // Detecta las aplicaciones en primer plano
    @SuppressLint("WrongConstant")
    private fun detectForegroundApp() {
        val usageStatsManager =
            getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager//Obtiene el servicio de estadísticas de uso
        val endTime = System.currentTimeMillis()//Tiempo actual en milisegundos
        val beginTime = endTime - 10000 // últimos 10 segundos
        val usageEvents =
            usageStatsManager.queryEvents(beginTime, endTime)//Consulta las estadísticas de uso
        var lastEvent: UsageEvents.Event? = null//guarda el ultimo evento

        while (usageEvents.hasNextEvent()) {//Recorre las estadísticas de uso
            val event = UsageEvents.Event()//Crea un evento
            usageEvents.getNextEvent(event) //Obtiene el siguiente evento
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {//Verifica si el evento es un evento de aplicación en primer plano
                lastEvent = event//guarda el ultimo evento
            }
        }

        lastEvent?.let {//Verifica si hay un evento
            val currentPackageName = it.packageName//Obtiene el nombre de la aplicación actual
            if (currentPackageName != lastForegroundAppPackage) {//Verifica si el nombre de la aplicación actual es diferente al nombre de la aplicación anterior
                lastForegroundAppPackage =
                    currentPackageName//guarda el nombre de la aplicación actual
                sendAppAccessNotification(currentPackageName)//envia la notificación al cliente
            }
        }
    }

    // Envia la notificación al cliente
    private fun sendAppAccessNotification(packageName: String) {
        val clientIp = ipInput.text.toString()
        thread {
            try {
                val socket = Socket(clientIp, 8082)//Crea un socket para la conexión
                val outputStream =
                    DataOutputStream(socket.getOutputStream())//Obtiene el flujo de salida del socket
                outputStream.writeUTF("Se ha accedido a: $packageName")//Envia la notificación al cliente
                outputStream.flush()//Vacia el flujo de salida
                socket.close()//Cierra el socket
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    // Inicia el servicio de detección de aplicaciones en primer plano
    private fun startAppDetection() {
        val handler = Handler(Looper.getMainLooper())//Crea un handler para el hilo principal
        val runnable = object : Runnable {
            override fun run() {
                detectForegroundApp()//detecta las aplicaciones en primer plano
                handler.postDelayed(this, 2000) // revisar cada 2 segundos
            }//
        }//Ejecuta la tarea en un hilo
        handler.post(runnable)//Ejecuta la tarea en el hilo principal
    }

    // Crea el canal de notificación para Android 7.0 o mas
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {// Verifica la versión de Android
            val name = "Canal de acceso de notificaciones"//Nombre del canal
            val descriptionText = "Channel for app access notifications"//Descripción del canal
            val importance = NotificationManager.IMPORTANCE_DEFAULT//Importancia del canal
            val channel = NotificationChannel("APP_ACCESS_CHANNEL", name, importance).apply {
                description = descriptionText
            }//Crea el canal de notificación
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)//Crea el canal de notificación
        }
    }

    //
    @SuppressLint("MissingPermission")
    private fun showNotification(message: String) {
        val builder = NotificationCompat.Builder(
            this, "APP_ACCESS_CHANNEL"
        )   .setSmallIcon(R.drawable.ic_notification)//Icono de la notificación
            .setContentTitle("Aplicación en curso")//Titulo de la notificación
            .setContentText(message)//Mensaje de la notificación
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())// muestra la aplicación en la interfaz
        }
    }

    //cierra la app
    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, ScreenCaptureService::class.java))
        executorService.shutdown()
    }


}
