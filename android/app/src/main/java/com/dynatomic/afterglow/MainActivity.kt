// PolyForm Noncommercial 1.0.0. Required Notice: Copyright 2026 Dynatomic
package com.dynatomic.afterglow

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import kotlin.math.*

/** Foreground-only controls. A transport callback never submits or resumes lighting. */
class MainActivity : Activity(), LightController.Listener {
    private val background = Color.rgb(20, 20, 24)
    private val panel = Color.rgb(37, 35, 44)
    private val ink = Color.rgb(244, 240, 248)
    private val muted = Color.rgb(176, 167, 187)
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var controller: LightController
    private var desired = LightState()
    private var displayedColor = desired.color
    private var ready = false
    private var busy = false
    private var discovering = false
    private var foreground = false
    private var destroyed = false
    private var binding = false
    private var hardwareCycle = false
    private var cycleRunning = false
    private var cyclePaused = false
    private var cycleHue = 0f
    private var status = "Not connected"
    private enum class BluetoothAction { SCAN, CONNECT_SAVED, CONNECT_NEARBY }
    private data class PendingBluetooth(val action: BluetoothAction, val deviceId: String? = null)
    private var pendingBluetooth: PendingBluetooth? = null
    private var awaitingBluetoothResult = false
    private val preservePendingDismissals = mutableSetOf<Dialog>()
    private lateinit var power: ImageButton
    private lateinit var connectionName: TextView
    private lateinit var connectionStatus: TextView
    private lateinit var chooseDevice: Button
    private lateinit var preview: AmbientPreview
    private lateinit var previewCaption: TextView
    private lateinit var wheel: ColorWheel
    private lateinit var brightnessValue: TextView
    private lateinit var brightness: SeekBar
    private lateinit var sceneName: TextView
    private lateinit var effects: Spinner
    private lateinit var speedBox: LinearLayout
    private lateinit var speedValue: TextView
    private lateinit var speed: SeekBar
    private lateinit var musicCaption: TextView
    private lateinit var resume: Button
    private lateinit var error: TextView
    private val sceneButtons = mutableListOf<Button>()
    private var selectedScene = 0
    private var devices = emptyList<LightDevice>()
    private var chooser: Dialog? = null
    private var savedRows: LinearLayout? = null
    private var nearbyRows: LinearLayout? = null
    private var discoveryCaption: TextView? = null
    private var chooserError: TextView? = null
    private var scanNearby: Button? = null
    private var optionConnectionStatus: TextView? = null
    private var optionConnectionHint: TextView? = null
    private var optionConnectionAction: Button? = null
    private var refreshingChooser = false
    private val scenes = listOf(
        Scene("Golden hour", "#ff8648", 78), Scene("Candlelight", "#ffb45c", 35),
        Scene("Rose tinted", "#ed85ad", 65), Scene("Lavender dusk", "#b29ae8", 48),
        Scene("Movie night", "#7998ff", 45), Scene("Clear sky", "#83bde8", 70),
        Scene("Ocean breeze", "#58c6c2", 55), Scene("Slow Sunday", "#a8d16d", 62)
    )
    private data class Scene(val name: String, val hex: String, val brightness: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSafeWindow(window)
        controller = LightController(this, this)
        buildScreen()
        render()
    }

