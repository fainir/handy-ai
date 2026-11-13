package com.claudeagent.phone.dialer

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.text.InputFilter
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.text.InputType
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.isEmpty
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.claudeagent.phone.AccessibilityHelper
import com.claudeagent.phone.MainActivity
import com.claudeagent.phone.R
import com.claudeagent.phone.databinding.ActivityPhoneBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhoneActivity : AppCompatActivity() {
    private enum class Tab(val label: Int) {
        KEYPAD(R.string.phone_keypad), RECENTS(R.string.phone_recents), CONTACTS(R.string.phone_contacts),
    }

    private lateinit var binding: ActivityPhoneBinding
    private val directory by lazy { PhoneDirectory(applicationContext) }
    private var tab = Tab.KEYPAD
    private var number = ""
    private var contactQuery = ""
    private var lastDial = -1000L
    private var listJob: Job? = null
    private val chooseRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if (isDefaultPhone()) binding.phoneToolbar.announcePhone(R.string.phone_status_ready)
    }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    private val pickContact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@registerForActivityResult
        lifecycleScope.launch {
            val picked = readDirectory { directory.pickedNumber(uri) }
            if (picked == null) toast(R.string.phone_contact_error) else showNumber(picked)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AccessibilityHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPhoneBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.setBackgroundColor(phoneBackground(R.color.phone_bg))
        binding.phoneToolbar.setNavigationContentDescription(R.string.phone_close)
        binding.phoneToolbar.setNavigationOnClickListener { finish() }
        binding.phoneToolbar.menu.add(R.string.phone_ai).apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            setOnMenuItemClickListener {
                startActivity(Intent(this@PhoneActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                true
            }
        }
        binding.phoneToolbar.menu.add(R.string.phone_default_settings).setOnMenuItemClickListener {
            openSettings(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
            true
        }
        number = savedInstanceState?.getString("number").orEmpty()
        contactQuery = savedInstanceState?.getString("contactQuery").orEmpty()
        tab = savedInstanceState?.getString("tab")?.let(Tab::valueOf) ?: Tab.KEYPAD
        if (savedInstanceState == null) applyDialIntent(intent)
        Tab.entries.forEach { value ->
            binding.phoneTabs.addView(phoneButton(value.label).apply {
                setOnClickListener { selectTab(value) }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }
        refresh()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                PhoneCalls.updates.collect { updateReturnToCall() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDialIntent(intent)
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("number", number)
        outState.putString("tab", tab.name)
        outState.putString("contactQuery", contactQuery)
        super.onSaveInstanceState(outState)
    }

    private fun applyDialIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_DIAL) {
            tab = Tab.KEYPAD
            val uri = intent.data
            number = uri?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let(PhoneNumbers::normalize).orEmpty()
        }
    }

    private fun selectTab(value: Tab) {
        if (tab == value) return
        tab = value
        binding.phoneScroll.scrollTo(0, 0)
        refresh()
    }

    private fun refresh() {
        listJob?.cancel()
        val setup = binding.phoneSetup
        setup.removeAllViews()
        val manager = getSystemService(RoleManager::class.java)
        when {
            manager?.isRoleAvailable(RoleManager.ROLE_DIALER) != true -> setupCard(R.string.phone_unavailable)
            !isDefaultPhone() -> setupCard(R.string.phone_default_hint, R.string.phone_default) {
                chooseRole.launch(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
            !hasPhonePermission(Manifest.permission.CALL_PHONE) -> setupCard(R.string.phone_permission_hint, R.string.phone_allow_calls) {
                request(Manifest.permission.CALL_PHONE)
            }
            !hasCallAlerts() -> setupCard(R.string.phone_alerts_hint, R.string.phone_allow_alerts) { enableAlerts() }
            Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent() ->
                setupCard(R.string.phone_lock_hint, R.string.phone_lock_alerts) {
                    settings(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                }
        }
        for ((index, value) in Tab.entries.withIndex()) {
            val button = binding.phoneTabs.getChildAt(index) as MaterialButton
            button.isSelected = tab == value
            button.backgroundTintList = ColorStateList.valueOf(phoneBackground(R.color.phone_panel))
            button.setTextColor(phoneText(if (tab == value) R.color.phone_accent else R.color.phone_secondary))
            button.strokeColor = ColorStateList.valueOf(phoneText(if (tab == value) R.color.phone_accent else R.color.phone_outline))
        }
        binding.phoneContent.removeAllViews()
        when (tab) {
            Tab.KEYPAD -> showKeypad()
            Tab.CONTACTS -> showContacts()
            Tab.RECENTS -> showRecents()
        }
        updateReturnToCall()
    }

    private fun setupCard(message: Int, label: Int? = null, action: (() -> Unit)? = null) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = phonePanel()
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        binding.phoneSetup.addView(card, LinearLayout.LayoutParams(-1, -2))
        card.addView(phoneLabel(message).apply { setTextColor(phoneText(R.color.phone_secondary)) })
        if (label != null) card.addView(phoneButton(label).apply {
            setOnClickListener { action?.invoke() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun showKeypad() {
        val content = binding.phoneContent
        val entry = EditText(this).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_PHONE
            filters = arrayOf(InputFilter.LengthFilter(PhoneNumbers.MAX_LENGTH * 2))
            showSoftInputOnFocus = false
            isSaveEnabled = false
            hint = getString(R.string.phone_number)
            contentDescription = hint
            setTextColor(phoneText(R.color.phone_text))
            setHintTextColor(phoneText(R.color.phone_secondary))
            gravity = Gravity.CENTER
            textSize = 28f
            background = null
            minHeight = dp(62)
            setText(number)
            setSelection(text.length)
            doAfterTextChanged {
                number = it.toString()
                error = null
            }
        }
        content.addView(entry, LinearLayout.LayoutParams(-1, -2))
        content.fillPhoneKeys(phoneKeys) { key ->
            val start = minOf(entry.selectionStart, entry.selectionEnd).coerceAtLeast(0)
            val end = maxOf(entry.selectionStart, entry.selectionEnd).coerceAtLeast(start)
            val candidate = if (start == number.length) PhoneNumbers.append(number, key) else {
                number.replaceRange(start, end, key.toString()).takeIf { PhoneNumbers.normalize(it) != null } ?: number
            }
            if (candidate != number) {
                entry.setText(candidate)
                entry.setSelection((start + 1).coerceAtMost(candidate.length))
            }
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        row.addView(phoneButton(R.string.phone_call).apply {
            minHeight = dp(52)
            phoneFill(R.color.phone_accent)
            setOnClickListener {
                val clean = PhoneNumbers.normalize(number)
                if (clean == null) {
                    entry.error = getString(R.string.phone_invalid_number)
                    entry.requestFocus()
                } else placeCall(clean)
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(phoneButton(R.string.phone_delete).apply {
            minHeight = dp(52)
            setOnClickListener {
                val start = minOf(entry.selectionStart, entry.selectionEnd).coerceAtLeast(0)
                val end = maxOf(entry.selectionStart, entry.selectionEnd).coerceAtLeast(start)
                when {
                    end > start -> entry.text.delete(start, end)
                    start > 0 -> entry.text.delete(start - 1, start)
                }
            }
            setOnLongClickListener {
                entry.text.clear()
                announcePhone(R.string.phone_clear_number)
                true
            }
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
    }

    private fun showContacts() {
        val content = binding.phoneContent
        content.addView(phoneButton(R.string.phone_choose_contact).apply {
            setOnClickListener {
                try {
                    pickContact.launch(Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE))
                } catch (_: ActivityNotFoundException) {
                    toast(R.string.phone_contact_error)
                }
            }
        })
        if (!hasPhonePermission(Manifest.permission.READ_CONTACTS)) {
            content.addView(phoneLabel(R.string.phone_contacts_hint).apply { setPadding(0, dp(10), 0, dp(10)) })
            content.addView(phoneButton(R.string.phone_allow_contacts).apply {
                setOnClickListener { request(Manifest.permission.READ_CONTACTS) }
            })
            return
        }
        val search = EditText(this).apply {
            setSingleLine()
            isSaveEnabled = false
            hint = getString(R.string.phone_search_contacts)
            setHintTextColor(phoneText(R.color.phone_secondary))
            setTextColor(phoneText(R.color.phone_text))
            setText(contactQuery)
        }
        val entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(search)
        content.addView(entries)
        search.doAfterTextChanged {
            contactQuery = it.toString()
            val query = contactQuery.trim()
            loadList(entries, R.string.phone_no_contacts, 220) { directory.contacts(query) }
        }
        val query = contactQuery.trim()
        loadList(entries, R.string.phone_no_contacts) { directory.contacts(query) }
    }

    private fun showRecents() {
        val content = binding.phoneContent
        if (!isDefaultPhone()) {
            content.addView(phoneLabel(R.string.phone_recents_role_hint))
        } else if (!hasPhonePermission(Manifest.permission.READ_CALL_LOG)) {
            content.addView(phoneLabel(R.string.phone_recents_hint))
            content.addView(phoneButton(R.string.phone_allow_recents).apply {
                setOnClickListener { request(Manifest.permission.READ_CALL_LOG) }
            })
        } else loadList(content, R.string.phone_no_recents) { directory.recents() }
    }

    private fun loadList(target: LinearLayout, empty: Int, wait: Long = 0, load: () -> List<PhoneDirectoryEntry>) {
        listJob?.cancel()
        target.removeAllViews()
        target.addView(phoneLabel(R.string.phone_loading))
        listJob = lifecycleScope.launch {
            delay(wait)
            val entries = readDirectory(load)
            target.removeAllViews()
            if (entries.isNullOrEmpty()) {
                target.addView(phoneLabel(if (entries == null) R.string.phone_list_error else empty))
            } else entries.forEach { entry -> addEntry(target, entry) }
        }
    }

    private fun addEntry(target: LinearLayout, entry: PhoneDirectoryEntry) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = phonePanel()
            if (entry.number != null) {
                isFocusable = true
                contentDescription = getString(R.string.phone_directory_entry, entry.title, entry.detail)
                setOnClickListener { showNumber(entry.number) }
            }
        }
        row.addView(phoneLabel(R.string.phone_contacts, 18f).apply { text = entry.title })
        row.addView(phoneLabel(R.string.phone_recents, 14f).apply {
            text = entry.detail
            setTextColor(phoneText(R.color.phone_secondary))
        })
        target.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun showNumber(value: String) {
        number = value
        tab = Tab.KEYPAD
        refresh()
        binding.phoneScroll.scrollTo(0, 0)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun placeCall(number: String) {
        val roles = getSystemService(RoleManager::class.java)
        if (roles?.isRoleAvailable(RoleManager.ROLE_DIALER) != true) {
            toast(R.string.phone_unavailable)
            return
        }
        if (!isDefaultPhone()) {
            chooseRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            return
        }
        if (!hasPhonePermission(Manifest.permission.CALL_PHONE)) {
            request(Manifest.permission.CALL_PHONE)
            return
        }
        val telecom = getSystemService(TelecomManager::class.java)
        try {
            val emergency = getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(number) == true
            if (emergency) {
                startCarrierCall(telecom, number, null)
                return
            }
            val sims = telecom.callCapablePhoneAccounts.mapNotNull { handle ->
                telecom.getPhoneAccount(handle)?.takeIf { it.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) }
            }
            val preferred = telecom.getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            val sim = sims.singleOrNull() ?: sims.firstOrNull { it.accountHandle == preferred }
            when {
                sim != null -> startCarrierCall(telecom, number, sim)
                sims.isEmpty() -> toast(R.string.phone_no_sim)
                else -> {
                    val labels = sims.mapIndexed { index, item ->
                        item.label?.takeIf { it.isNotBlank() } ?: getString(R.string.phone_sim_number, index + 1)
                    }
                    AlertDialog.Builder(this).setTitle(R.string.phone_select_sim)
                        .setItems(labels.toTypedArray()) { _, which -> startCarrierCall(telecom, number, sims[which]) }
                        .setNegativeButton(R.string.phone_cancel, null).show()
                }
            }
        } catch (_: RuntimeException) {
            toast(R.string.phone_cannot_call)
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun startCarrierCall(telecom: TelecomManager, number: String, account: PhoneAccount?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastDial < 900) return
        lastDial = now
        val extras = Bundle().apply {
            if (account != null) putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account.accountHandle)
        }
        try {
            telecom.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), extras)
        } catch (_: RuntimeException) {
            toast(R.string.phone_cannot_call)
        }
    }

    private fun updateReturnToCall() {
        val visible = PhoneCalls.service?.primaryCall() != null
        binding.phoneReturn.isVisible = visible
        if (visible && binding.phoneReturn.isEmpty()) {
            binding.phoneReturn.addView(phoneButton(R.string.phone_return_to_call).apply {
                phoneFill(R.color.phone_accent)
                setOnClickListener { PhoneCalls.service?.showCall() }
            })
        }
    }

    private fun enableAlerts() {
        if (Build.VERSION.SDK_INT >= 33 && !hasPhonePermission(Manifest.permission.POST_NOTIFICATIONS)) {
            request(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun request(name: String) {
        val preferences = getPreferences(MODE_PRIVATE)
        if (preferences.getBoolean(name, false) && !shouldShowRequestPermissionRationale(name)) {
            settings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        } else {
            preferences.edit { putBoolean(name, true) }
            permission.launch(name)
        }
    }

    private fun settings(action: String) = openSettings(Intent(action, Uri.fromParts("package", packageName, null)))

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.phone_settings_unavailable)
        }
    }

    private suspend fun <T> readDirectory(read: () -> T): T? = withContext(Dispatchers.IO) {
        try {
            read()
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        internal fun intent(context: Context, number: String? = null): Intent =
            Intent(context, PhoneActivity::class.java).apply {
                if (number != null) {
                    action = Intent.ACTION_DIAL
                    data = Uri.fromParts("tel", number, null)
                }
            }
    }
}
