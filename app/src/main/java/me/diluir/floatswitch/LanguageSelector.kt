package me.diluir.floatswitch

import android.content.res.Resources
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton

class LanguageSelector(
    private val activity: AppCompatActivity,
    private val headerButton: AppCompatImageButton,
) {
    fun bind() {
        updateHeaderButton()
        headerButton.setOnClickListener { showLanguageDialog() }
    }

    private fun updateHeaderButton() {
        val selectedLanguage = currentSelection()
        val displayedLanguage = displayedLanguage(selectedLanguage)
        headerButton.setImageResource(displayedLanguage.iconResource)
        headerButton.imageTintList = null
        headerButton.contentDescription = if (selectedLanguage == AppLanguage.AUTOMATIC) {
            activity.getString(R.string.language_selector_description_automatic)
        } else {
            activity.getString(
                R.string.language_selector_description,
                activity.getString(selectedLanguage.displayNameResource),
            )
        }
    }

    private fun showLanguageDialog() {
        val columns = if (
            activity.resources.configuration.screenWidthDp >= WIDE_SCREEN_MIN_WIDTH_DP
        ) {
            WIDE_GRID_COLUMNS
        } else {
            NARROW_GRID_COLUMNS
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dimension(R.dimen.language_dialog_padding)
            setPadding(padding, padding, padding, padding)
        }
        content.addView(TextView(activity).apply {
            setText(R.string.language_sheet_title)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
        })
        val selectedLanguage = currentSelection()
        val automaticLanguage = displayedLanguage(AppLanguage.AUTOMATIC)
        val rows = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            AppLanguageRules.supportedLanguages.chunked(columns).forEach { rowLanguages ->
                addView(LinearLayout(activity).apply {
                    gravity = Gravity.CENTER
                    orientation = LinearLayout.HORIZONTAL
                    rowLanguages.forEach { language ->
                        addView(
                            createLanguageOption(
                                language,
                                selectedLanguage,
                                automaticLanguage,
                            ) { dismissDialog() },
                            languageButtonLayoutParams(),
                        )
                    }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }
        }
        content.addView(rows, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = dimension(R.dimen.language_dialog_title_spacing)
        })
        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(content)
            .create()
        rows.tag = dialog
        dialog.show()
        val desiredWidth = dialogWidth(columns)
        val availableWidth = activity.resources.displayMetrics.widthPixels -
            (2 * dimension(R.dimen.language_dialog_screen_margin))
        dialog.window?.setLayout(
            desiredWidth.coerceAtMost(availableWidth),
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun createLanguageOption(
        language: AppLanguage,
        selectedLanguage: AppLanguage,
        automaticLanguage: AppLanguage,
        dismissDialog: () -> Unit,
    ): View {
        val displayedLanguage = if (language == AppLanguage.AUTOMATIC) {
            automaticLanguage
        } else {
            language
        }
        val button = MaterialButton(
            activity,
            null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = null
            setIconResource(displayedLanguage.iconResource)
            iconTint = null
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            contentDescription = activity.getString(language.displayNameResource)
            tooltipText = contentDescription
            isCheckable = true
            isChecked = language == selectedLanguage
            gravity = Gravity.CENTER
            minWidth = 0
            minHeight = 0
            insetTop = 0
            insetBottom = 0
            cornerRadius = dimension(R.dimen.language_grid_button_corner_radius)
            strokeWidth = dimension(R.dimen.language_grid_button_stroke_width)
            backgroundTintList = activity.getColorStateList(R.color.language_option_background_tint)
            strokeColor = activity.getColorStateList(R.color.language_option_stroke_tint)
            setOnClickListener {
                dismissDialog()
                applyLanguage(language)
            }
        }
        if (language != AppLanguage.AUTOMATIC) return button

        return FrameLayout(activity).apply {
            addView(button, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            addView(TextView(activity).apply {
                setText(R.string.language_automatic_badge)
                gravity = Gravity.CENTER
                setTextColor(activity.getColor(R.color.language_automatic_badge_text))
                setTextSize(
                    android.util.TypedValue.COMPLEX_UNIT_SP,
                    LANGUAGE_BADGE_TEXT_SIZE_SP,
                )
                setBackgroundResource(R.drawable.bg_language_automatic_badge)
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(
                dimension(R.dimen.language_automatic_badge_size),
                dimension(R.dimen.language_automatic_badge_size),
                Gravity.TOP or Gravity.END,
            ).apply {
                topMargin = dimension(R.dimen.language_automatic_badge_margin)
                marginEnd = dimension(R.dimen.language_automatic_badge_margin)
            })
        }
    }

    private fun LinearLayout.dismissDialog() {
        (tag as? androidx.appcompat.app.AlertDialog)?.dismiss()
    }

    private fun languageButtonLayoutParams() = LinearLayout.LayoutParams(
        dimension(R.dimen.language_grid_button_size),
        dimension(R.dimen.language_grid_button_height),
    ).apply {
        val halfSpacing = dimension(R.dimen.language_grid_option_spacing) / 2
        setMargins(halfSpacing, halfSpacing, halfSpacing, halfSpacing)
    }

    private fun dialogWidth(columns: Int): Int =
        dimension(R.dimen.language_dialog_window_insets) +
            (2 * dimension(R.dimen.language_dialog_padding)) +
            columns * (
                dimension(R.dimen.language_grid_button_size) +
                    dimension(R.dimen.language_grid_option_spacing)
                )

    private fun applyLanguage(language: AppLanguage) {
        val locales = if (language == AppLanguage.AUTOMATIC) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun currentSelection(): AppLanguage =
        AppLanguageRules.fromApplicationLanguageTags(
            AppCompatDelegate.getApplicationLocales().toLanguageTags(),
        )

    private fun displayedLanguage(selectedLanguage: AppLanguage): AppLanguage {
        if (selectedLanguage != AppLanguage.AUTOMATIC) return selectedLanguage
        val locales = ConfigurationCompat.getLocales(Resources.getSystem().configuration)
        return AppLanguageRules.effectiveAutomaticLanguage(
            (0 until locales.size()).mapNotNull { index ->
                locales[index]?.toLanguageTag()
            },
        )
    }

    private fun dimension(resource: Int): Int =
        activity.resources.getDimensionPixelSize(resource)

    companion object {
        private const val WIDE_SCREEN_MIN_WIDTH_DP = 600
        private const val WIDE_GRID_COLUMNS = 5
        private const val NARROW_GRID_COLUMNS = 4
        private const val LANGUAGE_BADGE_TEXT_SIZE_SP = 9f
    }
}