    private fun configureSafeWindow(target: Window) {
        target.statusBarColor = background
        target.navigationBarColor = background
        if(Build.VERSION.SDK_INT>=30){
            target.decorView.systemUiVisibility = 0
            target.setDecorFitsSystemWindows(false)
        }else {
            target.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if(Build.VERSION.SDK_INT>=28){
            target.attributes=target.attributes.apply {
                layoutInDisplayCutoutMode=if(Build.VERSION.SDK_INT>=30)WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }
    private fun protectSafeViewport(view: ViewGroup) {
        view.clipToPadding=true
        view.clipChildren=true
        view.setOnApplyWindowInsetsListener { v, insets ->
            // This outer viewport owns the safe area; replace padding, never add it again.
            if(Build.VERSION.SDK_INT>=30){
                val safe=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(safe.left,safe.top,safe.right,safe.bottom)
                WindowInsets.CONSUMED
            }else {
                val cutout=if(Build.VERSION.SDK_INT>=28)insets.displayCutout else null
                v.setPadding(max(insets.systemWindowInsetLeft,cutout?.safeInsetLeft ?: 0),
                    max(insets.systemWindowInsetTop,cutout?.safeInsetTop ?: 0),
                    max(insets.systemWindowInsetRight,cutout?.safeInsetRight ?: 0),
                    max(insets.systemWindowInsetBottom,cutout?.safeInsetBottom ?: 0))
                if(Build.VERSION.SDK_INT>=28)insets.consumeDisplayCutout().consumeSystemWindowInsets() else insets.consumeSystemWindowInsets()
            }
        }
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@MainActivity.background)
            isFillViewport = true
        }
        protectSafeViewport(scroll)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(22), dp(24), dp(28))
        }
        scroll.addView(body, ViewGroup.LayoutParams(-1, -2))
        val header = row().apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = row().apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(label("Afterglow", 25f).apply { typeface = Typeface.create("sans-serif-medium", 0) })
        val version=packageManager.getPackageInfo(packageName,0).versionName ?: ""
        brand.addView(label("v$version", 11f, muted),LinearLayout.LayoutParams(-2,-2).apply { leftMargin=dp(8) })
        header.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        power = ImageButton(this).apply {
            setImageResource(R.drawable.ic_power)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            scaleType = ImageView.ScaleType.FIT_CENTER
            stateListAnimator = null
            setOnClickListener {
                clearError()
                val turnOn = !desired.on
                desired = desired.copy(on = turnOn, brightness = if (turnOn && desired.brightness == 0) 78 else desired.brightness)
                if (desired.on) applyAppearance() else {
                    stopCycle(false)
                    controller.submit(desired, Change.OFF)
                }
                render()
            }
        }
        header.addView(power,LinearLayout.LayoutParams(dp(52),dp(52)))
        body.addView(header, LinearLayout.LayoutParams(-1, -2))
        val connectionCard=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            isClickable=false
            isLongClickable=false
            background=rounded(panel)
            setPadding(dp(12),dp(4),dp(12),dp(12))
        }
        connectionName = label("", 16f).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            isClickable=false
            isLongClickable=false
            setPadding(0,dp(6),0,dp(6))
        }
        connectionCard.addView(connectionName,LinearLayout.LayoutParams(-1,-2))
        val statusRow=row().apply { gravity=Gravity.CENTER_VERTICAL }
        connectionStatus=label("",13f,muted).apply {
            minHeight=dp(48)
            accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        statusRow.addView(connectionStatus,LinearLayout.LayoutParams(0,-2,1f))
        chooseDevice = button("Device Option").apply {
            setPadding(dp(20),dp(10),dp(20),dp(10))
            isSingleLine = true
            setOnClickListener { showDeviceChooser() }
        }
        statusRow.addView(chooseDevice,LinearLayout.LayoutParams(-2,-2).apply { leftMargin=dp(12) })
        connectionCard.addView(statusRow,LinearLayout.LayoutParams(-1,-2))
        body.addView(connectionCard, space(-1, -2, 18))
        error = label("", 13f).apply {
            setTextColor(Color.rgb(255, 176, 170))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        body.addView(error, space(-1, -2, 8))
        preview = AmbientPreview(this)
        body.addView(preview, space(-1, dp(92), 12))
        previewCaption = label("", 11f, muted).apply {
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(previewCaption, space(-1, -2, 2))
        wheel = ColorWheel(this) { color ->
            clearError()
            selectedScene = -1
            desired = desired.copy(color = color, effect = Effect.SOLID)
            displayedColor = color
            applyAppearance()
            render()
        }
        body.addView(wheel, space(dp(228), dp(228), 17).apply { gravity = Gravity.CENTER_HORIZONTAL })
        val brightnessRow = row().apply { gravity = Gravity.CENTER_VERTICAL }
        brightnessRow.addView(label("Brightness"), LinearLayout.LayoutParams(0, -2, 1f))
        brightnessValue = label("78%")
        brightnessRow.addView(brightnessValue)
        body.addView(brightnessRow, space(-1, -2, 16))
        brightness = SeekBar(this).apply {
            max = 100
            progress = desired.brightness
            contentDescription = "Brightness"
            progressTintList = android.content.res.ColorStateList.valueOf(ink)
            thumbTintList = android.content.res.ColorStateList.valueOf(ink)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view: SeekBar?, value: Int, user: Boolean) {
                    if (!user) return
                    val wasOn = desired.on
                    desired = desired.copy(brightness = value, on = value > 0)
                    selectedScene = -1
                    brightnessValue.text = "$value%"
                    preview.update(displayedColor, value, desired.on)
                    renderSceneSelection()
                    clearError()
                    // Includes touch, keyboard, and accessibility adjustments; transport coalesces.
                    when {
                        value == 0 -> {
                            stopCycle(false)
                            controller.submit(desired, Change.OFF)
                        }
                        !wasOn -> applyAppearance()
                        else -> controller.submit(desired, Change.BRIGHTNESS)
                    }
                    render()
                }
                override fun onStartTrackingTouch(view: SeekBar?) {}
                override fun onStopTrackingTouch(view: SeekBar?) {}
            })
        }
        body.addView(brightness, space(-1, dp(44), 0))
        body.addView(label("Scenes"), space(-1, -2, 11))
        val grid = GridLayout(this).apply { columnCount = 4; rowCount = 2 }
        scenes.forEachIndexed { index, scene ->
            val swatch = button("").apply {
                minimumWidth = 0
                minWidth = 0
                setPadding(0, 0, 0, 0)
                contentDescription = "${scene.name}, brightness ${scene.brightness} percent"
                setOnClickListener {
                    clearError()
                    selectedScene = index
                    desired = desired.copy(color = Color.parseColor(scene.hex), brightness = scene.brightness, effect = Effect.SOLID)
                    displayedColor = desired.color
                    applyAppearance()
                    render()
                }
            }
            sceneButtons.add(swatch)
            grid.addView(swatch, GridLayout.LayoutParams(
                GridLayout.spec(index / 4), GridLayout.spec(index % 4, 1f)
            ).apply {
                width = 0
                height = dp(49)
                setMargins(dp(4), dp(5), dp(4), dp(5))
            })
        }
        body.addView(grid, space(-1, -2, 7))
        sceneName = label("", 12f, muted).apply {
            gravity = Gravity.CENTER
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        body.addView(sceneName, space(-1, dp(25), 2))
        body.addView(label("Effect"), space(-1, -2, 14))
        effects = Spinner(this).apply {
            contentDescription = "Effect"
            background = rounded(panel)
            adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, Effect.values().map { it.label }) {
                override fun isEnabled(position: Int): Boolean = effectAvailable(Effect.values()[position])
                override fun areAllItemsEnabled() = false
                override fun getView(position: Int, recycled: View?, parent: ViewGroup): View =
                    (super.getView(position, recycled, parent) as TextView).apply {
                        setTextColor(ink); textSize = 14f; setPadding(dp(13), dp(10), dp(28), dp(10))
                    }
                override fun getDropDownView(position: Int, recycled: View?, parent: ViewGroup): View =
                    (super.getDropDownView(position, recycled, parent) as TextView).apply {
                        setBackgroundColor(panel); setTextColor(if(isEnabled(position))ink else muted); alpha=if(isEnabled(position))1f else 0.45f
                        isEnabled=isEnabled(position); textSize = 14f; minHeight = dp(48)
                        setPadding(dp(16), dp(12), dp(16), dp(12))
                    }
            }
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val effect = Effect.values()[position]
                    if (binding || effect == desired.effect) return
                    if(!effectAvailable(effect)){render();return}
                    clearError()
                    desired = desired.copy(effect = effect)
                    applyAppearance()
                    render()
                }
            }
        }
        body.addView(effects, space(-1, dp(50), 9))
        speedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val speedRow = row()
        speedRow.addView(label("Speed"), LinearLayout.LayoutParams(0, -2, 1f))
        speedValue = label("50")
        speedRow.addView(speedValue)
        speedBox.addView(speedRow, space(-1, -2, 15))
        speed = SeekBar(this).apply {
            min = 1; max = 100; progress = desired.speed
            contentDescription = "Effect speed"
            progressTintList = android.content.res.ColorStateList.valueOf(ink)
            thumbTintList = android.content.res.ColorStateList.valueOf(ink)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(view: SeekBar?, value: Int, user: Boolean) {
                    if (user) {
                        desired = desired.copy(speed = value)
                        speedValue.text = value.toString()
                        clearError()
                        controller.submit(desired, Change.SPEED)
                    }
                }
                override fun onStartTrackingTouch(view: SeekBar?) {}
                override fun onStopTrackingTouch(view: SeekBar?) {}
            })
        }
        speedBox.addView(speed, space(-1, dp(44), 0))
        body.addView(speedBox)
        musicCaption = label("Uses the light controller’s microphone.", 12f, muted)
        body.addView(musicCaption, space(-1, -2, 10))
        resume = button("Resume color wheel").apply {
            setOnClickListener { clearError(); applyAppearance(); render() }
        }
        body.addView(resume, space(-1, dp(48), 12))
        setContentView(scroll)
        scroll.requestApplyInsets()
    }

    /** Only called by an explicit lighting control, never connection/lifecycle callbacks. */
    private fun applyAppearance() {
        stopCycle(false)
        displayedColor = desired.color
        val accepted = controller.submit(desired, Change.APPEARANCE)
        if (desired.on && desired.effect == Effect.FULL_WHEEL && foreground) {
            val hsv = FloatArray(3)
            Color.colorToHSV(desired.color, hsv)
            cycleHue = hsv[0]
            hardwareCycle = accepted
            cycleRunning = true
            ui.postDelayed(frame, 60L)
        }
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!foreground || !cycleRunning || destroyed) return
            // Increasing hue follows the wheel clockwise. Speed changes cadence, not packet rate.
            cycleHue = (cycleHue + 0.25f + desired.speed * 0.055f) % 360f
            displayedColor = Color.HSVToColor(floatArrayOf(cycleHue, 1f, 1f))
            preview.update(displayedColor, desired.brightness, desired.on)
            wheel.setColor(displayedColor)
            if (hardwareCycle && !controller.submit(desired.copy(color = displayedColor), Change.FRAME)) {
                hardwareCycle = false
                renderCaption()
            }
            ui.postDelayed(this, 60L)
        }
    }

    private fun stopCycle(paused: Boolean) {
        val wasRunning = cycleRunning
        ui.removeCallbacks(frame)
        cycleRunning = false
        hardwareCycle = false
        cyclePaused = paused && (wasRunning || (desired.on && desired.effect == Effect.FULL_WHEEL))
    }

    private fun effectAvailable(effect:Effect):Boolean = controller.currentCapabilities()?.supports(desired.copy(on=true,brightness=maxOf(1,desired.brightness),effect=effect),Change.APPEARANCE) ?: true

    private fun render() {
        if (!::power.isInitialized) return
        binding = true
        power.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (desired.on) ink else panel)
            setStroke(dp(1), if (desired.on) ink else Color.rgb(61,55,71))
        }
        power.imageTintList = android.content.res.ColorStateList.valueOf(if (desired.on) background else muted)
        power.contentDescription = if (desired.on) "Power requested on. Turn off" else "Power requested off. Turn on"
        power.isSelected = desired.on
        val currentName = controller.currentDeviceName()?.ifBlank { null } ?: "Light"
        connectionName.text = when {
            ready -> currentName
            busy || controller.hasPendingConnection() -> "Connecting"
            else -> "Not connected"
        }
        connectionStatus.text=when {
            busy -> status
            ready -> "Connected"
            else -> status
        }
        connectionName.contentDescription=connectionName.text.toString()+". "+connectionStatus.text
        brightness.progress = desired.brightness
        brightnessValue.text = "${desired.brightness}%"
        (effects.adapter as? BaseAdapter)?.notifyDataSetChanged()
        effects.setSelection(desired.effect.ordinal)
        speed.progress = desired.speed
        speedValue.text = desired.speed.toString()
        speedBox.visibility = if (desired.effect != Effect.SOLID && !desired.effect.isMusic) View.VISIBLE else View.GONE
        musicCaption.visibility = if (desired.effect.isMusic && controller.currentCapabilities()?.controllerMicrophone != false) View.VISIBLE else View.GONE
        resume.visibility = if (cyclePaused && desired.on && desired.effect == Effect.FULL_WHEEL) View.VISIBLE else View.GONE
        preview.update(displayedColor, desired.brightness, desired.on)
        wheel.setColor(displayedColor)
        renderCaption()
        renderSceneSelection()
        binding = false
    }

    private fun renderCaption() {
        previewCaption.text = when {
            !desired.on -> "Power requested off"
            cyclePaused -> "Color wheel paused · tap Resume"
            cycleRunning && !hardwareCycle -> "Color wheel preview · tap Resume to send after connecting"
            !ready -> "Light preview · connect to control your light"
            else -> "Ambient preview"
        }
        // A disconnected sample stays a sample after connection. Resume is a new explicit intent.
        if (::resume.isInitialized && cycleRunning && !hardwareCycle && ready) resume.visibility = View.VISIBLE
    }

    private fun renderSceneSelection() {
        sceneButtons.forEachIndexed { i, b ->
            b.background = rounded(Color.parseColor(scenes[i].hex), if (selectedScene == i) ink else Color.rgb(61, 55, 71), if (selectedScene == i) 3 else 1)
            b.isSelected = selectedScene == i
            b.contentDescription = "${scenes[i].name}, brightness ${scenes[i].brightness} percent${if (selectedScene == i) ", selected" else ""}"
        }
        sceneName.text = if (selectedScene >= 0) scenes[selectedScene].name else "Custom color"
    }

    /** Captures the selected ID before permission/system UI can dismiss the chooser. */
    private fun beginBluetooth(request: PendingBluetooth) {
        clearError()
        pendingBluetooth = request
        val scanning = request.action == BluetoothAction.SCAN
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            if (scanning) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else if (scanning) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION) else emptyArray()
        if (permissions.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            awaitingBluetoothResult = true
            requestPermissions(permissions, PERMISSION_SCAN)
            return
        }
        try {
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            if (adapter == null) {
                pendingBluetooth = null
                showError("Bluetooth is unavailable on this phone.")
                return
            }
            if (!adapter.isEnabled) {
                awaitingBluetoothResult = true
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH)
                return
            }
            pendingBluetooth = null
            when (request.action) {
                BluetoothAction.SCAN -> {
                    if (chooser?.isShowing != true) showDeviceChooser()
                    controller.scan()
                }
                BluetoothAction.CONNECT_SAVED -> {
                    controller.connectSaved(request.deviceId)
                    chooser?.dismiss()
                }
                BluetoothAction.CONNECT_NEARBY -> {
                    request.deviceId?.let { controller.connect(it) }
                    chooser?.dismiss()
                }
            }
        } catch (_: SecurityException) {
            pendingBluetooth = null
            showError("Bluetooth access is required for this action.")
        }
    }

    /** Opening this dialog is read-only: discovery starts only from Scan nearby. */
    private fun showDeviceChooser() {
        if (chooser?.isShowing == true) return
        val dialog=Dialog(this,android.R.style.Theme_Material_NoActionBar)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(false)
        val page=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
        }
        protectSafeViewport(page)
        val toolbar=row().apply {
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(8),dp(18),dp(8))
            setBackgroundColor(this@MainActivity.background)
        }
        toolbar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_arrow_back)
            imageTintList=android.content.res.ColorStateList.valueOf(ink)
            background=null
            setPadding(dp(12),dp(12),dp(12),dp(12))
            contentDescription="Back to light controls"
            setOnClickListener{dialog.dismiss()}
        },LinearLayout.LayoutParams(dp(48),dp(48)))
        toolbar.addView(label("Device Option",22f).apply{typeface=Typeface.create("sans-serif-medium",0)},
            LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(10)})
        page.addView(toolbar,LinearLayout.LayoutParams(-1,-2))
        devices = emptyList()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val connectionPanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            background=rounded(panel,Color.rgb(85,76,99))
            setPadding(dp(16),dp(16),dp(16),dp(16))
        }
        connectionPanel.addView(label("Connection",12f,muted).apply {
            typeface=Typeface.create("sans-serif-medium",0)
        })
        optionConnectionStatus = label("", 18f).apply {
            typeface=Typeface.create("sans-serif-medium",0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        connectionPanel.addView(optionConnectionStatus, space(-1, -2, 8))
        optionConnectionHint=label("Select a device below",12f,muted)
        connectionPanel.addView(optionConnectionHint,space(-1,-2,8))
        optionConnectionAction = button("").apply {
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setOnClickListener { disconnectLight() }
        }
        connectionPanel.addView(optionConnectionAction, space(-1, -2, 14))
        content.addView(connectionPanel,space(-1,-2,0))
        content.addView(label("Saved devices", 16f), space(-1, -2, 20))
        controller.savedDevicesIssue()?.let { issue ->
            content.addView(label(issue, 12f, Color.rgb(255, 176, 170)), space(-1, -2, 8))
        }
        chooserError = label("", 12f, Color.rgb(255, 176, 170)).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(chooserError, space(-1, -2, 8))
        savedRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(savedRows, space(-1, -2, 8))
        content.addView(label("Nearby devices", 16f), space(-1, -2, 20))
        scanNearby = button("Scan nearby").apply {
            setOnClickListener { beginBluetooth(PendingBluetooth(BluetoothAction.SCAN)) }
        }
        content.addView(scanNearby, space(-1, -2, 10))
        discoveryCaption = label("", 12f, muted).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        content.addView(discoveryCaption, space(-1, -2, 8))
        nearbyRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(nearbyRows, space(-1, -2, 4))
        val scroll = ScrollView(this).apply {
            setBackgroundColor(this@MainActivity.background)
            clipToPadding=true;clipChildren=true
            addView(content)
        }
        page.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        dialog.setContentView(page)
        dialog.window?.let{target->
            configureSafeWindow(target)
            target.setBackgroundDrawable(ColorDrawable(background))
            target.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            target.attributes=target.attributes.apply{dimAmount=0f}
            if(Build.VERSION.SDK_INT>=31)target.setBackgroundBlurRadius(0)
        }
        chooser = dialog
        dialog.setOnDismissListener {
            // Closing, Back, and outside-tap stop discovery only; the active light is preserved.
            val preservePending = preservePendingDismissals.remove(dialog)
            if (chooser === dialog) {
                if (!preservePending) pendingBluetooth = null
                controller.stopDiscovery()
                chooser = null
                savedRows = null
                nearbyRows = null
                discoveryCaption = null
                chooserError = null
                scanNearby = null
                optionConnectionStatus = null
                optionConnectionHint = null
                optionConnectionAction = null
            }
        }
        refreshChooser()
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT)
        page.requestApplyInsets()
    }

    private fun refreshChooser() {
        if (chooser == null || refreshingChooser) return
        refreshingChooser = true
        try {
            val pending = controller.hasPendingConnection()
            optionConnectionStatus?.text = when {
                ready -> "${controller.currentDeviceName()?.ifBlank { null } ?: "Light"} · Connected"
                else -> status
            }
            optionConnectionAction?.apply {
                text = if (pending || busy) "Cancel connection" else "Disconnect"
                visibility = if (ready || pending || busy) View.VISIBLE else View.GONE
            }
            optionConnectionHint?.visibility=if(ready||pending||busy)View.GONE else View.VISIBLE
            savedRows?.apply {
                removeAllViews()
                val records = controller.savedDevices()
                if (records.isEmpty()) addView(label("No saved devices yet.", 13f, muted))
                records.forEach { saved ->
                    val detail=if(controller.isConnectedDevice(saved.id))"Connected" else if(saved.authenticated)"Saved · reconnect to verify" else DeviceCatalog.discoveryLabel(saved.advertisedName)
                    val card = deviceCard(saved.alias,detail)
                    val actions = row()
                    actions.addView(button("Connect").apply {
                        contentDescription = "Connect to ${saved.alias}"
                        setOnClickListener {
                            val selectedId = saved.id
                            beginBluetooth(PendingBluetooth(BluetoothAction.CONNECT_SAVED, selectedId))
                        }
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    actions.addView(button("⋮").apply {
                        minWidth = dp(48); minimumWidth = dp(48)
                        contentDescription = "Manage ${saved.alias}"
                        setOnClickListener { anchor ->
                            PopupMenu(this@MainActivity, anchor).apply {
                                menu.add("Rename").setOnMenuItemClickListener { renameDevice(saved); true }
                                menu.add("Forget").setOnMenuItemClickListener { confirmForget(saved); true }
                                show()
                            }
                        }
                    }, LinearLayout.LayoutParams(dp(52), -2).apply { leftMargin = dp(8) })
                    card.addView(actions, space(-1, -2, 8))
                    addView(card, space(-1, -2, 8))
                }
            }
            nearbyRows?.apply {
                removeAllViews()
                devices.forEach { device ->
                    val name = device.name.ifBlank { "Unnamed device" }
                    val savedRecord = controller.savedDevices().find { it.id == device.id }
                    val saved = savedRecord != null
                    val detail = when {
                        controller.isConnectedDevice(device.id) -> "Connected"
                        savedRecord?.authenticated == true -> "Saved · reconnect to verify"
                        else -> DeviceCatalog.discoveryLabel(device.name)
                    }
                    val card = deviceCard(name, detail)
                    val actions = row()
                    actions.addView(button("Connect").apply {
                        contentDescription = "Connect to $name"
                        setOnClickListener {
                            val selectedId = device.id
                            beginBluetooth(PendingBluetooth(BluetoothAction.CONNECT_NEARBY, selectedId))
                        }
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    actions.addView(button(if (saved) "Saved" else "Save").apply {
                        contentDescription = if (saved) "$name is saved" else "Save $name on this phone"
                        isEnabled = !saved
                        setOnClickListener {
                            // A local-memory action: never connects, disconnects, or sends lighting.
                            if (controller.saveDevice(device)) { refreshChooser(); render() }
                        }
                    }, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(8) })
                    card.addView(actions, space(-1, -2, 8))
                    addView(card, space(-1, -2, 8))
                }
            }
            discoveryCaption?.text = when {
                discovering -> "Searching nearby…"
                devices.isNotEmpty() -> "Nearby names are advertised by devices; compatibility is not verified."
                else -> "Tap Scan nearby to find devices."
            }
            scanNearby?.isEnabled = !discovering
        } finally { refreshingChooser = false }
    }

    private fun deviceCard(name: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(panel)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        addView(label(name, 15f))
        if(detail.isNotBlank())addView(label(detail, 12f, muted), space(-1, -2, 4))
    }

    private fun renameDevice(device: SavedDevice) {
        val input = EditText(this).apply {
            setText(device.alias)
            setSelectAllOnFocus(true)
            setTextColor(ink)
            isSingleLine = true
            contentDescription = "Device name"
        }
        val inlineError = label("", 12f, Color.rgb(255, 176, 170)).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(this@MainActivity.background)
            setPadding(dp(20), dp(8), dp(20), dp(12))
            addView(input, LinearLayout.LayoutParams(-1, -2))
            addView(inlineError, space(-1, -2, 6))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Rename device").setView(content)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rawAlias = input.text.toString()
                val alias = rawAlias.trim()
                val validation = when {
                    rawAlias.any { Character.isISOControl(it) } -> "Use a name without control characters."
                    alias.isEmpty() -> "Enter a name."
                    alias.length > 40 -> "Use 40 characters or fewer."
                    else -> null
                }
                if (validation != null) {
                    inlineError.text = validation
                    inlineError.visibility = View.VISIBLE
                } else if (controller.renameSavedDevice(device.id, alias)) {
                    refreshChooser()
                    render()
                    dialog.dismiss()
                } else {
                    inlineError.text = "Could not rename this saved device."
                    inlineError.visibility = View.VISIBLE
                }
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun confirmForget(device: SavedDevice) {
        AlertDialog.Builder(this).setTitle("Forget ${device.alias}?")
            .setMessage(if (controller.isSelectedDevice(device.id)) "Remove and disconnect this saved device?"
                else "Remove this saved device from this phone?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Forget") { _, _ ->
                val wasSelected = controller.isSelectedDevice(device.id)
                if (controller.forgetSavedDevice(device.id)) {
                    if (wasSelected) stopCycle(true)
                    refreshChooser()
                    render()
                }
            }.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_SCAN) return
        awaitingBluetoothResult = false
        val request = pendingBluetooth ?: return
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) beginBluetooth(request)
        else {
            pendingBluetooth = null
            showError(if (request.action == BluetoothAction.SCAN && Build.VERSION.SDK_INT < 31)
                "Android requires location permission to scan nearby devices." else "Allow Bluetooth access to continue.")
        }
    }

    @Deprecated("System Bluetooth enable result for native Activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ENABLE_BLUETOOTH) {
            awaitingBluetoothResult = false
            val request = pendingBluetooth ?: return
            if (resultCode == RESULT_OK) beginBluetooth(request) else {
                pendingBluetooth = null
                showError("Bluetooth is off. Turn it on when you want to connect.")
            }
        }
    }

    private fun disconnectLight() {
        pendingBluetooth = null
        stopCycle(true)
        controller.cancelLighting()
        controller.disconnect()
        refreshChooser()
        render()
    }

    override fun onStatus(text: String, ready: Boolean, busy: Boolean) = onUi {
        val lostReadyLink = this.ready && !ready
        this.status = text
        this.ready = ready
        this.busy = busy
        if(ready && desired.effect.isMusic && controller.currentCapabilities()?.controllerMicrophone==false)
            desired=desired.copy(effect=Effect.SOLID)
        if (lostReadyLink) stopCycle(true) else if (!ready) hardwareCycle = false
        refreshChooser()
        render()
    }

    override fun onDevices(devices: List<LightDevice>) = onUi {
        this.devices = devices.toList()
        refreshChooser()
    }
    override fun onDiscovery(active: Boolean) = onUi {
        discovering = active
        refreshChooser()
        render()
    }

    override fun onError(message: String) = onUi { showError(message) }
    private fun onUi(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!destroyed) action()
        } else ui.post { if (!destroyed) action() }
    }
    private fun clearError() {
        error.visibility = View.GONE
        chooserError?.visibility = View.GONE
    }
    private fun showError(message: String) {
        if (::error.isInitialized) { error.text = message; error.visibility = View.VISIBLE }
        chooserError?.apply { text = message; visibility = View.VISIBLE }
    }

    override fun onStart() {
        super.onStart()
        foreground = true
        controller.setForeground(true)
        render()
    }
    override fun onStop() {
        foreground = false
        controller.setForeground(false)
        stopCycle(true)
        controller.stopDiscovery()
        // System permission/enable UI may stop this Activity; its explicit intent may continue.
        chooser?.let { dialog ->
            if (awaitingBluetoothResult && pendingBluetooth != null) preservePendingDismissals.add(dialog)
            dialog.dismiss()
        }
        controller.cancelLighting()
        if (busy) controller.disconnect()
        render()
        super.onStop()
    }
    override fun onDestroy() {
        destroyed = true
        ui.removeCallbacksAndMessages(null)
        chooser?.dismiss()
        controller.disconnect()
        controller.close()
        super.onDestroy()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).roundToInt()
    private fun label(text: String, size: Float = 14f, color: Int = ink) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    }
    private fun button(text: String) = Button(this).apply {
        this.text = text; isAllCaps = false; setTextColor(ink); textSize = 14f
        minHeight = dp(48); minimumHeight = dp(48); background = rounded(panel)
        stateListAnimator = null
    }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun rounded(color: Int, stroke: Int = Color.rgb(61, 55, 71), width: Int = 1) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = dp(12).toFloat(); setColor(color); setStroke(dp(width), stroke)
    }
    private fun space(width: Int, height: Int, top: Int) = LinearLayout.LayoutParams(width, height).apply { topMargin = dp(top) }
    companion object { private const val PERMISSION_SCAN = 41; private const val ENABLE_BLUETOOTH = 42 }
}

