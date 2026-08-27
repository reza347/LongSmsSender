package com.longsms.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val smsPermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->

                if (granted) {
                    pendingSmsAction?.invoke()
                } else {
                    Toast.makeText(
                        this,
                        "برای ارسال پیامک باید مجوز SMS را فعال کنید.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                pendingSmsAction = null
            }

        setContent {
            SmsLongApp { action ->

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
            }
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
    requestSmsPermission: ((() -> Unit)) -> Unit
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
            background = Color(0xFF090A0F),
            surface = Color(0xFF15171D),
            surfaceVariant = Color(0xFF202228),
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        lightColorScheme(
            primary = AppPurple,
            background = Color(0xFFF7F7F9),
            surface = Color.White,
            surfaceVariant = Color(0xFFF1EFF7),
            onBackground = Color(0xFF161616),
            onSurface = Color(0xFF161616)
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

    MaterialTheme(colorScheme = colors, typography = AppTypography) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            Scaffold(
                topBar = {
                    AppHeader(
                        darkTheme = darkTheme,
                        onToggleTheme = {
                            darkTheme = !darkTheme
                            prefs.darkTheme = darkTheme
                        },
                        onAbout = { showAbout = true }
                    )
                },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = page == Page.SEND,
                            onClick = { page = Page.SEND },
                            icon = { Icon(Icons.Outlined.Send, null) },
                            label = { Text("ارسال") }
                        )

                        NavigationBarItem(
                            selected = page == Page.GROUPS,
                            onClick = { page = Page.GROUPS },
                            icon = { Icon(Icons.Outlined.Groups, null) },
                            label = { Text("گروه‌ها") }
                        )

                        NavigationBarItem(
                            selected = page == Page.SETTINGS,
                            onClick = { page = Page.SETTINGS },
                            icon = { Icon(Icons.Outlined.Settings, null) },
                            label = { Text("تنظیمات") }
                        )
                    }
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
                                onOpenGroups = { page = Page.GROUPS }
                            )
                        }

                        Page.GROUPS -> {
                            GroupsScreen(
                                groups = groups,
                                onSaveGroup = { group ->
                                    val index = groups.indexOfFirst { it.id == group.id }

                                    if (index >= 0) {
                                        groups[index] = group
                                    } else {
                                        groups.add(group)
                                    }

                                    prefs.saveGroups(groups)
                                },
                                onDeleteGroup = { group ->
                                    groups.removeAll { it.id == group.id }
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
                                }
                            )
                        }
                    }
                }
            }

            if (showAbout) {
                AboutAppDialog(
                    onDismiss = { showAbout = false }
                )
            }
        }
    }
}

