package com.longsms.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val AppPurple = Color(0xFF6D28D9)
private val AppPurpleLight = Color(0xFF8B5CF6)
private val AppPurpleDeep = Color(0xFF4C1D95)
private val AppLavender = Color(0xFFF0E8FF)
private val AppDanger = Color(0xFFDC2626)

private val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 32.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 28.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 28.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 23.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp
    )
)

class MainActivity : ComponentActivity() {

    private var pendingSmsAction: (() -> Unit)? = null
    private var showSmsPermissionGuide by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val smsPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->

                if (granted) {
                    pendingSmsAction?.invoke()
                } else {
                    showSmsPermissionGuide = true
                }

                pendingSmsAction = null
            }

        setContent {
            SmsLongApp(
                requestSmsPermission = { action ->
                    if (
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        action()
                    } else {
                        pendingSmsAction = action
                        smsPermissionLauncher.launch(
                            Manifest.permission.SEND_SMS
                        )
                    }
                },
                showSmsPermissionGuide = showSmsPermissionGuide,
                onDismissSmsPermissionGuide = {
                    showSmsPermissionGuide = false
                },
                onOpenSmsSettings = {
                    showSmsPermissionGuide = false
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            )
        }
    }
}

private enum class Page {
    SEND,
    GROUPS,
    SETTINGS
}

private enum class RecipientMode {
    PERSON,
    GROUP
}

private data class GroupMember(
    val name: String,
    val phone: String
)

private data class ContactGroup(
    val id: String,
    val name: String,
    val members: List<GroupMember>
)

private data class SimOption(
    val subscriptionId: Int,
    val slotIndex: Int,
    val label: String
)

