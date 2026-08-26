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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val AppPurple = Color(0xFF6D28D9)
private val AppPurpleLight = Color(0xFF8B5CF6)

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
        get() =
            prefs.getBoolean(
                "dark_theme",
                false
            )
        set(value) {
            prefs.edit()
                .putBoolean(
                    "dark_theme",
                    value
                )
                .apply()
        }

    var confirmBeforeSend: Boolean
        get() =
            prefs.getBoolean(
                "confirm_before_send",
                true
            )
        set(value) {
            prefs.edit()
                .putBoolean(
                    "confirm_before_send",
                    value
                )
                .apply()
        }

    var selectedSimId: Int
        get() =
            prefs.getInt(
                "selected_sim",
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            )
        set(value) {
            prefs.edit()
                .putInt(
                    "selected_sim",
                    value
                )
                .apply()
        }

    fun loadGroups(): List<ContactGroup> {

        return try {

            val groupsArray =
                JSONArray(
                    prefs.getString(
                        "groups",
                        "[]"
                    ) ?: "[]"
                )

            buildList {

                for (i in 0 until groupsArray.length()) {

                    val groupJson =
                        groupsArray.getJSONObject(i)

                    val membersArray =
                        groupJson.optJSONArray("members")
                            ?: JSONArray()

                    val members =
                        buildList {

                            for (
                                j in 0 until
                                    membersArray.length()
                            ) {

                                val member =
                                    membersArray
                                        .getJSONObject(j)

                                add(
                                    GroupMember(
                                        name =
                                            member
                                                .optString(
                                                    "name"
                                                ),
                                        phone =
                                            member
                                                .optString(
                                                    "phone"
                                                )
                                    )
                                )
                            }
                        }

                    add(
                        ContactGroup(
                            id =
                                groupJson
                                    .optString("id"),
                            name =
                                groupJson
                                    .optString("name"),
                            members =
                                members
                        )
                    )
                }
            }

        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveGroups(
        groups: List<ContactGroup>
    ) {

        val groupsArray = JSONArray()

        groups.forEach { group ->

            val groupJson = JSONObject()

            groupJson.put(
                "id",
                group.id
            )

            groupJson.put(
                "name",
                group.name
            )

            val membersArray =
                JSONArray()

            group.members.forEach { member ->

                val memberJson =
                    JSONObject()

                memberJson.put(
                    "name",
                    member.name
                )

                memberJson.put(
                    "phone",
                    member.phone
                )

                membersArray.put(
                    memberJson
                )
            }

            groupJson.put(
                "members",
                membersArray
            )

            groupsArray.put(
                groupJson
            )
        }

        prefs.edit()
            .putString(
                "groups",
                groupsArray.toString()
            )
            .apply()
    }
}

@Composable
private fun SmsLongApp(
    requestSmsPermission:
        ((() -> Unit)) -> Unit
) {

    val context =
        LocalContext.current

    val prefs =
        remember {
            AppPrefs(context)
        }

    var darkTheme by remember {
        mutableStateOf(
            prefs.darkTheme
        )
    }

    var confirmBeforeSend by remember {
        mutableStateOf(
            prefs.confirmBeforeSend
        )
    }

    var selectedSimId by remember {
        mutableIntStateOf(
            prefs.selectedSimId
        )
    }

    var page by rememberSaveable {
        mutableStateOf(
            Page.SEND
        )
    }

    var showAbout by remember {
        mutableStateOf(false)
    }

    val groups =
        remember {
            mutableStateListOf(
                *prefs
                    .loadGroups()
                    .toTypedArray()
            )
        }

    val colors =
        if (darkTheme) {

            darkColorScheme(
                primary =
                    AppPurpleLight,
                background =
                    Color(0xFF090A0F),
                surface =
                    Color(0xFF15171D),
                surfaceVariant =
                    Color(0xFF202228),
                onBackground =
                    Color.White,
                onSurface =
                    Color.White
            )

        } else {

            lightColorScheme(
                primary =
                    AppPurple,
                background =
                    Color(0xFFF7F7F9),
                surface =
                    Color.White,
                surfaceVariant =
                    Color(0xFFF1EFF7),
                onBackground =
                    Color(0xFF161616),
                onSurface =
                    Color(0xFF161616)
            )
        }

    val activity =
        context as? Activity

    SideEffect {

        activity?.let {

            it.window.statusBarColor =
                colors.background.toArgb()

            it.window.navigationBarColor =
                colors.background.toArgb()

            WindowCompat
                .getInsetsController(
                    it.window,
                    it.window.decorView
                )
                .apply {

                    isAppearanceLightStatusBars =
                        !darkTheme

                    isAppearanceLightNavigationBars =
                        !darkTheme
                }
        }
    }

    MaterialTheme(
        colorScheme = colors
    ) {

        CompositionLocalProvider(
            LocalLayoutDirection provides
                LayoutDirection.Rtl
        ) {

            Scaffold(

                topBar = {

                    AppHeader(
                        darkTheme =
                            darkTheme,

                        onToggleTheme = {

                            darkTheme =
                                !darkTheme

                            prefs.darkTheme =
                                darkTheme
                        },

                        onAbout = {
                            showAbout = true
                        }
                    )
                },

                bottomBar = {

                    NavigationBar {

                        NavigationBarItem(
                            selected =
                                page == Page.SEND,

                            onClick = {
                                page = Page.SEND
                            },

                            icon = {
                                Icon(
                                    Icons
                                        .Outlined
                                        .Send,
                                    null
                                )
                            },

                            label = {
                                Text("ارسال")
                            }
                        )

                        NavigationBarItem(
                            selected =
                                page == Page.GROUPS,

                            onClick = {
                                page =
                                    Page.GROUPS
                            },

                            icon = {
                                Icon(
                                    Icons
                                        .Outlined
                                        .Groups,
                                    null
                                )
                            },

                            label = {
                                Text("گروه‌ها")
                            }
                        )

                        NavigationBarItem(
                            selected =
                                page ==
                                    Page.SETTINGS,

                            onClick = {
                                page =
                                    Page.SETTINGS
                            },

                            icon = {
                                Icon(
                                    Icons
                                        .Outlined
                                        .Settings,
                                    null
                                )
                            },

                            label = {
                                Text("تنظیمات")
                            }
                        )
                    }
                }

            ) { padding ->

                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme
                                    .colorScheme
                                    .background
                            )
                            .padding(padding)
                ) {

                    when (page) {

                        Page.SEND -> {

                            SendScreen(
                                groups =
                                    groups,

                                selectedSimId =
                                    selectedSimId,

                                confirmBeforeSend =
                                    confirmBeforeSend,

                                requestSmsPermission =
                                    requestSmsPermission,

                                onOpenGroups = {
                                    page =
                                        Page.GROUPS
                                }
                            )
                        }

                        Page.GROUPS -> {

                            GroupsScreen(
                                groups =
                                    groups,

                                onSaveGroup = { group ->

                                    val index =
                                        groups
                                            .indexOfFirst {
                                                it.id ==
                                                    group.id
                                            }

                                    if (index >= 0) {
                                        groups[index] =
                                            group
                                    } else {
                                        groups.add(
                                            group
                                        )
                                    }

                                    prefs.saveGroups(
                                        groups
                                    )
                                },

                                onDeleteGroup = { group ->

                                    groups.removeAll {
                                        it.id ==
                                            group.id
                                    }

                                    prefs.saveGroups(
                                        groups
                                    )
                                }
                            )
                        }

                        Page.SETTINGS -> {

                            SettingsScreen(
                                selectedSimId =
                                    selectedSimId,

                                onSelectedSimChanged = {

                                    selectedSimId =
                                        it

                                    prefs.selectedSimId =
                                        it
                                },

                                confirmBeforeSend =
                                    confirmBeforeSend,

                                onConfirmChanged = {

                                    confirmBeforeSend =
                                        it

                                    prefs.confirmBeforeSend =
                                        it
                                }
                            )
                        }
                    }
                }
            }

            if (showAbout) {

                AlertDialog(
                    onDismissRequest = {
                        showAbout = false
                    },

                    icon = {

                        Icon(
                            Icons
                                .Outlined
                                .Info,
                            null
                        )
                    },

                    title = {
                        Text(
                            "پیامک طولانی"
                        )
                    },

                    text = {

                        Text(
                            "برنامه ارسال پیامک طولانی با قابلیت انتخاب مخاطب، ساخت گروه، ارسال گروهی، انتخاب سیم‌کارت و تم روشن و تاریک.\n\nنسخه 2.0"
                        )
                    },

                    confirmButton = {

                        TextButton(
                            onClick = {
                                showAbout =
                                    false
                            }
                        ) {
                            Text("باشه")
                        }
                    }
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
        LocalLayoutDirection provides
            LayoutDirection.Ltr
    ) {

        Surface(
            shadowElevation = 2.dp,
            color =
                MaterialTheme
                    .colorScheme
                    .surface
        ) {

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(
                            horizontal =
                                10.dp
                        )
            ) {

                Row(
                    modifier =
                        Modifier.align(
                            Alignment.CenterStart
                        )
                ) {

                    IconButton(
                        onClick =
                            onToggleTheme
                    ) {

                        Icon(
                            imageVector =
                                if (darkTheme)
                                    Icons
                                        .Outlined
                                        .LightMode
                                else
                                    Icons
                                        .Outlined
                                        .DarkMode,

                            contentDescription =
                                "تغییر تم"
                        )
                    }

                    IconButton(
                        onClick =
                            onAbout
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .Info,
                            "توضیحات"
                        )
                    }
                }

                Text(
                    text =
                        "پیامک طولانی",

                    modifier =
                        Modifier
                            .align(
                                Alignment.CenterEnd
                            )
                            .padding(
                                end = 6.dp
                            ),

                    textAlign =
                        TextAlign.End,

                    fontWeight =
                        FontWeight.Bold,

                    fontSize =
                        20.sp
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
    requestSmsPermission:
        ((() -> Unit)) -> Unit,
    onOpenGroups: () -> Unit
) {

    val context =
        LocalContext.current

    val snackbar =
        remember {
            SnackbarHostState()
        }

    val scope =
        rememberCoroutineScope()

    var recipientMode by rememberSaveable {
        mutableStateOf(
            RecipientMode.PERSON
        )
    }

    var phone by rememberSaveable {
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
            it.id ==
                selectedGroupId
        }

    val contactPicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .StartActivityForResult()
        ) { result ->

            if (
                result.resultCode ==
                Activity.RESULT_OK
            ) {

                result.data
                    ?.data
                    ?.let { uri ->

                        context
                            .contentResolver
                            .query(
                                uri,

                                arrayOf(
                                    ContactsContract
                                        .CommonDataKinds
                                        .Phone
                                        .NUMBER
                                ),

                                null,
                                null,
                                null
                            )
                            ?.use { cursor ->

                                if (
                                    cursor
                                        .moveToFirst()
                                ) {

                                    val index =
                                        cursor
                                            .getColumnIndex(
                                                ContactsContract
                                                    .CommonDataKinds
                                                    .Phone
                                                    .NUMBER
                                            )

                                    if (index >= 0) {

                                        phone =
                                            cursor
                                                .getString(
                                                    index
                                                )
                                                .orEmpty()
                                    }
                                }
                            }
                    }
            }
        }

    fun targetNumbers():
        List<String> {

        return when (
            recipientMode
        ) {

            RecipientMode.PERSON -> {

                listOf(
                    phone
                ).filter {
                    normalizePhone(it)
                        .isNotBlank()
                }
            }

            RecipientMode.GROUP -> {

                selectedGroup
                    ?.members
                    ?.map {
                        it.phone
                    }
                    ?.distinctBy {
                        normalizePhone(it)
                    }
                    .orEmpty()
            }
        }
    }

    fun sendNow() {

        val targets =
            targetNumbers()

        if (targets.isEmpty()) {

            scope.launch {

                snackbar.showSnackbar(
                    if (
                        recipientMode ==
                        RecipientMode.GROUP
                    )
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
                        context =
                            context,

                        subscriptionId =
                            selectedSimId,

                        phone =
                            number,

                        message =
                            message
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
                        e.message
                            ?: "ارسال پیام ناموفق بود."
                    )
                }
            }
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
    ) {

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal =
                            16.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp
                )
        ) {

            item {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                top = 8.dp
                            ),

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {

                    FilterChip(
                        selected =
                            recipientMode ==
                                RecipientMode.PERSON,

                        onClick = {
                            recipientMode =
                                RecipientMode.PERSON
                        },

                        label = {
                            Text("مخاطب")
                        },

                        leadingIcon = {

                            Icon(
                                Icons
                                    .Outlined
                                    .Person,
                                null
                            )
                        }
                    )

                    FilterChip(
                        selected =
                            recipientMode ==
                                RecipientMode.GROUP,

                        onClick = {
                            recipientMode =
                                RecipientMode.GROUP
                        },

                        label = {
                            Text("گروه")
                        },

                        leadingIcon = {

                            Icon(
                                Icons
                                    .Outlined
                                    .Groups,
                                null
                            )
                        }
                    )
                }
            }

            if (
                recipientMode ==
                RecipientMode.PERSON
            ) {

                item {

                    ElevatedCard(
                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(
                                18.dp
                            )
                    ) {

                        OutlinedTextField(
                            value =
                                phone,

                            onValueChange = {
                                phone = it
                            },

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        12.dp
                                    ),

                            label = {
                                Text(
                                    "شماره گیرنده"
                                )
                            },

                            placeholder = {
                                Text(
                                    "09123456789"
                                )
                            },

                            singleLine =
                                true,

                            keyboardOptions =
                                KeyboardOptions(
                                    keyboardType =
                                        KeyboardType.Phone
                                ),

                            leadingIcon = {

                                Icon(
                                    Icons
                                        .Outlined
                                        .Person,
                                    null
                                )
                            },

                            trailingIcon = {

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
                                        Icons
                                            .Outlined
                                            .Contacts,
                                        "انتخاب مخاطب"
                                    )
                                }
                            },

                            shape =
                                RoundedCornerShape(
                                    14.dp
                                )
                        )
                    }
                }

            } else {

                item {

                    ElevatedCard(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {

                                    if (
                                        groups.isEmpty()
                                    ) {
                                        onOpenGroups()
                                    } else {
                                        showGroupPicker =
                                            true
                                    }
                                },

                        shape =
                            RoundedCornerShape(
                                18.dp
                            )
                    ) {

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        18.dp
                                    ),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Icon(
                                Icons
                                    .Outlined
                                    .Groups,
                                null,

                                tint =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                            )

                            Spacer(
                                Modifier.width(
                                    12.dp
                                )
                            )

                            Column(
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {

                                Text(
                                    selectedGroup
                                        ?.name
                                        ?: "انتخاب گروه",

                                    fontWeight =
                                        FontWeight.Bold
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

                                    fontSize =
                                        12.sp
                                )
                            }

                            Icon(
                                Icons
                                    .Outlined
                                    .KeyboardArrowDown,
                                null
                            )
                        }
                    }
                }
            }

            item {

                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(
                            18.dp
                        )
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            )
                    ) {

                        Text(
                            "متن پیام",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        OutlinedTextField(
                            value =
                                message,

                            onValueChange = {
                                message = it
                            },

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min = 270.dp
                                    ),

                            placeholder = {

                                Text(
                                    "متن طولانی خود را اینجا بنویسید..."
                                )
                            },

                            minLines =
                                10,

                            maxLines =
                                20,

                            shape =
                                RoundedCornerShape(
                                    14.dp
                                )
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        Text(
                            "${message.length} کاراکتر",

                            modifier =
                                Modifier.fillMaxWidth(),

                            textAlign =
                                TextAlign.End,

                            fontSize =
                                13.sp
                        )
                    }
                }
            }

            item {

                Button(
                    onClick = {

                        val targets =
                            targetNumbers()

                        when {

                            targets.isEmpty() -> {

                                scope.launch {

                                    snackbar
                                        .showSnackbar(
                                            "گیرنده را انتخاب کنید."
                                        )
                                }
                            }

                            message.isBlank() -> {

                                scope.launch {

                                    snackbar
                                        .showSnackbar(
                                            "متن پیام را وارد کنید."
                                        )
                                }
                            }

                            confirmBeforeSend -> {
                                showConfirm =
                                    true
                            }

                            else -> {
                                sendNow()
                            }
                        }
                    },

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                58.dp
                            ),

                    shape =
                        RoundedCornerShape(
                            17.dp
                        ),

                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    AppPurple
                            )
                ) {

                    Icon(
                        Icons
                            .Outlined
                            .Send,
                        null
                    )

                    Spacer(
                        Modifier.width(
                            8.dp
                        )
                    )

                    Text(
                        "ارسال پیام",
                        fontSize =
                            17.sp,

                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }

            item {

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )
            }
        }

        SnackbarHost(
            hostState =
                snackbar,

            modifier =
                Modifier.align(
                    Alignment.BottomCenter
                )
        )
    }

    if (showGroupPicker) {

        AlertDialog(
            onDismissRequest = {
                showGroupPicker =
                    false
            },

            title = {
                Text(
                    "انتخاب گروه"
                )
            },

            text = {

                LazyColumn(
                    modifier =
                        Modifier.heightIn(
                            max = 400.dp
                        )
                ) {

                    items(
                        groups,
                        key = {
                            it.id
                        }
                    ) { group ->

                        ListItem(
                            headlineContent = {
                                Text(
                                    group.name
                                )
                            },

                            supportingContent = {

                                Text(
                                    "${group.members.size} مخاطب"
                                )
                            },

                            leadingContent = {

                                RadioButton(
                                    selected =
                                        selectedGroupId ==
                                            group.id,

                                    onClick = {

                                        selectedGroupId =
                                            group.id

                                        showGroupPicker =
                                            false
                                    }
                                )
                            },

                            modifier =
                                Modifier.clickable {

                                    selectedGroupId =
                                        group.id

                                    showGroupPicker =
                                        false
                                }
                        )
                    }
                }
            },

            confirmButton = {}
        )
    }

    if (showConfirm) {

        val targetCount =
            targetNumbers().size

        AlertDialog(
            onDismissRequest = {
                showConfirm =
                    false
            },

            icon = {

                Icon(
                    Icons
                        .Outlined
                        .Send,
                    null
                )
            },

            title = {
                Text(
                    "ارسال پیام؟"
                )
            },

            text = {

                Column {

                    Text(
                        if (
                            recipientMode ==
                            RecipientMode.GROUP
                        )
                            "گروه: ${selectedGroup?.name.orEmpty()}"
                        else
                            "گیرنده: $phone"
                    )

                    if (targetCount > 1) {

                        Spacer(
                            Modifier.height(
                                6.dp
                            )
                        )

                        Text(
                            "$targetCount مخاطب"
                        )
                    }

                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    Text(
                        message,
                        maxLines = 8
                    )
                }
            },

            confirmButton = {

                Button(
                    onClick = {

                        showConfirm =
                            false

                        sendNow()
                    }
                ) {
                    Text("ارسال")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        showConfirm =
                            false
                    }
                ) {
                    Text("انصراف")
                }
            }
        )
    }
}