/** Hue runs clockwise from red at 12 o'clock; radius controls saturation. */
private class ColorWheel(context: Context, private val commit: (Int) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hsv = floatArrayOf(24f, 0.72f, 1f)
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var tracking = false
    private val density = resources.displayMetrics.density
    init { isFocusable = true; isClickable = true; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    fun setColor(color: Int) { if (!tracking) { Color.colorToHSV(color, hsv); hsv[2] = 1f; invalidate() } }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cx = w / 2f; cy = h / 2f; radius = min(w, h) / 2f - 12f * density
    }
    override fun onDraw(canvas: Canvas) {
        val colors = intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED)
        paint.shader = SweepGradient(cx, cy, colors, null).apply { setLocalMatrix(Matrix().apply { setRotate(-90f, cx, cy) }) }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = RadialGradient(cx, cy, radius, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        val angle = Math.toRadians(hsv[0].toDouble() - 90)
        val x = cx + cos(angle).toFloat() * radius * hsv[1]
        val y = cy + sin(angle).toFloat() * radius * hsv[1]
        paint.color = Color.argb(100, 0, 0, 0)
        canvas.drawCircle(x, y + density, 12f * density, paint)
        paint.color = Color.HSVToColor(hsv)
        canvas.drawCircle(x, y, 9f * density, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f * density; paint.color = Color.WHITE
        canvas.drawCircle(x, y, 9f * density, paint)
        if (isFocused) { paint.strokeWidth = density; paint.color = Color.WHITE; canvas.drawCircle(cx, cy, radius + 7f * density, paint) }
        paint.style = Paint.Style.FILL
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { tracking = true; parent.requestDisallowInterceptTouchEvent(true); requestFocus(); pick(event.x, event.y); return true }
            MotionEvent.ACTION_MOVE -> { if (tracking) pick(event.x, event.y); return true }
            MotionEvent.ACTION_UP -> { if (tracking) { pick(event.x, event.y); tracking = false; performClick() }; parent.requestDisallowInterceptTouchEvent(false); return true }
            MotionEvent.ACTION_CANCEL -> { tracking = false; parent.requestDisallowInterceptTouchEvent(false); return true }
        }
        return super.onTouchEvent(event)
    }
    private fun pick(x: Float, y: Float) {
        hsv[0] = ((Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())) + 450) % 360).toFloat()
        hsv[1] = (hypot(x - cx, y - cy) / radius).coerceIn(0f, 1f)
        hsv[2] = 1f
        invalidate()
    }
    override fun performClick(): Boolean { super.performClick(); commit(Color.HSVToColor(hsv)); return true }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> hsv[0] = (hsv[0] + 355f) % 360f
            KeyEvent.KEYCODE_DPAD_RIGHT -> hsv[0] = (hsv[0] + 5f) % 360f
            KeyEvent.KEYCODE_DPAD_UP -> hsv[1] = (hsv[1] + 0.05f).coerceAtMost(1f)
            KeyEvent.KEYCODE_DPAD_DOWN -> hsv[1] = (hsv[1] - 0.05f).coerceAtLeast(0f)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> return performClick()
            else -> return super.onKeyDown(keyCode, event)
        }
        invalidate(); commit(Color.HSVToColor(hsv)); announceForAccessibility(description()); return true
    }
    private fun description() = "Color wheel. Hue ${hsv[0].roundToInt()} degrees, saturation ${(hsv[1] * 100).roundToInt()} percent. Left and right change hue; up and down change saturation."
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.SeekBar"
        info.contentDescription = description()
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 360f, hsv[0])
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(MORE_SATURATION, "Increase saturation"))
        info.addAction(AccessibilityNodeInfo.AccessibilityAction(LESS_SATURATION, "Decrease saturation"))
    }
    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> hsv[0] = (hsv[0] + 5f) % 360f
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> hsv[0] = (hsv[0] + 355f) % 360f
            MORE_SATURATION -> hsv[1] = (hsv[1] + 0.05f).coerceAtMost(1f)
            LESS_SATURATION -> hsv[1] = (hsv[1] - 0.05f).coerceAtLeast(0f)
            else -> return super.performAccessibilityAction(action, arguments)
        }
        invalidate(); commit(Color.HSVToColor(hsv)); announceForAccessibility(description()); return true
    }
    companion object { private const val MORE_SATURATION = 0x00a01101; private const val LESS_SATURATION = 0x00a01102 }
}