private class AppPrefs(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "long_sms_sender",
            Context.MODE_PRIVATE
        )

    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) {
            prefs.edit().putBoolean("dark_theme", value).apply()
        }

    var confirmBeforeSend: Boolean
        get() = prefs.getBoolean("confirm_before_send", true)
        set(value) {
            prefs.edit().putBoolean("confirm_before_send", value).apply()
        }

    var selectedSimId: Int
        get() = prefs.getInt(
            "selected_sim",
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        set(value) {
            prefs.edit().putInt("selected_sim", value).apply()
        }

    fun loadGroups(): List<ContactGroup> {
        return try {
            val groupsArray = JSONArray(
                prefs.getString("groups", "[]") ?: "[]"
            )

            buildList {
                for (i in 0 until groupsArray.length()) {
                    val groupJson = groupsArray.getJSONObject(i)
                    val membersArray = groupJson.optJSONArray("members") ?: JSONArray()

                    val members = buildList {
                        for (j in 0 until membersArray.length()) {
                            val member = membersArray.getJSONObject(j)
                            add(
                                GroupMember(
                                    name = member.optString("name"),
                                    phone = member.optString("phone")
                                )
                            )
                        }
                    }

                    add(
                        ContactGroup(
                            id = groupJson.optString("id"),
                            name = groupJson.optString("name"),
                            members = members
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveGroups(groups: List<ContactGroup>) {
        val groupsArray = JSONArray()

        groups.forEach { group ->
            val groupJson = JSONObject()
            groupJson.put("id", group.id)
            groupJson.put("name", group.name)

            val membersArray = JSONArray()
            group.members.forEach { member ->
                val memberJson = JSONObject()
                memberJson.put("name", member.name)
                memberJson.put("phone", member.phone)
                membersArray.put(memberJson)
            }

            groupJson.put("members", membersArray)
            groupsArray.put(groupJson)
        }

        prefs.edit().putString("groups", groupsArray.toString()).apply()
    }
}

@Composable
private fun SmsLongApp(
    requestSmsPermission: ((() -> Unit)) -> Unit,
    showSmsPermissionGuide: Boolean,
    onDismissSmsPermissionGuide: () -> Unit,
    onOpenSmsSettings: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }

    var darkTheme by remember { mutableStateOf(prefs.darkTheme) }
    var confirmBeforeSend by remember { mutableStateOf(prefs.confirmBeforeSend) }
    var selectedSimId by remember { mutableIntStateOf(prefs.selectedSimId) }
    var page by rememberSaveable { mutableStateOf(Page.SEND) }
    var showAbout by remember { mutableStateOf(false) }

    val groups = remember {
        mutableStateListOf(*prefs.loadGroups().toTypedArray())
    }

    val colors = if (darkTheme) {
        darkColorScheme(
            primary = AppPurpleLight,
            background = Color(0xFF0B0B10),
            surface = Color(0xFF15151C),
            surfaceVariant = Color(0xFF20202A),
            onBackground = Color.White,
            onSurface = Color(0xFFF6F4FA),
            outline = Color(0xFF77727F),
            outlineVariant = Color(0xFF34313C)
        )
    } else {
        lightColorScheme(
            primary = AppPurple,
            background = Color(0xFFFAFAFC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFFBF9FD),
            onBackground = Color(0xFF17151D),
            onSurface = Color(0xFF17151D),
            outline = Color(0xFF85808D),
            outlineVariant = Color(0xFFE8E3ED)
        )
    }

    val activity = context as? Activity

    SideEffect {
        activity?.let {
            it.window.statusBarColor = colors.background.toArgb()
            it.window.navigationBarColor = colors.background.toArgb()

            WindowCompat.getInsetsController(
                it.window,
                it.window.decorView
            ).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val headerTitle = when (page) {
        Page.SEND -> "پیامک طولانی"
        Page.GROUPS -> "گروه‌ها"
        Page.SETTINGS -> "تنظیمات"
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    AppHeader(
                        title = headerTitle,
                        darkTheme = darkTheme,
                        onToggleTheme = {
                            darkTheme = !darkTheme
                            prefs.darkTheme = darkTheme
                        },
                        onAbout = {
                            showAbout = true
                        }
                    )
                },
                bottomBar = {
                    AppBottomBar(
                        page = page,
                        onPageSelected = {
                            page = it
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(padding)
                ) {
                    when (page) {
                        Page.SEND -> {
                            SendScreen(
                                groups = groups,
                                selectedSimId = selectedSimId,
                                confirmBeforeSend = confirmBeforeSend,
                                requestSmsPermission = requestSmsPermission,
                                onOpenGroups = {
                                    page = Page.GROUPS
                                }
                            )
                        }

                        Page.GROUPS -> {
                            GroupsScreen(
                                groups = groups,
                                onSaveGroup = { group ->
                                    val index =
                                        groups.indexOfFirst {
                                            it.id == group.id
                                        }

                                    if (index >= 0) {
                                        groups[index] = group
                                    } else {
                                        groups.add(group)
                                    }

                                    prefs.saveGroups(groups)
                                },
                                onDeleteGroup = { group ->
                                    groups.removeAll {
                                        it.id == group.id
                                    }
                                    prefs.saveGroups(groups)
                                }
                            )
                        }

                        Page.SETTINGS -> {
                            SettingsScreen(
                                selectedSimId = selectedSimId,
                                onSelectedSimChanged = {
                                    selectedSimId = it
                                    prefs.selectedSimId = it
                                },
                                confirmBeforeSend = confirmBeforeSend,
                                onConfirmChanged = {
                                    confirmBeforeSend = it
                                    prefs.confirmBeforeSend = it
                                },
                                onAbout = {
                                    showAbout = true
                                }
                            )
                        }
                    }
                }
            }

            if (showAbout) {
                AboutAppDialog(
                    onDismiss = {
                        showAbout = false
                    }
                )
            }

            if (showSmsPermissionGuide) {
                SmsPermissionGuideDialog(
                    onOpenSettings = onOpenSmsSettings,
                    onDismiss = onDismissSmsPermissionGuide
                )
            }
        }
    }
}

@Composable
private fun AppHeader(
    title: String,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAbout: () -> Unit
) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(
                        start = 16.dp,
                        end = 18.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIconButton(
                    icon =
                        if (darkTheme)
                            Icons.Outlined.LightMode
                        else
                            Icons.Outlined.DarkMode,
                    contentDescription = "تغییر تم",
                    onClick = onToggleTheme,
                    buttonSize = 36.dp,
                    iconSize = 20.dp
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = title,
                    textAlign = TextAlign.End,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            HorizontalDivider(
                modifier = Modifier.align(Alignment.BottomCenter),
                color =
                    MaterialTheme.colorScheme.outlineVariant
                        .copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun SendScreen(
    groups: List<ContactGroup>,
    selectedSimId: Int,
    confirmBeforeSend: Boolean,
    requestSmsPermission: ((() -> Unit)) -> Unit,
    onOpenGroups: () -> Unit
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var recipientMode by rememberSaveable {
        mutableStateOf(RecipientMode.PERSON)
    }

    var phone by rememberSaveable {
        mutableStateOf("")
    }

    var contactName by rememberSaveable {
        mutableStateOf("")
    }

    var message by rememberSaveable {
        mutableStateOf("")
    }

    var selectedGroupId by rememberSaveable {
        mutableStateOf("")
    }

    var showGroupPicker by remember {
        mutableStateOf(false)
    }

    var showConfirm by remember {
        mutableStateOf(false)
    }

    val selectedGroup =
        groups.firstOrNull {
            it.id == selectedGroupId
        }

    val contactPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    context.contentResolver.query(
                        uri,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex =
                                cursor.getColumnIndex(
                                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                                )

                            val phoneIndex =
                                cursor.getColumnIndex(
                                    ContactsContract.CommonDataKinds.Phone.NUMBER
                                )

                            if (phoneIndex >= 0) {
                                phone =
                                    cursor.getString(phoneIndex).orEmpty()

                                val pickedName =
                                    if (nameIndex >= 0)
                                        cursor.getString(nameIndex).orEmpty()
                                    else
                                        ""

                                contactName =
                                    pickedName.takeIf {
                                        it.isNotBlank() &&
                                            normalizePhone(it) != normalizePhone(phone)
                                    }.orEmpty()
                            }
                        }
                    }
                }
            }
        }

    fun targetNumbers(): List<String> {
        return when (recipientMode) {
            RecipientMode.PERSON ->
                listOf(phone)
                    .filter {
                        normalizePhone(it).isNotBlank()
                    }

            RecipientMode.GROUP ->
                selectedGroup
                    ?.members
                    ?.map { it.phone }
                    ?.distinctBy {
                        normalizePhone(it)
                    }
                    .orEmpty()
        }
    }

    fun sendNow() {
        val targets = targetNumbers()

        if (targets.isEmpty()) {
            scope.launch {
                snackbar.showSnackbar(
                    if (recipientMode == RecipientMode.GROUP)
                        "یک گروه را انتخاب کنید."
                    else
                        "شماره گیرنده را وارد کنید."
                )
            }
            return
        }

        if (message.isBlank()) {
            scope.launch {
                snackbar.showSnackbar(
                    "متن پیام را وارد کنید."
                )
            }
            return
        }

        requestSmsPermission {
            try {
                targets.forEach { number ->
                    LongSmsSender.send(
                        context = context,
                        subscriptionId = selectedSimId,
                        phone = number,
                        message = message
                    )
                }

                scope.launch {
                    snackbar.showSnackbar(
                        if (targets.size == 1)
                            "پیام با موفقیت ارسال شد."
                        else
                            "پیام برای ${targets.size} مخاطب ارسال شد."
                    )
                }

            } catch (e: Exception) {
                scope.launch {
                    snackbar.showSnackbar(
                        e.message ?: "ارسال پیام ناموفق بود."
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp,
                    vertical = 10.dp
                )
        ) {
            RecipientModeSelector(
                selected = recipientMode,
                onSelected = {
                    recipientMode = it
                }
            )

            Spacer(Modifier.height(12.dp))

            if (recipientMode == RecipientMode.PERSON) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.width(8.dp))

                        OutlinedTextField(
                            value =
                                if (contactName.isNotBlank())
                                    contactName
                                else
                                    phone,
                            onValueChange = {
                                if (contactName.isBlank()) {
                                    phone = it
                                }
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "شماره گیرنده",
                                    color =
                                        MaterialTheme.colorScheme.onSurface
                                            .copy(alpha = 0.55f)
                                )
                            },
                            singleLine = true,
                            readOnly = contactName.isNotBlank(),
                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType = KeyboardType.Phone
                                ),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent,
                                    disabledBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                            textStyle =
                                MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 14.sp
                                ),
                            trailingIcon = {
                                if (contactName.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            contactName = ""
                                            phone = ""
                                        }
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            "پاک کردن"
                                        )
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.width(8.dp))

                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    contactPicker.launch(
                                        Intent(
                                            Intent.ACTION_PICK,
                                            ContactsContract
                                                .CommonDataKinds
                                                .Phone
                                                .CONTENT_URI
                                        )
                                    )
                                },
                            shape = RoundedCornerShape(12.dp),
                            color =
                                MaterialTheme.colorScheme.primary
                                    .copy(alpha = 0.07f),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary
                                        .copy(alpha = 0.10f)
                                )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Contacts,
                                    "انتخاب مخاطب",
                                    modifier = Modifier.size(23.dp),
                                    tint =
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .clickable {
                            if (groups.isEmpty()) {
                                onOpenGroups()
                            } else {
                                showGroupPicker = true
                            }
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 5.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color =
                                MaterialTheme.colorScheme.primary
                                    .copy(alpha = 0.10f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Groups,
                                    null,
                                    tint =
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                selectedGroup?.name
                                    ?: "انتخاب گروه",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(Modifier.height(1.dp))

                            Text(
                                when {
                                    groups.isEmpty() ->
                                        "ابتدا یک گروه بسازید"

                                    selectedGroup != null ->
                                        "${selectedGroup.members.size} مخاطب"

                                    else ->
                                        "برای انتخاب لمس کنید"
                                },
                                fontSize = 10.sp,
                                color =
                                    MaterialTheme.colorScheme.onSurface
                                        .copy(alpha = 0.52f)
                            )
                        }

                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint =
                                MaterialTheme.colorScheme.onSurface
                                    .copy(alpha = 0.55f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 5.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.width(8.dp))

                        Text(
                            "متن پیام",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    OutlinedTextField(
                        value = message,
                        onValueChange = {
                            message = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        placeholder = {
                            Text(
                                "متن طولانی خود را اینجا بنویسید...",
                                color =
                                    MaterialTheme.colorScheme.onSurface
                                        .copy(alpha = 0.50f)
                            )
                        },
                        singleLine = false,
                        minLines = 1,
                        maxLines = Int.MAX_VALUE,
                        textStyle =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            ),
                        shape = RoundedCornerShape(20.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor =
                                    MaterialTheme.colorScheme.primary
                                        .copy(alpha = 0.62f),
                                unfocusedBorderColor =
                                    MaterialTheme.colorScheme.outline
                                        .copy(alpha = 0.32f),
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        "${message.length} کاراکتر",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                        fontSize = 12.sp,
                        color =
                            MaterialTheme.colorScheme.onSurface
                                .copy(alpha = 0.55f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            PrimaryActionButton(
                text = "ارسال پیام",
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp,
                onClick = {
                    val targets = targetNumbers()

                    when {
                        targets.isEmpty() -> {
                            scope.launch {
                                snackbar.showSnackbar(
                                    "گیرنده را انتخاب کنید."
                                )
                            }
                        }

                        message.isBlank() -> {
                            scope.launch {
                                snackbar.showSnackbar(
                                    "متن پیام را وارد کنید."
                                )
                            }
                        }

                        confirmBeforeSend -> {
                            showConfirm = true
                        }

                        else -> {
                            sendNow()
                        }
                    }
                }
            )

            Spacer(Modifier.height(1.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(
                Alignment.BottomCenter
            )
        )
    }

    if (showGroupPicker) {
        GroupPickerDialog(
            groups = groups,
            selectedGroupId = selectedGroupId,
            onSelect = {
                selectedGroupId = it.id
                showGroupPicker = false
            },
            onDismiss = {
                showGroupPicker = false
            }
        )
    }

    if (showConfirm) {
        val targets = targetNumbers()

        MessagePreviewDialog(
            recipientTitle =
                if (recipientMode == RecipientMode.GROUP)
                    "گروه: ${selectedGroup?.name.orEmpty()}"
                else
                    "گیرنده: ${contactName.ifBlank { phone }}",
            recipientCount = targets.size,
            message = message,
            onDismiss = {
                showConfirm = false
            },
            onSend = {
                showConfirm = false
                sendNow()
            }
        )
    }
}

@Composable
private fun GroupsScreen(
    groups: List<ContactGroup>,
    onSaveGroup: (ContactGroup) -> Unit,
    onDeleteGroup: (ContactGroup) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var contacts by remember {
        mutableStateOf(emptyList<GroupMember>())
    }

    var editingGroup by remember {
        mutableStateOf<ContactGroup?>(null)
    }

    var deleteGroup by remember {
        mutableStateOf<ContactGroup?>(null)
    }

    var pendingEdit by remember {
        mutableStateOf<ContactGroup?>(null)
    }

    var pendingCreate by remember {
        mutableStateOf(false)
    }

    fun loadContactsAndEdit(group: ContactGroup) {
        scope.launch {
            contacts = withContext(Dispatchers.IO) {
                ContactRepository.load(context)
            }
            editingGroup = group
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                val target =
                    if (pendingCreate) {
                        ContactGroup(
                            id =
                                UUID.randomUUID().toString(),
                            name = "",
                            members = emptyList()
                        )
                    } else {
                        pendingEdit
                    }

                if (target != null) {
                    loadContactsAndEdit(target)
                }
            } else {
                Toast.makeText(
                    context,
                    "برای ساخت گروه باید مجوز مخاطبین فعال باشد.",
                    Toast.LENGTH_LONG
                ).show()
            }

            pendingCreate = false
            pendingEdit = null
        }

    fun openEditor(group: ContactGroup?) {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadContactsAndEdit(
                group ?: ContactGroup(
                    id =
                        UUID.randomUUID().toString(),
                    name = "",
                    members = emptyList()
                )
            )
        } else {
            pendingCreate = group == null
            pendingEdit = group
            permissionLauncher.launch(
                Manifest.permission.READ_CONTACTS
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = 18.dp,
                vertical = 10.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
        contentPadding =
            PaddingValues(bottom = 12.dp)
    ) {
        item {
            SecondaryActionButton(
                text = "ساخت گروه جدید",
                icon = Icons.Outlined.Add,
                modifier = Modifier.fillMaxWidth(),
                height = 56.dp,
                onClick = {
                    openEditor(null)
                }
            )

            Spacer(Modifier.height(4.dp))
        }

        if (groups.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 24.dp,
                                vertical = 34.dp
                            ),
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(70.dp),
                            shape = CircleShape,
                            color =
                                MaterialTheme.colorScheme.primary
                                    .copy(alpha = 0.10f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Groups,
                                    null,
                                    modifier = Modifier.size(36.dp),
                                    tint =
                                        MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            "هنوز گروهی ساخته نشده است",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            "برای ارسال گروهی، اولین گروه مخاطبین خود را بسازید.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color =
                                MaterialTheme.colorScheme.onSurface
                                    .copy(alpha = 0.55f)
                        )
                    }
                }
            }
        } else {
            items(
                groups,
                key = { it.id }
            ) { group ->
                GroupListCard(
                    group = group,
                    onEdit = {
                        openEditor(group)
                    },
                    onDelete = {
                        deleteGroup = group
                    }
                )
            }
        }
    }

    editingGroup?.let { group ->
        GroupEditorDialog(
            group = group,
            contacts = contacts,
            onDismiss = {
                editingGroup = null
            },
            onSave = {
                onSaveGroup(it)
                editingGroup = null
            }
        )
    }

    deleteGroup?.let { group ->
        DeleteGroupDialog(
            groupName = group.name,
            onDismiss = {
                deleteGroup = null
            },
            onDelete = {
                onDeleteGroup(group)
                deleteGroup = null
            }
        )
    }
}

@Composable
private fun GroupEditorDialog(
    group: ContactGroup,
    contacts: List<GroupMember>,
    onDismiss: () -> Unit,
    onSave: (ContactGroup) -> Unit
) {
    var name by remember(group.id) {
        mutableStateOf(group.name)
    }

    var search by remember {
        mutableStateOf("")
    }

    val selected = remember(group.id) {
        mutableStateListOf<String>().apply {
            addAll(
                group.members.map {
                    normalizePhone(it.phone)
                }
            )
        }
    }

    val allContacts = remember(contacts, group.members) {
        (contacts + group.members)
            .distinctBy {
                normalizePhone(it.phone)
            }
    }

    val filtered = remember(search, allContacts) {
        if (search.isBlank()) {
            allContacts
        } else {
            allContacts.filter {
                it.name.contains(
                    search,
                    ignoreCase = true
                ) ||
                    it.phone.contains(search)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 14.dp,
                        vertical = 14.dp
                    )
            ) {

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary
                                .copy(alpha = 0.11f)
                    ) {
                        Box(
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Icon(
                                imageVector =
                                    if (group.name.isBlank())
                                        Icons.Outlined.GroupAdd
                                    else
                                        Icons.Outlined.Groups,
                                contentDescription = null,
                                tint =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                            )
                        }
                    }

                    Spacer(Modifier.width(6.dp))

                    Text(
                        text =
                            if (group.name.isBlank())
                                "ساخت گروه"
                            else
                                "ویرایش گروه",
                        modifier = Modifier.weight(1f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }

                Spacer(Modifier.height(6.dp))

                // Group name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    label = {
                        Text("نام گروه", fontSize = 13.sp)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(15.dp)
                )

                Spacer(Modifier.height(6.dp))

                // Search
                OutlinedTextField(
                    value = search,
                    onValueChange = {
                        search = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    placeholder = {
                        Text("جستجوی مخاطب", fontSize = 13.sp)
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            null
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(15.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor =
                                MaterialTheme
                                    .colorScheme
                                    .outline
                                    .copy(alpha = 0.22f),
                            focusedBorderColor =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            unfocusedContainerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                                    .copy(alpha = 0.30f),
                            focusedContainerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                                    .copy(alpha = 0.20f)
                        )
                )

                Spacer(Modifier.height(6.dp))

                // Selected count chip
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary
                            .copy(alpha = 0.10f)
                ) {
                    Row(
                        modifier =
                            Modifier.padding(
                                horizontal = 10.dp,
                                vertical = 5.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Groups,
                            null,
                            modifier =
                                Modifier.size(17.dp),
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )

                        Spacer(Modifier.width(5.dp))

                        Text(
                            "${selected.size} مخاطب انتخاب شده",
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Contacts card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    color =
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                            .copy(alpha = 0.22f),
                    tonalElevation = 1.dp
                ) {
                    if (filtered.isEmpty()) {
                        Column(
                            modifier =
                                Modifier.fillMaxSize(),
                            horizontalAlignment =
                                Alignment.CenterHorizontally,
                            verticalArrangement =
                                Arrangement.Center
                        ) {
                            Icon(
                                Icons.Outlined.PersonSearch,
                                null,
                                modifier =
                                    Modifier.size(30.dp),
                                tint =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(alpha = 0.38f)
                            )

                            Spacer(Modifier.height(6.dp))

                            Text(
                                "مخاطبی پیدا نشد",
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(alpha = 0.55f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier =
                                Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    vertical = 3.dp
                                )
                        ) {
                            items(
                                filtered,
                                key = {
                                    normalizePhone(it.phone)
                                }
                            ) { contact ->

                                val key =
                                    normalizePhone(
                                        contact.phone
                                    )

                                val checked =
                                    selected.contains(key)

                                ProfessionalContactRow(
                                    contact = contact,
                                    checked = checked,
                                    onToggle = {
                                        if (checked) {
                                            selected.remove(key)
                                        } else {
                                            selected.add(key)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Bottom actions
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryActionButton(
                        text = "انصراف",
                        modifier =
                            Modifier.weight(1f),
                        height = 50.dp,
                        onClick = onDismiss
                    )

                    PrimaryActionButton(
                        text = "ذخیره",
                        icon = Icons.Outlined.Check,
                        enabled =
                            name.isNotBlank() &&
                                selected.isNotEmpty(),
                        modifier =
                            Modifier.weight(1f),
                        height = 50.dp,
                        onClick = {
                            val map =
                                allContacts
                                    .associateBy {
                                        normalizePhone(
                                            it.phone
                                        )
                                    }

                            val members =
                                selected.mapNotNull {
                                    map[it]
                                }

                            onSave(
                                group.copy(
                                    name =
                                        name.trim(),
                                    members =
                                        members
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfessionalContactRow(
    contact: GroupMember,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(
                horizontal = 10.dp,
                vertical = 4.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        ContactInitialAvatar(
            name = contact.name,
            phone = contact.phone
        )

        Spacer(Modifier.width(8.dp))

        Column(
            modifier =
                Modifier.weight(1f)
        ) {
            Text(
                text =
                    contact.name.ifBlank {
                        contact.phone
                    },
                fontSize = 14.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Text(
                text = contact.phone,
                fontSize = 11.sp,
                lineHeight = 12.sp,
                color =
                    MaterialTheme
                        .colorScheme
                        .onSurface
                        .copy(alpha = 0.52f),
                maxLines = 1
            )
        }

        Spacer(Modifier.width(5.dp))

        ModernSelectionBox(
            checked = checked,
            onClick = onToggle
        )
    }
}

@Composable
private fun ContactInitialAvatar(
    name: String,
    phone: String
) {
    Surface(
        modifier =
            Modifier.size(38.dp),
        shape = CircleShape,
        color =
            MaterialTheme
                .colorScheme
                .primary
                .copy(alpha = 0.10f)
    ) {
        Box(
            contentAlignment =
                Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Person,
                null,
                modifier =
                    Modifier.size(20.dp),
                tint =
                    MaterialTheme
                        .colorScheme
                        .primary
            )
        }
    }
}

@Composable
private fun ModernSelectionBox(
    checked: Boolean,
    onClick: () -> Unit
) {
    val shape =
        RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                AppPurpleLight,
                                AppPurpleDeep
                            )
                        )
                    )
                } else {
                    Modifier
                        .background(
                            MaterialTheme
                                .colorScheme
                                .surface
                        )
                        .border(
                            width = 1.2.dp,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .outline
                                    .copy(alpha = 0.34f),
                            shape = shape
                        )
                }
            )
            .clickable(onClick = onClick),
        contentAlignment =
            Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Outlined.Check,
                null,
                modifier =
                    Modifier.size(14.dp),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    selectedSimId: Int,
    onSelectedSimChanged: (Int) -> Unit,
    confirmBeforeSend: Boolean,
    onConfirmChanged: (Boolean) -> Unit,
    onAbout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sims by remember {
        mutableStateOf(emptyList<SimOption>())
    }

    var showSimDialog by remember {
        mutableStateOf(false)
    }

    fun loadSims(openDialog: Boolean) {
        scope.launch {
            sims = withContext(Dispatchers.IO) {
                SimRepository.load(context)
            }

            if (openDialog) {
                showSimDialog = true
            }
        }
    }

    val phonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                loadSims(openDialog = true)
            } else {
                Toast.makeText(
                    context,
                    "برای نمایش سیم‌کارت‌ها باید مجوز تلفن فعال باشد.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    LaunchedEffect(Unit) {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadSims(openDialog = false)
        }
    }

    fun openSimPicker() {
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadSims(openDialog = true)
        } else {
            phonePermissionLauncher.launch(
                Manifest.permission.READ_PHONE_STATE
            )
        }
    }

    val currentSimLabel =
        if (
            selectedSimId ==
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) {
            "پیش‌فرض سیستم"
        } else {
            sims.firstOrNull {
                it.subscriptionId == selectedSimId
            }?.let {
                "SIM ${it.slotIndex + 1} - ${it.label}"
            } ?: "سیم‌کارت انتخاب‌شده"
        }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = 18.dp,
                vertical = 10.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(14.dp),
        contentPadding =
            PaddingValues(bottom = 18.dp)
    ) {
        item {
            SettingsSectionCard(
                title = "تنظیمات عمومی"
            ) {
                SettingsOptionRow(
                    icon = Icons.Outlined.SimCard,
                    title = "سیم‌کارت پیش‌فرض",
                    subtitle = currentSimLabel,
                    onClick = {
                        openSimPicker()
                    }
                ) {
                    Icon(
                        Icons.Outlined.KeyboardArrowLeft,
                        null,
                        tint =
                            MaterialTheme.colorScheme.onSurface
                                .copy(alpha = 0.46f)
                    )
                }

                HorizontalDivider(
                    modifier =
                        Modifier.padding(horizontal = 16.dp),
                    color =
                        MaterialTheme.colorScheme.outlineVariant
                            .copy(alpha = 0.55f)
                )

                SettingsOptionRow(
                    icon = Icons.Outlined.Verified,
                    title = "تأیید قبل از ارسال",
                    subtitle = "نمایش پیش‌نمایش پیام پیش از ارسال",
                    onClick = {
                        onConfirmChanged(!confirmBeforeSend)
                    }
                ) {
                    Switch(
                        checked = confirmBeforeSend,
                        onCheckedChange = onConfirmChanged
                    )
                }
            }
        }

        item {
            SettingsSectionCard(
                title = "درباره برنامه"
            ) {
                SettingsOptionRow(
                    icon = Icons.Outlined.Info,
                    title = "پیامک طولانی",
                    subtitle = "نسخه 2.1",
                    onClick = onAbout
                ) {
                    Icon(
                        Icons.Outlined.KeyboardArrowLeft,
                        null,
                        tint =
                            MaterialTheme.colorScheme.onSurface
                                .copy(alpha = 0.46f)
                    )
                }
            }
        }

    }

    if (showSimDialog) {
        SimPickerDialog(
            sims = sims,
            selectedSimId = selectedSimId,
            onDismiss = {
                showSimDialog = false
            },
            onSelect = {
                onSelectedSimChanged(it)
                showSimDialog = false
            }
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: Dp = 44.dp,
    iconSize: Dp = 24.dp
) {
    val shape = CircleShape

    Box(
        modifier = Modifier
            .size(buttonSize)
            .shadow(
                elevation = 2.dp,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(
                if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
                    Color(0xFFFBFAFD)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color =
                    MaterialTheme.colorScheme.outlineVariant
                        .copy(alpha = 0.55f),
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AppBottomBar(
    page: Page,
    onPageSelected: (Page) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(
            modifier = Modifier.align(Alignment.TopCenter),
            color =
                MaterialTheme.colorScheme.outlineVariant
                    .copy(alpha = 0.42f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(76.dp)
                .padding(
                    horizontal = 12.dp,
                    vertical = 5.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppBottomItem(
                page = Page.SEND,
                currentPage = page,
                icon = Icons.Outlined.Send,
                label = "ارسال",
                modifier = Modifier.weight(1f),
                onClick = onPageSelected
            )

            AppBottomItem(
                page = Page.GROUPS,
                currentPage = page,
                icon = Icons.Outlined.Groups,
                label = "گروه‌ها",
                modifier = Modifier.weight(1f),
                onClick = onPageSelected
            )

            AppBottomItem(
                page = Page.SETTINGS,
                currentPage = page,
                icon = Icons.Outlined.Settings,
                label = "تنظیمات",
                modifier = Modifier.weight(1f),
                onClick = onPageSelected
            )
        }
    }
}

@Composable
private fun AppBottomItem(
    page: Page,
    currentPage: Page,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (Page) -> Unit
) {
    val selected = page == currentPage

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable {
                onClick(page)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = 66.dp,
                    height = 36.dp
                )
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (selected)
                        MaterialTheme.colorScheme.primary
                            .copy(alpha = 0.09f)
                    else
                        Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint =
                    if (selected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.72f)
            )
        }

        Spacer(Modifier.height(1.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight =
                if (selected)
                    FontWeight.SemiBold
                else
                    FontWeight.Normal,
            color =
                if (selected)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurface
                        .copy(alpha = 0.70f)
        )
    }
}

@Composable
private fun GroupListCard(
    group: ContactGroup,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp,
                    vertical = 12.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme.primary
                        .copy(alpha = 0.10f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Groups,
                        null,
                        tint =
                            MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    group.name,
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    "${group.members.size} مخاطب",
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color =
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.50f)
                )
            }

            Box {
                IconButton(
                    onClick = {
                        expanded = true
                    }
                ) {
                    Icon(
                        Icons.Outlined.MoreVert,
                        "گزینه‌ها"
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                    }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text("ویرایش")
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Edit,
                                null
                            )
                        },
                        onClick = {
                            expanded = false
                            onEdit()
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                "حذف",
                                color = AppDanger
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                null,
                                tint = AppDanger
                            )
                        },
                        onClick = {
                            expanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text(
                title,
                modifier = Modifier.padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 8.dp,
                    bottom = 4.dp
                ),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            content()
        }
    }
}

@Composable
private fun SettingsOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailingContent: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = 16.dp,
                vertical = 13.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(14.dp),
            color =
                MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.09f)
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint =
                        MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(1.dp))

            Text(
                subtitle,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color =
                    MaterialTheme.colorScheme.onSurface
                        .copy(alpha = 0.50f)
            )
        }

        Spacer(Modifier.width(8.dp))

        trailingContent()
    }
}

@Composable
private fun SimPickerDialog(
    sims: List<SimOption>,
    selectedSimId: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    var tempSelected by remember(selectedSimId) {
        mutableIntStateOf(selectedSimId)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        "انتخاب سیم‌کارت",
                        modifier = Modifier.weight(1f),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )

                    HeaderIconButton(
                        icon = Icons.Outlined.Close,
                        contentDescription = "بستن",
                        onClick = onDismiss
                    )
                }

                Spacer(Modifier.height(14.dp))

                SimChoiceRow(
                    title = "پیش‌فرض سیستم",
                    subtitle = "استفاده از سیم‌کارت پیش‌فرض گوشی",
                    selected =
                        tempSelected ==
                            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
                    onClick = {
                        tempSelected =
                            SubscriptionManager.INVALID_SUBSCRIPTION_ID
                    }
                )

                sims.forEach { sim ->
                    Spacer(Modifier.height(6.dp))

                    SimChoiceRow(
                        title = "SIM ${sim.slotIndex + 1}",
                        subtitle = sim.label,
                        selected =
                            tempSelected ==
                                sim.subscriptionId,
                        onClick = {
                            tempSelected =
                                sim.subscriptionId
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                PrimaryActionButton(
                    text = "انتخاب",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onSelect(tempSelected)
                    }
                )
            }
        }
    }
}

@Composable
private fun SimChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color =
            if (selected)
                MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant
                    .copy(alpha = 0.32f),
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                if (selected)
                    MaterialTheme.colorScheme.primary
                        .copy(alpha = 0.35f)
                else
                    MaterialTheme.colorScheme.outlineVariant
            )
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color =
                    MaterialTheme.colorScheme.primary
                        .copy(alpha = 0.10f)
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.SimCard,
                        null,
                        tint =
                            MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color =
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.50f)
                )
            }

            RadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .height(height)
            .alpha(if (enabled) 1f else 0.42f)
            .shadow(
                elevation = if (enabled) 4.dp else 0.dp,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color(0xFF8E3FFF),
                        Color(0xFF6720D7)
                    )
                )
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 56.dp
) {
    val shape = RoundedCornerShape(20.dp)

    Surface(
        modifier = modifier
            .height(height)
            .alpha(
                if (enabled)
                    1f
                else
                    0.42f
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border =
            androidx.compose.foundation.BorderStroke(
                1.2.dp,
                MaterialTheme.colorScheme.primary
                    .copy(alpha = 0.65f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.width(7.dp))
            }

            Text(
                text = text,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DangerActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape =
        RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(AppDanger)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment =
            Alignment.Center
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Delete,
                null,
                tint = Color.White
            )

            Spacer(
                Modifier.width(8.dp)
            )

            Text(
                text,
                color = Color.White,
                style =
                    MaterialTheme
                        .typography
                        .labelLarge
            )
        }
    }
}

@Composable
private fun RecipientModeSelector(
    selected: RecipientMode,
    onSelected: (RecipientMode) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(62.dp)
            .shadow(
                elevation = 3.dp,
                shape = shape,
                clip = false
            )
            .clip(shape)
            .background(
                if (MaterialTheme.colorScheme.background.luminance() > 0.5f)
                    Color(0xFFFBFAFD)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color =
                    MaterialTheme.colorScheme.outlineVariant
                        .copy(alpha = 0.28f),
                shape = shape
            )
            .padding(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RecipientModeItem(
                text = "مخاطب",
                icon = Icons.Outlined.Person,
                selected = selected == RecipientMode.PERSON,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSelected(RecipientMode.PERSON)
                }
            )

            RecipientModeItem(
                text = "گروه",
                icon = Icons.Outlined.Groups,
                selected = selected == RecipientMode.GROUP,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSelected(RecipientMode.GROUP)
                }
            )
        }
    }
}