@Composable
private fun GroupsScreen(
    groups: List<ContactGroup>,
    onSaveGroup:
        (ContactGroup) -> Unit,
    onDeleteGroup:
        (ContactGroup) -> Unit
) {

    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var contacts by remember {
        mutableStateOf(
            emptyList<GroupMember>()
        )
    }

    var editingGroup by remember {
        mutableStateOf<ContactGroup?>(
            null
        )
    }

    var deleteGroup by remember {
        mutableStateOf<ContactGroup?>(
            null
        )
    }

    var pendingEdit by remember {
        mutableStateOf<ContactGroup?>(
            null
        )
    }

    var pendingCreate by remember {
        mutableStateOf(false)
    }

    fun loadContactsAndEdit(
        group: ContactGroup
    ) {

        scope.launch {

            contacts =
                withContext(
                    Dispatchers.IO
                ) {

                    ContactRepository
                        .load(context)
                }

            editingGroup =
                group
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .RequestPermission()
        ) { granted ->

            if (granted) {

                val target =
                    if (pendingCreate) {

                        ContactGroup(
                            id =
                                UUID
                                    .randomUUID()
                                    .toString(),

                            name = "",

                            members =
                                emptyList()
                        )

                    } else {
                        pendingEdit
                    }

                if (target != null) {

                    loadContactsAndEdit(
                        target
                    )
                }

            } else {

                Toast.makeText(
                    context,
                    "برای ساخت گروه باید مجوز مخاطبین فعال باشد.",
                    Toast.LENGTH_LONG
                ).show()
            }

            pendingCreate =
                false

            pendingEdit =
                null
        }

    fun openEditor(
        group: ContactGroup?
    ) {

        if (
            ContextCompat
                .checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            loadContactsAndEdit(
                group
                    ?: ContactGroup(
                        id =
                            UUID
                                .randomUUID()
                                .toString(),

                        name = "",

                        members =
                            emptyList()
                    )
            )

        } else {

            pendingCreate =
                group == null

            pendingEdit =
                group

            permissionLauncher.launch(
                Manifest.permission.READ_CONTACTS
            )
        }
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal =
                        16.dp
                ),

        verticalArrangement =
            Arrangement.spacedBy(
                12.dp
            )
    ) {

        item {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 10.dp
                        ),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    "گروه‌ها",
                    fontSize =
                        22.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                Button(
                    onClick = {
                        openEditor(null)
                    }
                ) {

                    Icon(
                        Icons
                            .Outlined
                            .GroupAdd,
                        null
                    )

                    Spacer(
                        Modifier.width(
                            6.dp
                        )
                    )

                    Text(
                        "گروه جدید"
                    )
                }
            }
        }

        if (groups.isEmpty()) {

            item {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical =
                                    70.dp
                            ),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Icon(
                        Icons
                            .Outlined
                            .Groups,
                        null,

                        modifier =
                            Modifier.size(
                                72.dp
                            ),

                        tint =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )

                    Spacer(
                        Modifier.height(
                            14.dp
                        )
                    )

                    Text(
                        "هنوز گروهی ساخته نشده است."
                    )

                    Spacer(
                        Modifier.height(
                            14.dp
                        )
                    )

                    OutlinedButton(
                        onClick = {
                            openEditor(null)
                        }
                    ) {

                        Text(
                            "ساخت اولین گروه"
                        )
                    }
                }
            }

        } else {

            items(
                groups,
                key = {
                    it.id
                }
            ) { group ->

                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(
                            18.dp
                        )
                ) {

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    16.dp
                                ),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .Groups,
                            null,

                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )

                        Spacer(
                            Modifier.width(
                                12.dp
                            )
                        )

                        Column(
                            modifier =
                                Modifier.weight(
                                    1f
                                )
                        ) {

                            Text(
                                group.name,

                                fontWeight =
                                    FontWeight.Bold,

                                fontSize =
                                    17.sp
                            )

                            Text(
                                "${group.members.size} مخاطب",

                                fontSize =
                                    12.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                openEditor(
                                    group
                                )
                            }
                        ) {

                            Icon(
                                Icons
                                    .Outlined
                                    .Edit,
                                "ویرایش"
                            )
                        }

                        IconButton(
                            onClick = {
                                deleteGroup =
                                    group
                            }
                        ) {

                            Icon(
                                Icons
                                    .Outlined
                                    .Delete,
                                "حذف"
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(
                Modifier.height(
                    10.dp
                )
            )
        }
    }

    editingGroup?.let { group ->

        GroupEditorDialog(
            group =
                group,

            contacts =
                contacts,

            onDismiss = {
                editingGroup =
                    null
            },

            onSave = {

                onSaveGroup(it)

                editingGroup =
                    null
            }
        )
    }

    deleteGroup?.let { group ->

        AlertDialog(
            onDismissRequest = {
                deleteGroup =
                    null
            },

            title = {
                Text(
                    "حذف گروه"
                )
            },

            text = {
                Text(
                    "گروه «${group.name}» حذف شود؟"
                )
            },

            confirmButton = {

                Button(
                    onClick = {

                        onDeleteGroup(
                            group
                        )

                        deleteGroup =
                            null
                    }
                ) {
                    Text("حذف")
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {
                        deleteGroup =
                            null
                    }
                ) {
                    Text("انصراف")
                }
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

    var name by remember(
        group.id
    ) {
        mutableStateOf(
            group.name
        )
    }

    var search by remember {
        mutableStateOf("")
    }

    val selected =
        remember(
            group.id
        ) {

            mutableStateListOf<String>()
                .apply {

                    addAll(
                        group.members.map {
                            normalizePhone(
                                it.phone
                            )
                        }
                    )
                }
        }

    val allContacts =
        remember(
            contacts,
            group.members
        ) {

            (contacts +
                group.members)
                .distinctBy {
                    normalizePhone(
                        it.phone
                    )
                }
        }

    val filtered =
        remember(
            search,
            allContacts
        ) {

            if (
                search.isBlank()
            ) {
                allContacts
            } else {

                allContacts.filter {

                    it.name.contains(
                        search,
                        ignoreCase =
                            true
                    ) ||
                        it.phone.contains(
                            search
                        )
                }
            }
        }

    Dialog(
        onDismissRequest =
            onDismiss
    ) {

        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(
                        0.90f
                    ),

            shape =
                RoundedCornerShape(
                    24.dp
                ),

            color =
                MaterialTheme
                    .colorScheme
                    .surface
        ) {

            Column(
                modifier =
                    Modifier.padding(
                        16.dp
                    )
            ) {

                Text(
                    if (
                        group.name.isBlank()
                    )
                        "ساخت گروه"
                    else
                        "ویرایش گروه",

                    fontSize =
                        20.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                OutlinedTextField(
                    value =
                        name,

                    onValueChange = {
                        name = it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    label = {
                        Text(
                            "نام گروه"
                        )
                    },

                    singleLine =
                        true
                )

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                OutlinedTextField(
                    value =
                        search,

                    onValueChange = {
                        search = it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    placeholder = {
                        Text(
                            "جستجوی مخاطب"
                        )
                    },

                    leadingIcon = {

                        Icon(
                            Icons
                                .Outlined
                                .Search,
                            null
                        )
                    },

                    singleLine =
                        true
                )

                Spacer(
                    Modifier.height(
                        8.dp
                    )
                )

                Text(
                    "${selected.size} مخاطب انتخاب شده",

                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        6.dp
                    )
                )

                LazyColumn(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    items(
                        filtered,
                        key = {
                            normalizePhone(
                                it.phone
                            )
                        }
                    ) { contact ->

                        val key =
                            normalizePhone(
                                contact.phone
                            )

                        val checked =
                            selected.contains(
                                key
                            )

                        ListItem(
                            headlineContent = {

                                Text(
                                    contact.name
                                        .ifBlank {
                                            contact.phone
                                        }
                                )
                            },

                            supportingContent = {

                                Text(
                                    contact.phone
                                )
                            },

                            leadingContent = {

                                Checkbox(
                                    checked =
                                        checked,

                                    onCheckedChange = { value ->

                                        if (value) {

                                            if (
                                                !selected
                                                    .contains(
                                                        key
                                                    )
                                            ) {
                                                selected.add(
                                                    key
                                                )
                                            }

                                        } else {

                                            selected.remove(
                                                key
                                            )
                                        }
                                    }
                                )
                            },

                            modifier =
                                Modifier.clickable {

                                    if (checked) {
                                        selected.remove(
                                            key
                                        )
                                    } else {
                                        selected.add(
                                            key
                                        )
                                    }
                                }
                        )
                    }
                }

                Spacer(
                    Modifier.height(
                        10.dp
                    )
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(
                            10.dp
                        )
                ) {

                    OutlinedButton(
                        onClick =
                            onDismiss,

                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text("انصراف")
                    }

                    Button(
                        enabled =
                            name.isNotBlank() &&
                                selected.isNotEmpty(),

                        onClick = {

                            val map =
                                allContacts
                                    .associateBy {
                                        normalizePhone(
                                            it.phone
                                        )
                                    }

                            val members =
                                selected
                                    .mapNotNull {
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
                        },

                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {

                        Text("ذخیره")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    selectedSimId: Int,
    onSelectedSimChanged:
        (Int) -> Unit,
    confirmBeforeSend: Boolean,
    onConfirmChanged:
        (Boolean) -> Unit
) {

    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    var sims by remember {
        mutableStateOf(
            emptyList<SimOption>()
        )
    }

    var showSimDialog by remember {
        mutableStateOf(false)
    }

    fun loadSims(
        openDialog: Boolean
    ) {

        scope.launch {

            sims =
                withContext(
                    Dispatchers.IO
                ) {

                    SimRepository
                        .load(context)
                }

            if (openDialog) {
                showSimDialog =
                    true
            }
        }
    }

    val phonePermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts
                .RequestPermission()
        ) { granted ->

            if (granted) {

                loadSims(
                    openDialog =
                        true
                )

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
            ContextCompat
                .checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            loadSims(
                openDialog =
                    false
            )
        }
    }

    fun openSimPicker() {

        if (
            ContextCompat
                .checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) ==
            PackageManager.PERMISSION_GRANTED
        ) {

            loadSims(
                openDialog =
                    true
            )

        } else {

            phonePermissionLauncher.launch(
                Manifest.permission.READ_PHONE_STATE
            )
        }
    }

    val currentSimLabel =

        if (
            selectedSimId ==
            SubscriptionManager
                .INVALID_SUBSCRIPTION_ID
        ) {

            "پیش‌فرض سیستم"

        } else {

            sims.firstOrNull {
                it.subscriptionId ==
                    selectedSimId
            }?.let {
                "SIM ${it.slotIndex + 1} - ${it.label}"
            } ?: "سیم‌کارت انتخاب‌شده"
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal =
                        16.dp
                ),

        verticalArrangement =
            Arrangement.spacedBy(
                14.dp
            )
    ) {

        item {

            Text(
                "تنظیمات",
                modifier =
                    Modifier.padding(
                        top = 12.dp
                    ),
                fontSize =
                    22.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }

        item {

            ElevatedCard(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            openSimPicker()
                        },

                shape =
                    RoundedCornerShape(
                        18.dp
                    )
            ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                18.dp
                            ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        Icons
                            .Outlined
                            .SimCard,
                        null,

                        tint =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )

                    Spacer(
                        Modifier.width(
                            12.dp
                        )
                    )

                    Column(
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {

                        Text(
                            "سیم‌کارت پیش‌فرض",

                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            currentSimLabel,

                            fontSize =
                                12.sp
                        )
                    }

                    Icon(
                        Icons
                            .Outlined
                            .KeyboardArrowDown,
                        null
                    )
                }
            }
        }

        item {

            ElevatedCard(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(
                        18.dp
                    )
            ) {

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                18.dp
                            ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Column(
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {

                        Text(
                            "تأیید قبل از ارسال",

                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "قبل از ارسال، گیرنده و متن نمایش داده شود.",

                            fontSize =
                                12.sp
                        )
                    }

                    Switch(
                        checked =
                            confirmBeforeSend,

                        onCheckedChange =
                            onConfirmChanged
                    )
                }
            }
        }

        item {

            ElevatedCard(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(
                        18.dp
                    )
            ) {

                Row(
                    modifier =
                        Modifier.padding(
                            18.dp
                        ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        Icons
                            .Outlined
                            .Info,
                        null,

                        tint =
                            MaterialTheme
                                .colorScheme
                                .primary
                    )

                    Spacer(
                        Modifier.width(
                            12.dp
                        )
                    )

                    Column {

                        Text(
                            "پیامک طولانی",

                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "نسخه 2.0",

                            fontSize =
                                12.sp
                        )
                    }
                }
            }
        }
    }

    if (showSimDialog) {

        AlertDialog(
            onDismissRequest = {
                showSimDialog =
                    false
            },

            title = {
                Text(
                    "انتخاب سیم‌کارت"
                )
            },

            text = {

                LazyColumn(
                    modifier =
                        Modifier.heightIn(
                            max = 400.dp
                        )
                ) {

                    item {

                        ListItem(
                            headlineContent = {

                                Text(
                                    "پیش‌فرض سیستم"
                                )
                            },

                            leadingContent = {

                                RadioButton(
                                    selected =
                                        selectedSimId ==
                                            SubscriptionManager
                                                .INVALID_SUBSCRIPTION_ID,

                                    onClick = {

                                        onSelectedSimChanged(
                                            SubscriptionManager
                                                .INVALID_SUBSCRIPTION_ID
                                        )

                                        showSimDialog =
                                            false
                                    }
                                )
                            },

                            modifier =
                                Modifier.clickable {

                                    onSelectedSimChanged(
                                        SubscriptionManager
                                            .INVALID_SUBSCRIPTION_ID
                                    )

                                    showSimDialog =
                                        false
                                }
                        )
                    }

                    items(
                        sims,
                        key = {
                            it.subscriptionId
                        }
                    ) { sim ->

                        ListItem(
                            headlineContent = {

                                Text(
                                    "SIM ${sim.slotIndex + 1}"
                                )
                            },

                            supportingContent = {

                                Text(
                                    sim.label
                                )
                            },

                            leadingContent = {

                                RadioButton(
                                    selected =
                                        selectedSimId ==
                                            sim.subscriptionId,

                                    onClick = {

                                        onSelectedSimChanged(
                                            sim.subscriptionId
                                        )

                                        showSimDialog =
                                            false
                                    }
                                )
                            },

                            modifier =
                                Modifier.clickable {

                                    onSelectedSimChanged(
                                        sim.subscriptionId
                                    )

                                    showSimDialog =
                                        false
                                }
                        )
                    }
                }
            },

            confirmButton = {}
        )
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

        context.contentResolver
            .query(
                ContactsContract
                    .CommonDataKinds
                    .Phone
                    .CONTENT_URI,

                arrayOf(
                    ContactsContract
                        .CommonDataKinds
                        .Phone
                        .DISPLAY_NAME,

                    ContactsContract
                        .CommonDataKinds
                        .Phone
                        .NUMBER
                ),

                null,
                null,

                ContactsContract
                    .CommonDataKinds
                    .Phone
                    .DISPLAY_NAME +
                    " ASC"
            )
            ?.use { cursor ->

                val nameIndex =
                    cursor.getColumnIndex(
                        ContactsContract
                            .CommonDataKinds
                            .Phone
                            .DISPLAY_NAME
                    )

                val phoneIndex =
                    cursor.getColumnIndex(
                        ContactsContract
                            .CommonDataKinds
                            .Phone
                            .NUMBER
                    )

                while (
                    cursor.moveToNext()
                ) {

                    if (
                        phoneIndex < 0
                    ) continue

                    val phone =
                        cursor
                            .getString(
                                phoneIndex
                            )
                            .orEmpty()

                    val name =
                        if (
                            nameIndex >= 0
                        )
                            cursor
                                .getString(
                                    nameIndex
                                )
                                .orEmpty()
                        else
                            phone

                    if (
                        normalizePhone(
                            phone
                        ).isNotBlank()
                    ) {

                        result.add(
                            GroupMember(
                                name =
                                    name,

                                phone =
                                    phone
                            )
                        )
                    }
                }
            }

        return result
            .distinctBy {
                normalizePhone(
                    it.phone
                )
            }
            .sortedBy {
                it.name
            }
    }
}

private object SimRepository {

    @SuppressLint(
        "MissingPermission"
    )
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
                    subscriptionId =
                        info.subscriptionId,

                    slotIndex =
                        info.simSlotIndex,

                    label =
                        info.displayName
                            ?.toString()
                            ?.takeIf {
                                it.isNotBlank()
                            }
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

        val destination =
            normalizePhone(
                phone
            )

        require(
            destination.isNotBlank()
        ) {
            "شماره گیرنده معتبر نیست."
        }

        require(
            message.isNotBlank()
        ) {
            "متن پیام خالی است."
        }

        val smsManager =
            getSmsManager(
                context,
                subscriptionId
            )

        val parts =
            smsManager
                .divideMessage(
                    message
                )

        if (
            parts.size > 1
        ) {

            smsManager
                .sendMultipartTextMessage(
                    destination,
                    null,
                    ArrayList(parts),
                    null,
                    null
                )

        } else {

            smsManager
                .sendTextMessage(
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
            SubscriptionManager
                .INVALID_SUBSCRIPTION_ID
        ) {
            return defaultManager
        }

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S
        ) {

            defaultManager
                .createForSubscriptionId(
                    subscriptionId
                )

        } else {

            SmsManager
                .getSmsManagerForSubscriptionId(
                    subscriptionId
                )
        }
    }
}

private fun normalizePhone(
    phone: String
): String {

    val converted =
        phone.map { char ->

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

                else ->
                    char
            }
        }
        .joinToString("")

    return converted
        .filter {
            it.isDigit() ||
                it == '+'
        }
}
