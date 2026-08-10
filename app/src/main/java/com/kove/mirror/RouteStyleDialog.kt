package com.kove.mirror

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatSpinner

data class ColorOption(val name: String, val color: Int)

/**
 * Enhanced Route Settings Dialog for selecting line style, direction arrows, and distance markers using dropdowns.
 */
class RouteStyleDialog(
    context: Context,
    private val initialColor: Int = Color.RED,
    private val initialWidth: Float = 5f,
    private val initialShowArrows: Boolean = true,
    private val initialArrowColor: Int = initialColor,
    private val initialShowDistance: Boolean = true,
    private val initialDistanceIntervalKm: Int = 5,
    private val onStyleSelected: (
        color: Int,
        width: Float,
        showArrows: Boolean,
        arrowColor: Int,
        showDistance: Boolean,
        distanceIntervalKm: Int
    ) -> Unit
) : Dialog(context) {

    companion object {
        val PRESET_COLORS = intArrayOf(
            Color.parseColor("#FF1744"),
            Color.parseColor("#FF9100"),
            Color.parseColor("#FFEA00"),
            Color.parseColor("#00E676"),
            Color.parseColor("#2979FF"),
            Color.parseColor("#D500F9"),
            Color.parseColor("#FF4081"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#00E5FF"),
            Color.parseColor("#76FF03")
        )
    }

    private var selectedColor: Int = initialColor
    private var selectedWidth: Float = initialWidth
    private var showArrows: Boolean = initialShowArrows
    private var selectedArrowColor: Int = initialArrowColor
    private var showDistance: Boolean = initialShowDistance
    private var selectedDistanceIntervalKm: Int = initialDistanceIntervalKm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val colorOptions = listOf(
            ColorOption(context.getString(R.string.color_red), Color.parseColor("#FF1744")),
            ColorOption(context.getString(R.string.color_orange), Color.parseColor("#FF9100")),
            ColorOption(context.getString(R.string.color_yellow), Color.parseColor("#FFEA00")),
            ColorOption(context.getString(R.string.color_green), Color.parseColor("#00E676")),
            ColorOption(context.getString(R.string.color_blue), Color.parseColor("#2979FF")),
            ColorOption(context.getString(R.string.color_purple), Color.parseColor("#D500F9")),
            ColorOption(context.getString(R.string.color_pink), Color.parseColor("#FF4081")),
            ColorOption(context.getString(R.string.color_white), Color.parseColor("#FFFFFF")),
            ColorOption(context.getString(R.string.color_cyan), Color.parseColor("#00E5FF")),
            ColorOption(context.getString(R.string.color_lime), Color.parseColor("#76FF03"))
        )
        
        val rootView = ScrollView(context).apply {
            setBackgroundColor(Color.parseColor("#EE0F172A")) // Dark Slate Translucent
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootView.addView(container)
        setContentView(rootView)

        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Title
        val tvTitle = TextView(context).apply {
            text = context.getString(R.string.map_route_settings_title)
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(12))
        }
        container.addView(tvTitle)

        // 1. Line Color & Width
        val tvLineHeader = TextView(context).apply {
            text = context.getString(R.string.map_route_line_color)
            textSize = 13f
            setTextColor(Color.parseColor("#38BDF8"))
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }
        container.addView(tvLineHeader)

        val previewLine = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(initialWidth.toInt().coerceAtLeast(2))
            ).apply { topMargin = dpToPx(4); bottomMargin = dpToPx(8) }
            setBackgroundColor(selectedColor)
        }

        val spinnerRouteColor = AppCompatSpinner(context).apply {
            adapter = ColorSpinnerAdapter(context, colorOptions)
            val initIdx = colorOptions.indexOfFirst { it.color == selectedColor }.coerceAtLeast(0)
            setSelection(initIdx)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedColor = colorOptions[position].color
                    updatePreview(previewLine)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        container.addView(spinnerRouteColor)
        container.addView(previewLine)

        val seekWidth = SeekBar(context).apply {
            max = 13
            progress = (initialWidth - 2f).toInt().coerceIn(0, 13)
        }
        val tvWidthValue = TextView(context).apply {
            text = context.getString(R.string.map_route_line_width_format, initialWidth.toInt())
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(0, dpToPx(4), 0, 0)
        }
        container.addView(tvWidthValue)
        container.addView(seekWidth)

        seekWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                selectedWidth = (progress + 2).toFloat()
                tvWidthValue.text = context.getString(R.string.map_route_line_width_format, selectedWidth.toInt())
                updatePreview(previewLine)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Divider
        container.addView(createDivider())

        // 2. Direction Arrows
        val cbArrows = AppCompatCheckBox(context).apply {
            text = context.getString(R.string.map_route_show_arrows)
            isChecked = showArrows
            setTextColor(Color.WHITE)
        }
        container.addView(cbArrows)

        val tvArrowColorHeader = TextView(context).apply {
            text = context.getString(R.string.map_route_arrow_color)
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dpToPx(8), dpToPx(4), 0, dpToPx(4))
        }
        container.addView(tvArrowColorHeader)

        val spinnerArrowColor = AppCompatSpinner(context).apply {
            adapter = ColorSpinnerAdapter(context, colorOptions)
            val initIdx = colorOptions.indexOfFirst { it.color == selectedArrowColor }.coerceAtLeast(0)
            setSelection(initIdx)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedArrowColor = colorOptions[position].color
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        container.addView(spinnerArrowColor)

        cbArrows.setOnCheckedChangeListener { _, isChecked ->
            showArrows = isChecked
            tvArrowColorHeader.visibility = if (isChecked) View.VISIBLE else View.GONE
            spinnerArrowColor.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        tvArrowColorHeader.visibility = if (showArrows) View.VISIBLE else View.GONE
        spinnerArrowColor.visibility = if (showArrows) View.VISIBLE else View.GONE

        // Divider
        container.addView(createDivider())

        // 3. Distance Markers
        val cbDistance = AppCompatCheckBox(context).apply {
            text = context.getString(R.string.map_route_show_distance)
            isChecked = showDistance
            setTextColor(Color.WHITE)
        }
        container.addView(cbDistance)

        val tvIntervalHeader = TextView(context).apply {
            text = context.getString(R.string.map_route_distance_interval)
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dpToPx(8), dpToPx(4), 0, dpToPx(4))
        }
        container.addView(tvIntervalHeader)

        val rgInterval = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
        }

        val intervals = intArrayOf(1, 5, 10, 20, 50)
        for (interval in intervals) {
            val rb = RadioButton(context).apply {
                id = View.generateViewId()
                text = "$interval km"
                isChecked = (interval == selectedDistanceIntervalKm)
                setTextColor(Color.WHITE)
                textSize = 11f
                setOnClickListener { selectedDistanceIntervalKm = interval }
            }
            rgInterval.addView(rb)
        }
        container.addView(rgInterval)

        cbDistance.setOnCheckedChangeListener { _, isChecked ->
            showDistance = isChecked
            tvIntervalHeader.visibility = if (isChecked) View.VISIBLE else View.GONE
            rgInterval.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        tvIntervalHeader.visibility = if (showDistance) View.VISIBLE else View.GONE
        rgInterval.visibility = if (showDistance) View.VISIBLE else View.GONE

        // Buttons Row
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(16), 0, 0)
        }

        val btnCancel = TextView(context).apply {
            text = context.getString(R.string.map_btn_cancel)
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setPadding(dpToPx(16), dpToPx(10), dpToPx(16), dpToPx(10))
            setOnClickListener { dismiss() }
        }

        val btnApply = TextView(context).apply {
            text = context.getString(R.string.map_btn_apply)
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#2563EB"))
            setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10))
            setOnClickListener {
                onStyleSelected(
                    selectedColor,
                    selectedWidth,
                    showArrows,
                    selectedArrowColor,
                    showDistance,
                    selectedDistanceIntervalKm
                )
                dismiss()
            }
        }

        btnRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        btnRow.addView(btnApply)
        container.addView(btnRow)
    }

    private fun createDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(1)
            ).apply { topMargin = dpToPx(12); bottomMargin = dpToPx(12) }
            setBackgroundColor(Color.parseColor("#334155"))
        }
    }

    private fun updatePreview(previewLine: View) {
        previewLine.setBackgroundColor(selectedColor)
        val lp = previewLine.layoutParams
        lp.height = dpToPx(selectedWidth.toInt().coerceAtLeast(2))
        previewLine.layoutParams = lp
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    private class ColorSpinnerAdapter(
        context: Context,
        private val options: List<ColorOption>
    ) : ArrayAdapter<ColorOption>(context, 0, options) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createCustomView(position, convertView, parent, isDropdown = false)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createCustomView(position, convertView, parent, isDropdown = true)
        }

        private fun createCustomView(position: Int, convertView: View?, parent: ViewGroup, isDropdown: Boolean): View {
            val density = context.resources.displayMetrics.density
            val layout = (convertView as? LinearLayout) ?: LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = (8 * density).toInt()
                setPadding(p, p, p, p)
                setBackgroundColor(Color.parseColor(if (isDropdown) "#1E293B" else "#0F172A"))
            }

            layout.removeAllViews()
            val option = getItem(position) ?: return layout

            val circleView = View(context).apply {
                val size = (16 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = (10 * density).toInt()
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(option.color)
                    setStroke((1 * density).toInt(), Color.WHITE)
                }
            }

            val textView = TextView(context).apply {
                text = option.name
                setTextColor(Color.WHITE)
                textSize = 13f
            }

            layout.addView(circleView)
            layout.addView(textView)
            return layout
        }
    }
}
