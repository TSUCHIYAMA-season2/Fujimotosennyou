package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import no.nordicsemi.android.support.v18.scanner.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val requestCodeLocation = 1001
    private var isScanning = false

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val rssi = result.rssi
            Log.d("BLE", "Device: ${device.address}, RSSI: $rssi")

            showCurrentLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!hasPermissions()) {
            val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), requestCodeLocation)
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        map.uiSettings.isZoomControlsEnabled = true

        if (hasPermissions()) {
            try {
                map.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            // ビーコンが無くても必ず現在地表示
            showCurrentLocation()
            // 可能であれば BLE スキャン開始
            startBleScanSafe()
        } else {
            Toast.makeText(this, "位置情報のパーミッションがありません", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * FusedLocationProvider で現在地を取得してマーカーとカメラを更新
     */
    private fun showCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    map.clear()
                    map.addMarker(MarkerOptions().position(latLng).title("現在地"))
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
                } ?: run {
                    Toast.makeText(this, "現在地が取得できませんでした", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startBleScanSafe() {
        if (isScanning) return
        try {
            val scanner = BluetoothLeScannerCompat.getScanner()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            val filters = listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid.fromString("E94F746D-0BAD-1801-A367-001C4D7451AE"))
                    .build()
            )
            scanner.startScan(filters, settings, bleCallback)
            isScanning = true
        } catch (e: SecurityException) {
            Log.w("BLE", "BLE スキャンのパーミッションがありません: ${e.message}")
        } catch (e: IllegalStateException) {
            // 他プロセスが既にスキャンを実行中など
            Log.w("BLE", "BLE スキャン開始に失敗: ${e.message}")
        } catch (e: Exception) {
            Log.e("BLE", "予期せぬエラーにより BLE スキャンを開始できませんでした: ${e.message}")
        }
    }

    private fun stopBleScan() {
        if (!isScanning) return
        try {
            BluetoothLeScannerCompat.getScanner().stopScan(bleCallback)
        } catch (e: Exception) {
            Log.e("BLE", "BLE スキャン停止に失敗: ${e.message}")
        }
        isScanning = false
    }

    override fun onPause() {
        super.onPause()
        stopBleScan()
    }

    override fun onResume() {
        super.onResume()
        if (::map.isInitialized && hasPermissions()) {
            startBleScanSafe()
        }
    }

    private fun hasPermissions(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bluetoothScan = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else true
        val bluetoothConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true
        return fineLocation && bluetoothScan && bluetoothConnect
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodeLocation && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            try {
                if (::map.isInitialized) {
                    map.isMyLocationEnabled = true
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
            showCurrentLocation()
            startBleScanSafe()
        }
    }
}

