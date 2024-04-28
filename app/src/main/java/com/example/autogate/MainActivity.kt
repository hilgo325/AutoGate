package com.example.autogate

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.telephony.TelephonyManager
import android.os.Handler
import android.os.Looper

object CallHelper {

    @SuppressLint("DiscouragedPrivateApi")
    fun endCall(context: Context) {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val endCallMethod = telephonyManager.javaClass.getDeclaredMethod("endCall")
        endCallMethod.isAccessible = true

        // Use a handler to delay the call to endCall()
        Handler(Looper.getMainLooper()).postDelayed({
            endCallMethod.invoke(telephonyManager)
        }, 1000) // End call after 1 second
    }
}


class MainActivity : AppCompatActivity() {
    private val REQUEST_CALL_PHONE_PERMISSION = 1
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001 // Any unique value

    private val phoneNumberMap = mutableMapOf<String, String>()
    private val gatesLocation = mutableMapOf<String, Location>()

    private val REQUEST_LOCATION_PERMISSION = 1
    private lateinit var locationManager: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private val checkIntervalMillis: Long = 3 * 1000 // 3 seconds


    @SuppressLint("UseSwitchCompatOrMaterialCode", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set up gate locations
        setupGateLocations()

        // Set up phone numbers
        setupPhoneNumbers()

        // Start periodic location check
        startLocationCheck()

        val switch: Switch = findViewById(R.id.SwitchOnOff)
        val spinner: Spinner = findViewById(R.id.SpinnerGateList)
        val phoneNumberEditText: TextView = findViewById(R.id.PhoneNumber)
        val callButton: Button = findViewById(R.id.BtnCall)

        val options = arrayOf("Shallavim", "Nof_Ayalon")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val selectedItem = options[position]
                val phoneNumber = phoneNumberMap[selectedItem]

                phoneNumberEditText.text = phoneNumber

                // Do something with the selected item
                println("Selected item: $selectedItem")
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                TODO("Not yet implemented")
            }
        }

        phoneNumberEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE || (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val selectedOption = spinner.selectedItem.toString()
                val newPhoneNumber = phoneNumberEditText.text.toString()
                phoneNumberMap[selectedOption] = newPhoneNumber
                // Hide keyboard
                phoneNumberEditText.clearFocus()
                // Optionally, you can update the TextView here as well
                true
            } else {
                false
            }
        }
        callButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CALL_PHONE),
                    REQUEST_CALL_PHONE_PERMISSION
                )
            } else {
                // Permission is granted
                makePhoneCall(phoneNumberEditText.text.toString())
            }
        }

        // Check if the permission has been granted already
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission is already granted, you can proceed with location-related tasks
            // For example, start getting the device's location
            // getLocation()
        }
    }

    private fun setupGateLocations() {
        gatesLocation["Shallavim"] = createLocation(31.873528, 34.978891)
        gatesLocation["Nof_Ayalon"] = createLocation(31.868717, 34.991361)
    }

    private fun setupPhoneNumbers() {
        phoneNumberMap["Shallavim"] = "1234567890"
        phoneNumberMap["Nof_Ayalon"] = "0987654321"
    }

    private fun createLocation(latitude: Double, longitude: Double): Location {
        val location = Location("")
        location.latitude = latitude
        location.longitude = longitude
        return location
    }

    private fun makePhoneCall(phoneNumber: String) {
        val callIntent = Intent(Intent.ACTION_CALL)
        callIntent.data = Uri.parse("tel:$phoneNumber")
        startActivity(callIntent)
        CallHelper.endCall(this)
    }

    private fun startLocationCheck() {

        handler.postDelayed(locationCheckRunnable, checkIntervalMillis)
    }

    private val locationCheckRunnable = object : Runnable {
        override fun run() {
            getLocation()
            handler.postDelayed(this, checkIntervalMillis)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocation() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
            return
        }

        locationManager.requestSingleUpdate(
            LocationManager.NETWORK_PROVIDER,
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    val spinner: Spinner = findViewById(R.id.SpinnerGateList)
                    val selectedGate = spinner.selectedItem as String
                    val destinationLocation = gatesLocation[selectedGate]

                    // Calculate distance
                    if (destinationLocation != null) {
                        val distance = location.distanceTo(destinationLocation) // in meters
                        val distanceInKm = distance / 1000 // convert to kilometers
                        val distanceText = "Distance to destination: %.2f km".format(distanceInKm)
                        findViewById<TextView>(R.id.distanceTextView).text = distanceText

                        // Check if the distance is less than or equal to 100 meters
                        if (distance <= 600) {
                            // Initiate a phone call to the corresponding phone number
                            val phoneNumber = phoneNumberMap[selectedGate]
                            if (phoneNumber != null) {
                                makePhoneCall(phoneNumber)
                            } else {
                                Toast.makeText(
                                    applicationContext,
                                    "Phone number not found for $selectedGate",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                }

                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            },
            null
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLocation()
            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(locationCheckRunnable)
    }
}