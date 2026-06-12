package com.example.cachefileloader

import android.content.Context

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cachefileloader.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioRecorderManager: AudioRecorderManager

    companion object {
        private const val REQUEST_AUDIO_PERMISSION = 100
    }

    private val pttReceiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        @RequiresApi(Build.VERSION_CODES.S)
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PttWidget.ACTION_PTT_START -> startTransmitting()
                PttWidget.ACTION_PTT_STOP  -> stopTransmitting()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        audioRecorderManager = AudioRecorderManager()
        checkAudioPermission()
        setupPTTButton()
        setupDummyStatus()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(PttWidget.ACTION_PTT_START)
            addAction(PttWidget.ACTION_PTT_STOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pttReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pttReceiver, filter)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onPause() {
        super.onPause()
        unregisterReceiver(pttReceiver)
        stopTransmitting()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorderManager.release()
    }

    private fun setupDummyStatus() {
        binding.tvChannel.text = "Channel 3 — Alpha Team"
        binding.tvStatus.text = "Standby"
        binding.tvMembersOnline.text = "4 members online"
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.S)
    private fun setupPTTButton() {
        binding.btnPTT.setOnTouchListener @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO) { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (hasAudioPermission()) startTransmitting() else requestAudioPermission()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopTransmitting(); true
                }
                else -> false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startTransmitting() {
        binding.tvStatus.text = "Transmitting..."
        binding.statusIndicator.setBackgroundResource(R.drawable.indicator_active)
        binding.pttWidget.startVisualizer()
        audioRecorderManager.startRecording { amplitude ->
            runOnUiThread { binding.pttWidget.updateAmplitude(amplitude) }
        }
        PttWidget.refreshAllWidgets(this)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun stopTransmitting() {
        binding.tvStatus.text = "Standby"
        binding.statusIndicator.setBackgroundResource(R.drawable.indicator_idle)
        binding.pttWidget.stopVisualizer()
        audioRecorderManager.stopRecording()
        PttWidget.refreshAllWidgets(this)
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

    private fun checkAudioPermission() { if (!hasAudioPermission()) requestAudioPermission() }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO_PERMISSION)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO_PERMISSION &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            binding.tvStatus.text = "Standby — Mic ready"
        }
    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