@Composable
private fun AppHeader(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onAbout: () -> Unit
) {
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
            shadowElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderIconButton(
                    icon = if (darkTheme)
                        Icons.Outlined.LightMode
                    else
                        Icons.Outlined.DarkMode,
                    contentDescription = "تغییر تم",
                    onClick = onToggleTheme
                )

                Spacer(Modifier.width(8.dp))

                HeaderIconButton(
                    icon = Icons.Outlined.Info,
                    contentDescription = "توضیحات",
                    onClick = onAbout
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text = "پیامک طولانی",
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1
                )
            }
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

        // صفحه اصلی عمداً اسکرول ندارد.
        // ارتفاع کادر پیام با weight تنظیم می‌شود و خود TextField
        // در صورت طولانی شدن متن، اسکرول داخلی دارد.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 14.dp,
                    vertical = 8.dp
                )
        ) {

            RecipientModeSelector(
                selected = recipientMode,
                onSelected = {
                    recipientMode = it
                }
            )

            Spacer(
                Modifier.height(8.dp)
            )

            if (recipientMode == RecipientMode.PERSON) {

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surface
                        ),
                    elevation =
                        CardDefaults.elevatedCardElevation(
                            defaultElevation = 2.dp
                        )
                ) {

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 10.dp,
                                vertical = 8.dp
                            ),
                        label = {
                            Text(
                                if (contactName.isNotBlank())
                                    "گیرنده"
                                else
                                    "شماره گیرنده"
                            )
                        },
                        placeholder = {
                            Text("09123456789")
                        },
                        singleLine = true,
                        readOnly = contactName.isNotBlank(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Phone
                            ),
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Person,
                                null
                            )
                        },
                        trailingIcon = {
                            Row(
                                verticalAlignment =
                                    Alignment.CenterVertically
                            ) {
                                if (contactName.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            contactName = ""
                                            phone = ""
                                        }
                                    ) {
                                        Icon(
                                            Icons.Outlined.Close,
                                            "پاک کردن مخاطب"
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        contactPicker.launch(
                                            Intent(
                                                Intent.ACTION_PICK,
                                                ContactsContract
                                                    .CommonDataKinds
                                                    .Phone
                                                    .CONTENT_URI
                                            )
                                        )
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.Contacts,
                                        "انتخاب مخاطب"
                                    )
                                }
                            }
                        },
                        shape =
                            RoundedCornerShape(16.dp)
                    )
                }

            } else {

                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {

                            if (groups.isEmpty()) {
                                onOpenGroups()
                            } else {
                                showGroupPicker = true
                            }
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surface
                        ),
                    elevation =
                        CardDefaults.elevatedCardElevation(
                            defaultElevation = 2.dp
                        )
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 14.dp,
                                vertical = 10.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Surface(
                            modifier =
                                Modifier.size(42.dp),
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
                                    Icons.Outlined.Groups,
                                    null,
                                    tint =
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                )
                            }
                        }

                        Spacer(
                            Modifier.width(10.dp)
                        )

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                selectedGroup?.name
                                    ?: "انتخاب گروه",
                                style =
                                    MaterialTheme
                                        .typography
                                        .titleMedium
                            )

                            Text(
                                when {
                                    groups.isEmpty() ->
                                        "ابتدا یک گروه بسازید"

                                    selectedGroup != null ->
                                        "${selectedGroup.members.size} مخاطب"

                                    else ->
                                        "برای انتخاب لمس کنید"
                                },
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                                        .copy(alpha = 0.65f)
                            )
                        }

                        Icon(
                            Icons.Outlined.KeyboardArrowDown,
                            null
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(10.dp)
            )

            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors =
                    CardDefaults.elevatedCardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surface
                    ),
                elevation =
                    CardDefaults.elevatedCardElevation(
                        defaultElevation = 2.dp
                    )
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {

                    Text(
                        "متن پیام",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    // فقط این کادر هنگام طولانی شدن متن اسکرول می‌شود.
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
                                "متن طولانی خود را اینجا بنویسید..."
                            )
                        },
                        singleLine = false,
                        minLines = 1,
                        maxLines = Int.MAX_VALUE,
                        textStyle =
                            MaterialTheme
                                .typography
                                .bodyLarge,
                        shape =
                            RoundedCornerShape(18.dp)
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(
                        "${message.length} کاراکتر",
                        modifier =
                            Modifier.fillMaxWidth(),
                        textAlign =
                            TextAlign.End,
                        style =
                            MaterialTheme
                                .typography
                                .bodyMedium,
                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                                .copy(alpha = 0.65f)
                    )
                }
            }

            Spacer(
                Modifier.height(10.dp)
            )

            PrimaryActionButton(
                text = "ارسال پیام",
                icon = Icons.Outlined.Send,
                height = 54.dp,
                modifier =
                    Modifier.fillMaxWidth(),
                onClick = {

                    val targets =
                        targetNumbers()

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

            Spacer(
                Modifier.height(4.dp)
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier =
                Modifier.align(
                    Alignment.BottomCenter
                )
        )
    }

    if (showGroupPicker) {

        GroupPickerDialog(
            groups = groups,
            selectedGroupId =
                selectedGroupId,
            onSelect = {
                selectedGroupId =
                    it.id
                showGroupPicker =
                    false
            },
            onDismiss = {
                showGroupPicker =
                    false
            }
        )
    }

    if (showConfirm) {

        val targets =
            targetNumbers()

        MessagePreviewDialog(
            recipientTitle =
                if (
                    recipientMode ==
                    RecipientMode.GROUP
                )
                    "گروه: ${selectedGroup?.name.orEmpty()}"
                else
                    "گیرنده: ${contactName.ifBlank { phone }}",
            recipientCount =
                targets.size,
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
                val target = if (pendingCreate) {
                    ContactGroup(
                        id = UUID.randomUUID().toString(),
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
                    id = UUID.randomUUID().toString(),
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "گروه‌ها",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                PrimaryActionButton(
                    text = "گروه جدید",
                    icon = Icons.Outlined.GroupAdd,
                    modifier = Modifier.widthIn(min = 132.dp),
                    height = 46.dp,
                    onClick = { openEditor(null) }
                )
            }
        }

        if (groups.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 70.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.Groups,
                        null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(Modifier.height(14.dp))
                    Text("هنوز گروهی ساخته نشده است.")
                    Spacer(Modifier.height(14.dp))

                    SecondaryActionButton(
                        text = "ساخت اولین گروه",
                        icon = Icons.Outlined.GroupAdd,
                        modifier = Modifier.widthIn(min = 190.dp),
                        onClick = { openEditor(null) }
                    )
                }
            }
        } else {
            items(
                groups,
                key = { it.id }
            ) { group ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Groups,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )

                        Spacer(Modifier.width(12.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                group.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )

                            Text(
                                "${group.members.size} مخاطب",
                                fontSize = 12.sp
                            )
                        }

                        IconButton(
                            onClick = { openEditor(group) }
                        ) {
                            Icon(Icons.Outlined.Edit, "ویرایش")
                        }

                        IconButton(
                            onClick = { deleteGroup = group }
                        ) {
                            Icon(Icons.Outlined.Delete, "حذف")
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(10.dp))
        }
    }

    editingGroup?.let { group ->
        GroupEditorDialog(
            group = group,
            contacts = contacts,
            onDismiss = { editingGroup = null },
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

                    Spacer(Modifier.width(9.dp))

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

                Spacer(Modifier.height(10.dp))

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
                                    horizontal = 6.dp,
                                    vertical = 2.dp
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

                Spacer(Modifier.height(10.dp))

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
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )

            Spacer(Modifier.height(1.dp))

            Text(
                text = contact.phone,
                fontSize = 11.sp,
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
        RoundedCornerShape(12.dp)

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
                                    .copy(alpha = 0.32f),
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
