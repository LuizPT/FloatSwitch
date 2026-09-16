package me.diluir.floatswitch

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class OverlayBackgroundControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val appearanceStore = OverlayAppearanceStore(context)
    private val autoStartStateStore = AutoStartStateStore(context)
    private val backgroundSwitch = SwitchCompat(context)
    private val opacityLabel = TextView(context)
    private val opacityPercentText = TextView(context)
    private val decreaseOpacityButton = createStepButton(
        R.string.overlay_size_decrease,
        R.string.overlay_background_opacity_decrease_description,
    )
    private val increaseOpacityButton = createStepButton(
        R.string.overlay_size_increase,
        R.string.overlay_background_opacity_increase_description,
    )
    private var currentOpacityPercent =
        OverlayAppearanceRules.DEFAULT_BACKGROUND_OPACITY_PERCENT
    private var updatingControls = false

    init {
        orientation = VERTICAL
        addView(createHeading())
        addView(createBackgroundSwitch())
        addView(createOpacityControls())
        restoreState()
        installListeners()
    }

    private fun createHeading() = TextView(context).apply {
        setText(R.string.overlay_background_heading)
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        setTextColor(ContextCompat.getColor(context, R.color.screen_text_primary))
    }

    private fun createBackgroundSwitch() = backgroundSwitch.apply {
        setText(R.string.overlay_background_show)
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        setTextColor(ContextCompat.getColor(context, R.color.screen_text_primary))
        minHeight = dimension(R.dimen.overlay_appearance_control_size)
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dimension(R.dimen.overlay_appearance_label_spacing)
        }
    }

    private fun createOpacityControls() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(opacityLabel.apply {
            setText(R.string.overlay_background_opacity)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
        }, LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ))
        addView(decreaseOpacityButton)
        addView(opacityPercentText.apply {
            gravity = Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        }, LayoutParams(
            dimension(R.dimen.overlay_appearance_percent_width),
            dimension(R.dimen.overlay_appearance_control_size),
        ))
        addView(increaseOpacityButton)
    }

    private fun createStepButton(textResource: Int, descriptionResource: Int) = MaterialButton(
        context,
        null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle,
    ).apply {
        setText(textResource)
        contentDescription = context.getString(descriptionResource)
        isAllCaps = false
        minWidth = 0
        insetTop = 0
        insetBottom = 0
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, 0)
        setTextColor(ContextCompat.getColor(context, R.color.screen_text_primary))
        layoutParams = LayoutParams(
            dimension(R.dimen.overlay_appearance_control_size),
            dimension(R.dimen.overlay_appearance_control_size),
        )
    }

    private fun restoreState() {
        val appearance = appearanceStore.load()
        updatingControls = true
        backgroundSwitch.isChecked = appearance.backgroundEnabled
        currentOpacityPercent = appearance.backgroundOpacityPercent
        updateControls()
        updatingControls = false
    }

    private fun installListeners() {
        backgroundSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingControls) return@setOnCheckedChangeListener
            appearanceStore.setBackgroundEnabled(isChecked)
            updateControls()
            refreshOverlayIfActive()
        }
        decreaseOpacityButton.setOnClickListener { changeOpacityBy(-1) }
        increaseOpacityButton.setOnClickListener { changeOpacityBy(1) }
    }

    private fun changeOpacityBy(stepCount: Int) {
        val updatedOpacity = OverlayAppearanceRules.changeBackgroundOpacity(
            currentOpacityPercent,
            stepCount,
        )
        if (updatedOpacity == currentOpacityPercent) return
        currentOpacityPercent = updatedOpacity
        appearanceStore.setBackgroundOpacityPercent(updatedOpacity)
        updateControls()
        refreshOverlayIfActive()
    }

    private fun updateControls() {
        val backgroundEnabled = backgroundSwitch.isChecked
        backgroundSwitch.contentDescription = context.getString(
            if (backgroundEnabled) {
                R.string.overlay_background_description_on
            } else {
                R.string.overlay_background_description_off
            },
        )
        opacityPercentText.text = context.getString(
            R.string.overlay_size_percent,
            currentOpacityPercent,
        )
        opacityPercentText.contentDescription = context.getString(
            R.string.overlay_background_opacity_description,
            currentOpacityPercent,
        )
        decreaseOpacityButton.isEnabled = backgroundEnabled &&
            currentOpacityPercent > OverlayAppearanceRules.MIN_BACKGROUND_OPACITY_PERCENT
        increaseOpacityButton.isEnabled = backgroundEnabled &&
            currentOpacityPercent < OverlayAppearanceRules.MAX_BACKGROUND_OPACITY_PERCENT
        val contentAlpha = if (backgroundEnabled) ENABLED_ALPHA else DISABLED_ALPHA
        opacityLabel.alpha = contentAlpha
        opacityPercentText.alpha = contentAlpha
        decreaseOpacityButton.alpha = if (decreaseOpacityButton.isEnabled) {
            ENABLED_ALPHA
        } else {
            DISABLED_ALPHA
        }
        increaseOpacityButton.alpha = if (increaseOpacityButton.isEnabled) {
            ENABLED_ALPHA
        } else {
            DISABLED_ALPHA
        }
    }

    private fun refreshOverlayIfActive() {
        if (
            !autoStartStateStore.isOverlayRequestedActive() ||
            !Settings.canDrawOverlays(context)
        ) {
            return
        }
        try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW),
            )
        } catch (_: RuntimeException) {
            // O serviço também verifica periodicamente alterações às preferências.
        }
    }

    private fun dimension(resource: Int): Int = resources.getDimensionPixelSize(resource)

    companion object {
        private const val ENABLED_ALPHA = 1f
        private const val DISABLED_ALPHA = 0.38f
    }
}