@Composable
private fun RecipientModeItem(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF8E3FFF),
                                Color(0xFF6720D7)
                            )
                        )
                    )
                } else {
                    Modifier.background(Color.Transparent)
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(23.dp),
                tint =
                    if (selected)
                        Color.White
                    else
                        MaterialTheme.colorScheme.onSurface
                            .copy(alpha = 0.76f)
            )

            Spacer(Modifier.width(6.dp))

            Text(
                text = text,
                color =
                    if (selected)
                        Color.White
                    else
                        MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GroupPickerDialog(
    groups: List<ContactGroup>,
    selectedGroupId: String,
    onSelect: (ContactGroup) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.68f)
                .shadow(
                    18.dp,
                    RoundedCornerShape(24.dp)
                ),
            shape =
                RoundedCornerShape(24.dp),
            color =
                MaterialTheme
                    .colorScheme
                    .surface
        ) {
            Column(
                modifier =
                    Modifier.padding(18.dp)
            ) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Text(
                        "انتخاب گروه",
                        style =
                            MaterialTheme
                                .typography
                                .titleLarge,
                        modifier =
                            Modifier.weight(1f)
                    )

                    HeaderIconButton(
                        icon =
                            Icons.Outlined.Close,
                        contentDescription =
                            "بستن",
                        onClick =
                            onDismiss
                    )
                }

                Spacer(
                    Modifier.height(12.dp)
                )

                LazyColumn(
                    modifier =
                        Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    items(
                        groups,
                        key = { it.id }
                    ) { group ->

                        val selected =
                            selectedGroupId ==
                                group.id

                        val shape =
                            RoundedCornerShape(
                                18.dp
                            )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(
                                    if (selected)
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                            .copy(alpha = 0.12f)
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .surfaceVariant
                                            .copy(alpha = 0.45f)
                                )
                                .clickable {
                                    onSelect(group)
                                }
                                .padding(
                                    horizontal = 14.dp,
                                    vertical = 13.dp
                                ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier =
                                    Modifier.size(44.dp),
                                shape = CircleShape,
                                color =
                                    if (selected)
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .surfaceVariant
                            ) {
                                Box(
                                    contentAlignment =
                                        Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Groups,
                                        null,
                                        tint =
                                            if (selected)
                                                Color.White
                                            else
                                                MaterialTheme
                                                    .colorScheme
                                                    .onSurface
                                    )
                                }
                            }

                            Spacer(
                                Modifier.width(12.dp)
                            )

                            Column(
                                modifier =
                                    Modifier.weight(1f)
                            ) {
                                Text(
                                    group.name,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .titleMedium
                                )

                                Text(
                                    "${group.members.size} مخاطب",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface
                                            .copy(alpha = 0.6f)
                                )
                            }

                            RadioButton(
                                selected = selected,
                                onClick = {
                                    onSelect(group)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagePreviewDialog(
    recipientTitle: String,
    recipientCount: Int,
    message: String,
    onDismiss: () -> Unit,
    onSend: () -> Unit
) {
    val messageScroll =
        rememberScrollState()

    val timeText =
        remember {
            SimpleDateFormat(
                "HH:mm",
                Locale.getDefault()
            ).format(Date())
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .fillMaxHeight(0.86f)
                .shadow(
                    24.dp,
                    RoundedCornerShape(30.dp)
                ),
            shape =
                RoundedCornerShape(30.dp),
            color =
                MaterialTheme
                    .colorScheme
                    .surface
        ) {
            Column(
                modifier =
                    Modifier.fillMaxSize()
            ) {

                CompositionLocalProvider(
                    LocalLayoutDirection provides
                        LayoutDirection.Ltr
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                            .padding(
                                horizontal = 14.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        HeaderIconButton(
                            icon =
                                Icons.Outlined.Close,
                            contentDescription =
                                "بستن",
                            onClick =
                                onDismiss
                        )

                        Spacer(
                            Modifier.weight(1f)
                        )

                        Text(
                            "پیش‌نمایش پیام",
                            style =
                                MaterialTheme
                                    .typography
                                    .titleLarge,
                            textAlign =
                                TextAlign.Center
                        )

                        Spacer(
                            Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                        .copy(alpha = 0.12f)
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Send,
                                null,
                                tint =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color =
                        MaterialTheme
                            .colorScheme
                            .outlineVariant
                            .copy(alpha = 0.55f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 18.dp,
                            vertical = 14.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Surface(
                        modifier =
                            Modifier.size(54.dp),
                        shape = CircleShape,
                        color =
                            MaterialTheme
                                .colorScheme
                                .primary
                    ) {
                        Box(
                            contentAlignment =
                                Alignment.Center
                        ) {
                            Icon(
                                if (recipientCount > 1)
                                    Icons.Outlined.Groups
                                else
                                    Icons.Outlined.Person,
                                null,
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(
                        Modifier.width(12.dp)
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {
                        Text(
                            recipientTitle,
                            style =
                                MaterialTheme
                                    .typography
                                    .titleMedium
                        )

                        Spacer(
                            Modifier.height(2.dp)
                        )

                        Text(
                            if (recipientCount > 1)
                                "$recipientCount مخاطب"
                            else
                                "۱ مخاطب",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyMedium,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                                    .copy(alpha = 0.6f)
                        )
                    }
                }

                HorizontalDivider(
                    color =
                        MaterialTheme
                            .colorScheme
                            .outlineVariant
                            .copy(alpha = 0.45f)
                )

                Text(
                    text =
                        "امروز • $timeText",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 12.dp,
                            bottom = 10.dp
                        ),
                    textAlign =
                        TextAlign.Center,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = 0.58f)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp
                        )
                        .clip(
                            RoundedCornerShape(24.dp)
                        )
                        .background(
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                                .copy(alpha = 0.42f)
                        )
                        .padding(12.dp)
                ) {

                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .heightIn(
                                max = 380.dp
                            )
                            .align(
                                Alignment.TopEnd
                            )
                            .clip(
                                RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 8.dp,
                                    bottomEnd = 24.dp,
                                    bottomStart = 24.dp
                                )
                            )
                            .background(
                                if (
                                    MaterialTheme
                                        .colorScheme
                                        .surface
                                        .luminance() < 0.5f
                                )
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                        .copy(alpha = 0.20f)
                                else
                                    AppLavender
                            )
                            .border(
                                width = 1.dp,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                                        .copy(alpha = 0.14f),
                                shape =
                                    RoundedCornerShape(
                                        topStart = 24.dp,
                                        topEnd = 8.dp,
                                        bottomEnd = 24.dp,
                                        bottomStart = 24.dp
                                    )
                            )
                            .verticalScroll(
                                messageScroll
                            )
                            .padding(
                                horizontal = 18.dp,
                                vertical = 16.dp
                            )
                    ) {
                        Text(
                            text = message,
                            modifier =
                                Modifier.fillMaxWidth(),
                            textAlign =
                                TextAlign.Start,
                            style =
                                MaterialTheme
                                    .typography
                                    .bodyLarge,
                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurface
                        )

                        Spacer(
                            Modifier.height(12.dp)
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.End,
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.DoneAll,
                                null,
                                modifier =
                                    Modifier.size(18.dp),
                                tint =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                            )

                            Spacer(
                                Modifier.width(5.dp)
                            )

                            Text(
                                timeText,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(alpha = 0.56f)
                            )
                        }
                    }
                }

                Text(
                    text =
                        if (recipientCount > 1)
                            "این پیام به $recipientCount مخاطب ارسال خواهد شد."
                        else
                            "پیام برای این مخاطب ارسال خواهد شد.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 20.dp,
                            vertical = 12.dp
                        ),
                    textAlign =
                        TextAlign.Center,
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = 0.6f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            bottom = 18.dp
                        ),
                    horizontalArrangement =
                        Arrangement.spacedBy(10.dp)
                ) {
                    SecondaryActionButton(
                        text = "انصراف",
                        modifier =
                            Modifier.weight(1f),
                        onClick =
                            onDismiss
                    )

                    PrimaryActionButton(
                        text = "ارسال پیام",
                        modifier =
                            Modifier.weight(1.25f),
                        onClick =
                            onSend
                    )
                }
            }
        }
    }
}

@Composable
private fun DeleteGroupDialog(
    groupName: String,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest =
            onDismiss
    ) {
        Surface(
            shape =
                RoundedCornerShape(26.dp),
            color =
                MaterialTheme
                    .colorScheme
                    .surface
        ) {
            Column(
                modifier =
                    Modifier.padding(20.dp)
            ) {
                Surface(
                    modifier =
                        Modifier.size(52.dp),
                    shape = CircleShape,
                    color =
                        AppDanger.copy(
                            alpha = 0.12f
                        )
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Delete,
                            null,
                            tint = AppDanger
                        )
                    }
                }

                Spacer(
                    Modifier.height(14.dp)
                )

                Text(
                    "حذف گروه",
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    "گروه «$groupName» و اطلاعات ذخیره‌شده آن حذف شود؟",
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )

                Spacer(
                    Modifier.height(20.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {
                    SecondaryActionButton(
                        text = "انصراف",
                        modifier =
                            Modifier.weight(1f),
                        onClick =
                            onDismiss
                    )

                    DangerActionButton(
                        text = "حذف",
                        modifier =
                            Modifier.weight(1f),
                        onClick =
                            onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun SmsPermissionGuideDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier.size(60.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Message,
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "فعال‌کردن دسترسی پیامک",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "برای ارسال پیامک، برنامه به مجوز SMS نیاز دارد. این دسترسی فقط زمانی استفاده می‌شود که خودتان دکمه «ارسال پیام» را بزنید.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )

                Spacer(Modifier.height(14.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text(
                            text = "اگر گوشی دسترسی را محدود کرده:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "۱. روی «رفتن به تنظیمات» بزنید.
۲. در صفحه برنامه، منوی سه‌نقطه را باز کنید و «Allow restricted settings / اجازه تنظیمات محدود» را فعال کنید.
۳. وارد «Permissions / مجوزها» شوید.
۴. دسترسی «SMS» را روی «Allow / مجاز» قرار دهید.
۵. به برنامه برگردید و دوباره ارسال را بزنید.",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 23.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                PrimaryActionButton(
                    text = "رفتن به تنظیمات",
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Outlined.Settings,
                    onClick = onOpenSettings
                )

                Spacer(Modifier.height(10.dp))

                SecondaryActionButton(
                    text = "فعلاً نه",
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun AboutAppDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest =
            onDismiss
    ) {
        Surface(
            shape =
                RoundedCornerShape(24.dp),
            color =
                MaterialTheme
                    .colorScheme
                    .surface
        ) {
            Column(
                modifier =
                    Modifier.padding(22.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier =
                        Modifier.size(64.dp),
                    shape = CircleShape,
                    color =
                        MaterialTheme
                            .colorScheme
                            .primary
                            .copy(alpha = 0.12f)
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Message,
                            null,
                            modifier =
                                Modifier.size(32.dp),
                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )
                    }
                }

                Spacer(
                    Modifier.height(14.dp)
                )

                Text(
                    "پیامک طولانی",
                    style =
                        MaterialTheme
                            .typography
                            .titleLarge
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    "ارسال پیامک طولانی، ساخت گروه، ارسال گروهی، انتخاب سیم‌کارت و تم روشن و تاریک.",
                    textAlign =
                        TextAlign.Center,
                    style =
                        MaterialTheme
                            .typography
                            .bodyLarge
                )

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    "نسخه 2.1",
                    style =
                        MaterialTheme
                            .typography
                            .bodyMedium,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                            .copy(alpha = 0.55f)
                )

                Spacer(
                    Modifier.height(20.dp)
                )

                PrimaryActionButton(
                    text = "باشه",
                    modifier =
                        Modifier.fillMaxWidth(),
                    onClick =
                        onDismiss
                )
            }
        }
    }
}

private object ContactRepository {

    @SuppressLint(
        "Range",
        "MissingPermission"
    )
    fun load(
        context: Context
    ): List<GroupMember> {

        val result =
            mutableListOf<GroupMember>()

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
        )?.use { cursor ->

            val nameIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            val phoneIndex = cursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            while (cursor.moveToNext()) {
                if (phoneIndex < 0) continue

                val phone = cursor.getString(phoneIndex).orEmpty()

                val name =
                    if (nameIndex >= 0)
                        cursor.getString(nameIndex).orEmpty()
                    else
                        phone

                if (normalizePhone(phone).isNotBlank()) {
                    result.add(
                        GroupMember(
                            name = name,
                            phone = phone
                        )
                    )
                }
            }
        }

        return result
            .distinctBy {
                normalizePhone(it.phone)
            }
            .sortedBy {
                it.name
            }
    }
}

private object SimRepository {

    @SuppressLint("MissingPermission")
    fun load(
        context: Context
    ): List<SimOption> {

        val manager =
            context.getSystemService(
                SubscriptionManager::class.java
            )

        return manager
            .activeSubscriptionInfoList
            .orEmpty()
            .sortedBy {
                it.simSlotIndex
            }
            .map { info ->
                SimOption(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    label =
                        info.displayName
                            ?.toString()
                            ?.takeIf { it.isNotBlank() }
                            ?: "SIM ${info.simSlotIndex + 1}"
                )
            }
    }
}

private object LongSmsSender {

    fun send(
        context: Context,
        subscriptionId: Int,
        phone: String,
        message: String
    ) {
        val destination = normalizePhone(phone)

        require(destination.isNotBlank()) {
            "شماره گیرنده معتبر نیست."
        }

        require(message.isNotBlank()) {
            "متن پیام خالی است."
        }

        val smsManager = getSmsManager(
            context,
            subscriptionId
        )

        val parts = smsManager.divideMessage(message)

        if (parts.size > 1) {
            smsManager.sendMultipartTextMessage(
                destination,
                null,
                ArrayList(parts),
                null,
                null
            )
        } else {
            smsManager.sendTextMessage(
                destination,
                null,
                message,
                null,
                null
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun getSmsManager(
        context: Context,
        subscriptionId: Int
    ): SmsManager {
        val defaultManager =
            context.getSystemService(
                SmsManager::class.java
            )

        if (
            subscriptionId ==
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        ) {
            return defaultManager
        }

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {
            defaultManager.createForSubscriptionId(
                subscriptionId
            )
        } else {
            SmsManager.getSmsManagerForSubscriptionId(
                subscriptionId
            )
        }
    }
}

private fun normalizePhone(
    phone: String
): String {
    val converted = phone.map { char ->
        when (char) {
            '۰' -> '0'
            '۱' -> '1'
            '۲' -> '2'
            '۳' -> '3'
            '۴' -> '4'
            '۵' -> '5'
            '۶' -> '6'
            '۷' -> '7'
            '۸' -> '8'
            '۹' -> '9'

            '٠' -> '0'
            '١' -> '1'
            '٢' -> '2'
            '٣' -> '3'
            '٤' -> '4'
            '٥' -> '5'
            '٦' -> '6'
            '٧' -> '7'
            '٨' -> '8'
            '٩' -> '9'

            else -> char
        }
    }.joinToString("")

    return converted.filter {
        it.isDigit() || it == '+'
    }
}
