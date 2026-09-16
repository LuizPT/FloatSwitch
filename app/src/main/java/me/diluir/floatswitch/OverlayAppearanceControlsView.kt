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
    private val sizeGroup = MaterialButtonToggleGroup(context)
    private val spacingGroup = MaterialButtonToggleGroup(context)
    private var updatingControls = false

    init {
        orientation = VERTICAL
        addView(createHeading(context.getString(R.string.overlay_appearance_heading)))
        addView(createDescription(context.getString(R.string.overlay_appearance_description)))
        addView(createLabel(context.getString(R.string.overlay_size_heading)))
        configureGroup(sizeGroup)
        addView(sizeGroup)
        addSizeButtons()
        addView(createLabel(context.getString(R.string.overlay_spacing_heading)).apply {
            layoutParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(12)
            }
        })
        configureGroup(spacingGroup)
        addView(spacingGroup)
        addSpacingButtons()
        restoreSelection()
        installListeners()
    }

    private fun createHeading(text: String) = TextView(context).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
    }

    private fun createDescription(text: String) = TextView(context).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dp(4)
            bottomMargin = dp(12)
        }
    }

    private fun createLabel(text: String) = TextView(context).apply {
        this.text = text
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = dp(6)
        }
    }

    private fun configureGroup(group: MaterialButtonToggleGroup) {
        group.isSingleSelection = true
        group.isSelectionRequired = true
        group.orientation = HORIZONTAL
        group.gravity = Gravity.CENTER
    }

    private fun addSizeButtons() {
        sizeGroup.addView(createChoiceButton(R.string.overlay_size_small, ID_SIZE_SMALL))
        sizeGroup.addView(createChoiceButton(R.string.overlay_size_medium, ID_SIZE_MEDIUM))
        sizeGroup.addView(createChoiceButton(R.string.overlay_size_large, ID_SIZE_LARGE))
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
            dp(48),
            1f,
        )
    }

    private fun restoreSelection() {
        val appearance = appearanceStore.load()
        updatingControls = true
        sizeGroup.check(
            when (appearance.buttonSize) {
                OverlayButtonSize.SMALL -> ID_SIZE_SMALL
                OverlayButtonSize.MEDIUM -> ID_SIZE_MEDIUM
                OverlayButtonSize.LARGE -> ID_SIZE_LARGE
            },
        )
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
        sizeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updatingControls) return@addOnButtonCheckedListener
            val size = when (checkedId) {
                ID_SIZE_SMALL -> OverlayButtonSize.SMALL
                ID_SIZE_LARGE -> OverlayButtonSize.LARGE
                else -> OverlayButtonSize.MEDIUM
            }
            appearanceStore.setButtonSize(size)
            refreshOverlayIfActive()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val ID_SIZE_SMALL = 0x10001
        private const val ID_SIZE_MEDIUM = 0x10002
        private const val ID_SIZE_LARGE = 0x10003
        private const val ID_SPACING_COMPACT = 0x10004
        private const val ID_SPACING_NORMAL = 0x10005
        private const val ID_SPACING_WIDE = 0x10006
    }
}
