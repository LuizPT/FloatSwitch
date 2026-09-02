package me.diluir.floatswitch

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import java.util.Locale

class AppPickerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_app_picker)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appPickerRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val selectionIndex = intent.getIntExtra(EXTRA_SELECTION_INDEX, INVALID_INDEX)
        if (selectionIndex !in APPEND_INDEX until SelectedAppsRules.MAX_APPLICATIONS) {
            finish()
            return
        }

        val repository = LauncherAppsRepository(packageManager, packageName)
        val applications = repository.loadInstalledApps(
            excludedPackageNames = intent.getStringArrayListExtra(EXTRA_EXCLUDED_PACKAGES)
                ?.toSet()
                .orEmpty(),
        )
        val listView = findViewById<ListView>(R.id.applicationsList)
        val emptyView = findViewById<TextView>(R.id.emptyApplicationsText)
        val adapter = InstalledAppsAdapter(this, applications)

        listView.adapter = adapter
        listView.emptyView = emptyView
        listView.setOnItemClickListener { _, _, position, _ ->
            val selection = adapter.getItem(position).selection
            setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    putExtra(EXTRA_SELECTION_INDEX, selectionIndex)
                    putExtra(EXTRA_SELECTION, SelectedAppCodec.encode(selection))
                },
            )
            finish()
        }

        findViewById<EditText>(R.id.applicationSearchInput).doAfterTextChanged { editable ->
            adapter.filter(editable?.toString().orEmpty())
        }
    }

    companion object {
        const val APPEND_INDEX = -1
        const val EXTRA_SELECTION = "selected_app"
        const val EXTRA_SELECTION_INDEX = "selected_app_index"
        private const val EXTRA_EXCLUDED_PACKAGES = "excluded_packages"
        private const val INVALID_INDEX = Int.MIN_VALUE

        fun createIntent(
            context: Context,
            selectionIndex: Int,
            excludedPackageNames: Collection<String>,
        ): Intent = Intent(context, AppPickerActivity::class.java).apply {
            putExtra(EXTRA_SELECTION_INDEX, selectionIndex)
            putStringArrayListExtra(
                EXTRA_EXCLUDED_PACKAGES,
                ArrayList(excludedPackageNames),
            )
        }
    }
}

private class InstalledAppsAdapter(
    private val context: Context,
    installedApps: List<InstalledLauncherApp>,
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private val allItems = installedApps
    private var visibleItems = installedApps

    override fun getCount(): Int = visibleItems.size

    override fun getItem(position: Int): InstalledLauncherApp = visibleItems[position]

    override fun getItemId(position: Int): Long = getItem(position).selection.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val rowView: View
        val holder: ViewHolder

        if (convertView == null) {
            rowView = inflater.inflate(R.layout.item_launcher_app, parent, false)
            holder = ViewHolder(
                icon = rowView.findViewById(R.id.applicationIcon),
                name = rowView.findViewById(R.id.applicationName),
            )
            rowView.tag = holder
        } else {
            rowView = convertView
            holder = convertView.tag as ViewHolder
        }

        val installedApp = getItem(position)
        holder.icon.setImageDrawable(installedApp.icon)
        holder.icon.contentDescription = context.getString(
            R.string.application_icon_description,
            installedApp.selection.displayName,
        )
        holder.name.text = installedApp.selection.displayName
        return rowView
    }

    fun filter(query: String) {
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        visibleItems = if (normalizedQuery.isEmpty()) {
            allItems
        } else {
            allItems.filter { installedApp ->
                installedApp.selection.displayName
                    .lowercase(Locale.getDefault())
                    .contains(normalizedQuery)
            }
        }
        notifyDataSetChanged()
    }

    private data class ViewHolder(
        val icon: ImageView,
        val name: TextView,
    )
}
