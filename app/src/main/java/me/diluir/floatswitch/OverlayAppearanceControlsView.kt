package me.diluir.floatswitch

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class OverlayAppearanceControlsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val appearanceStore = OverlayAppearanceStore(context)
    private val autoStartStateStore = AutoStartStateStore(context)
    private val spacingGroup = MaterialButtonToggleGroup(context)
    private val sizePercentText = TextView(context)
    private val decreaseSizeButton = createSizeStepButton(
        R.string.overlay_size_decrease,
        R.string.overlay_size_decrease_description,
    )
    private val increaseSizeButton = createSizeStepButton(
        R.string.overlay_size_increase,
        R.string.overlay_size_increase_description,
    )
    private var currentSizePercent = OverlayAppearanceRules.DEFAULT_SIZE_PERCENT
    private var updatingControls = false

    init {
        orientation = VERTICAL
        addView(createSizeControls())
        addView(createLabel(context.getString(R.string.overlay_spacing_heading)).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dimension(R.dimen.overlay_appearance_section_spacing)
                bottomMargin = dimension(R.dimen.overlay_appearance_label_spacing)
            }
        })
        configureGroup(spacingGroup)
        addView(spacingGroup)
        addSpacingButtons()
        restoreSelection()
        installListeners()
    }

    private fun createSizeControls() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            setText(R.string.overlay_size_heading)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
        }, LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ))
        addView(decreaseSizeButton)
        addView(sizePercentText.apply {
            gravity = Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        }, LayoutParams(
            dimension(R.dimen.overlay_appearance_percent_width),
            dimension(R.dimen.overlay_appearance_control_size),
        ))
        addView(increaseSizeButton)
    }

    private fun createLabel(text: String) = TextView(context).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dimension(R.dimen.overlay_appearance_label_spacing)
        }
    }

    private fun configureGroup(group: MaterialButtonToggleGroup) {
        group.isSingleSelection = true
        group.isSelectionRequired = true
        group.orientation = HORIZONTAL
        group.gravity = Gravity.CENTER
    }

    private fun addSpacingButtons() {
        spacingGroup.addView(createChoiceButton(R.string.overlay_spacing_compact, ID_SPACING_COMPACT))
        spacingGroup.addView(createChoiceButton(R.string.overlay_spacing_normal, ID_SPACING_NORMAL))
        spacingGroup.addView(createChoiceButton(R.string.overlay_spacing_wide, ID_SPACING_WIDE))
    }

    private fun createChoiceButton(textResource: Int, buttonId: Int) = MaterialButton(
        context,
        null,
        com.google.android.material.R.attr.materialButtonOutlinedStyle,
    ).apply {
        id = buttonId
        setText(textResource)
        isCheckable = true
        isAllCaps = false
        minWidth = 0
        layoutParams = LayoutParams(
            0,
            dimension(R.dimen.overlay_appearance_control_size),
            1f,
        )
    }

    private fun createSizeStepButton(textResource: Int, descriptionResource: Int) = MaterialButton(
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

    private fun restoreSelection() {
        val appearance = appearanceStore.load()
        updatingControls = true
        currentSizePercent = appearance.buttonSizePercent
        updateSizeControls()
        spacingGroup.check(
            when (appearance.buttonSpacing) {
                OverlayButtonSpacing.COMPACT -> ID_SPACING_COMPACT
                OverlayButtonSpacing.NORMAL -> ID_SPACING_NORMAL
                OverlayButtonSpacing.WIDE -> ID_SPACING_WIDE
            },
        )
        updatingControls = false
    }

    private fun installListeners() {
        decreaseSizeButton.setOnClickListener {
            changeSizeBy(-1)
        }
        increaseSizeButton.setOnClickListener {
            changeSizeBy(1)
        }
        spacingGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updatingControls) return@addOnButtonCheckedListener
            val spacing = when (checkedId) {
                ID_SPACING_COMPACT -> OverlayButtonSpacing.COMPACT
                ID_SPACING_WIDE -> OverlayButtonSpacing.WIDE
                else -> OverlayButtonSpacing.NORMAL
            }
            appearanceStore.setButtonSpacing(spacing)
            refreshOverlayIfActive()
        }
    }

    private fun changeSizeBy(stepCount: Int) {
        val updatedSize = OverlayAppearanceRules.changeSize(currentSizePercent, stepCount)
        if (updatedSize == currentSizePercent) return
        currentSizePercent = updatedSize
        appearanceStore.setButtonSizePercent(updatedSize)
        updateSizeControls()
        refreshOverlayIfActive()
    }

    private fun updateSizeControls() {
        sizePercentText.text = context.getString(
            R.string.overlay_size_percent,
            currentSizePercent,
        )
        sizePercentText.contentDescription = context.getString(
            R.string.overlay_size_percent_description,
            currentSizePercent,
        )
        decreaseSizeButton.isEnabled = currentSizePercent > OverlayAppearanceRules.MIN_SIZE_PERCENT
        increaseSizeButton.isEnabled = currentSizePercent < OverlayAppearanceRules.MAX_SIZE_PERCENT
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
        private const val ID_SPACING_COMPACT = 0x10004
        private const val ID_SPACING_NORMAL = 0x10005
        private const val ID_SPACING_WIDE = 0x10006
    }
}