private class AmbientPreview(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var color = Color.rgb(255, 134, 72)
    private var brightness = 78
    private var on = true
    init { contentDescription = "Illustrative TV ambient light preview"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    fun update(color: Int, brightness: Int, on: Boolean) { this.color = color; this.brightness = brightness; this.on = on; invalidate() }
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val alpha = if (on) (brightness * 1.8f).roundToInt().coerceIn(0, 200) else 0
        paint.shader = RadialGradient(w / 2, h / 2, w * .44f, Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, w, h, 18f, 18f, paint)
        paint.shader = null
        val screenW = min(w * .48f, h * 1.75f)
        val box = RectF((w - screenW) / 2f, h * .13f, (w + screenW) / 2f, h * .83f)
        paint.color = Color.rgb(9, 10, 14)
        canvas.drawRoundRect(box, 5f, 5f, paint)
        box.inset(5f * resources.displayMetrics.density, 4f * resources.displayMetrics.density)
        paint.shader = LinearGradient(box.left, box.top, box.right, box.bottom, intArrayOf(Color.rgb(26, 31, 59), Color.rgb(126, 81, 112), Color.rgb(226, 154, 125)), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(box, 2f, 2f, paint)
        paint.shader = null
        paint.color = Color.argb(160, 29, 30, 48)
        val hills = Path().apply {
            moveTo(box.left, box.bottom); lineTo(box.left, box.top + box.height() * .7f)
            cubicTo(box.left + box.width() * .25f, box.top + box.height() * .28f, box.left + box.width() * .45f, box.bottom, box.right, box.top + box.height() * .65f)
            lineTo(box.right, box.bottom); close()
        }
        canvas.drawPath(hills, paint)
        paint.color = Color.rgb(12, 13, 18)
        canvas.drawRoundRect(w * .485f, h * .82f, w * .515f, h * .92f, 2f, 2f, paint)
        canvas.drawRoundRect(w * .43f, h * .91f, w * .57f, h * .95f, 3f, 3f, paint)
    }
}
