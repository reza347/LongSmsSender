package com.longsms.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Purple = Color(0xFF6D28D9)
private val PurpleLight = Color(0xFF8B5CF6)
private val Green = Color(0xFF22C55E)

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
                        "برای ارسال پیام باید مجوز SMS فعال باشد.",
                        Toast.LENGTH_LONG
                    ).show()
                }

                pendingSmsAction = null
            }

        setContent {

            LongSmsApp { action ->

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
    HISTORY,
    SETTINGS
}

private data class HistoryItem(
    val id: String,
    val phone: String,
    val message: String,
    val time: Long
)

private class AppPrefs(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "long_sms_sender",
            Context.MODE_PRIVATE
        )

    var darkTheme: Boolean
        get() = prefs.getBoolean(
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

    var saveHistory: Boolean
        get() = prefs.getBoolean(
            "save_history",
            true
        )
        set(value) {
            prefs.edit()
                .putBoolean(
                    "save_history",
                    value
                )
                .apply()
        }

    var confirmBeforeSend: Boolean
        get() = prefs.getBoolean(
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

    fun loadHistory(): List<HistoryItem> {

        return try {

            val json =
                prefs.getString(
                    "history",
                    "[]"
                ) ?: "[]"

            val array = JSONArray(json)

            buildList {

                for (i in 0 until array.length()) {

                    val item =
                        array.getJSONObject(i)

                    add(
                        HistoryItem(
                            id =
                                item.optString("id"),
                            phone =
                                item.optString("phone"),
                            message =
                                item.optString("message"),
                            time =
                                item.optLong("time")
                        )
                    )
                }
            }

        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveHistory(
        items: List<HistoryItem>
    ) {

        val array = JSONArray()

        items.take(100).forEach { item ->

            val json = JSONObject()

            json.put(
                "id",
                item.id
            )

            json.put(
                "phone",
                item.phone
            )

            json.put(
                "message",
                item.message
            )

            json.put(
                "time",
                item.time
            )

            array.put(json)
        }

        prefs.edit()
            .putString(
                "history",
                array.toString()
            )
            .apply()
    }
}

@Composable
private fun LongSmsApp(
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

    var saveHistory by remember {
        mutableStateOf(
            prefs.saveHistory
        )
    }

    var confirmBeforeSend by remember {
        mutableStateOf(
            prefs.confirmBeforeSend
        )
    }

    var page by rememberSaveable {
        mutableStateOf(
            Page.SEND
        )
    }

    val history =
        remember {
            mutableStateListOf(
                *prefs
                    .loadHistory()
                    .toTypedArray()
            )
        }

    val colors =
        if (darkTheme) {

            darkColorScheme(
                primary = PurpleLight,
                secondary = Green,
                background = Color(0xFF090A0F),
                surface = Color(0xFF15171C),
                surfaceVariant = Color(0xFF202228),
                onBackground = Color.White,
                onSurface = Color.White
            )

        } else {

            lightColorScheme(
                primary = Purple,
                secondary = Green,
                background = Color(0xFFF6F6F8),
                surface = Color.White,
                surfaceVariant = Color(0xFFF0F0F4),
                onBackground = Color(0xFF151515),
                onSurface = Color(0xFF151515)
            )
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

                    Surface(
                        color =
                            MaterialTheme
                                .colorScheme
                                .background
                    ) {

                        Text(
                            text =
                                when (page) {

                                    Page.SEND ->
                                        "ارسال SMS طولانی"

                                    Page.HISTORY ->
                                        "تاریخچه پیام‌ها"

                                    Page.SETTINGS ->
                                        "تنظیمات"
                                },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 20.dp,
                                        vertical = 18.dp
                                    ),
                            fontSize = 22.sp,
                            fontWeight =
                                FontWeight.Bold
                        )
                    }
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
                                    Icons.Outlined.Send,
                                    null
                                )
                            },
                            label = {
                                Text("ارسال")
                            }
                        )

                        NavigationBarItem(
                            selected =
                                page == Page.HISTORY,
                            onClick = {
                                page = Page.HISTORY
                            },
                            icon = {
                                Icon(
                                    Icons.Outlined.History,
                                    null
                                )
                            },
                            label = {
                                Text("تاریخچه")
                            }
                        )

                        NavigationBarItem(
                            selected =
                                page == Page.SETTINGS,
                            onClick = {
                                page = Page.SETTINGS
                            },
                            icon = {
                                Icon(
                                    Icons.Outlined.Settings,
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
                                saveHistory =
                                    saveHistory,

                                confirmBeforeSend =
                                    confirmBeforeSend,

                                requestSmsPermission =
                                    requestSmsPermission,

                                onSent = { item ->

                                    history.add(
                                        0,
                                        item
                                    )

                                    while (
                                        history.size > 100
                                    ) {
                                        history.removeAt(
                                            history.lastIndex
                                        )
                                    }

                                    prefs.saveHistory(
                                        history
                                    )
                                }
                            )
                        }

                        Page.HISTORY -> {

                            HistoryScreen(
                                history = history,
                                onClear = {

                                    history.clear()

                                    prefs.saveHistory(
                                        history
                                    )
                                }
                            )
                        }

                        Page.SETTINGS -> {

                            SettingsScreen(

                                darkTheme =
                                    darkTheme,

                                onDarkThemeChanged = {

                                    darkTheme = it

                                    prefs.darkTheme =
                                        it
                                },

                                saveHistory =
                                    saveHistory,

                                onSaveHistoryChanged = {

                                    saveHistory = it

                                    prefs.saveHistory =
                                        it
                                },

                                confirmBeforeSend =
                                    confirmBeforeSend,

                                onConfirmBeforeSendChanged = {

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
        }
    }
}

@Composable
private fun SendScreen(
    saveHistory: Boolean,
    confirmBeforeSend: Boolean,
    requestSmsPermission:
        ((() -> Unit)) -> Unit,
    onSent: (HistoryItem) -> Unit
) {

    val context =
        LocalContext.current

    val snackbar =
        remember {
            SnackbarHostState()
        }

    val scope =
        rememberCoroutineScope()

    var phone by rememberSaveable {
        mutableStateOf("")
    }

    var message by rememberSaveable {
        mutableStateOf("")
    }

    var showConfirm by remember {
        mutableStateOf(false)
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

                val uri =
                    result.data?.data

                if (uri != null) {

                    context.contentResolver
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
                                cursor.moveToFirst()
                            ) {

                                val index =
                                    cursor.getColumnIndex(
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

    fun sendMessage() {

        if (phone.trim().isBlank()) {

            scope.launch {

                snackbar.showSnackbar(
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

                LongSmsSender.send(
                    phone = phone,
                    message = message
                )

                if (saveHistory) {

                    onSent(
                        HistoryItem(
                            id =
                                UUID
                                    .randomUUID()
                                    .toString(),

                            phone =
                                phone.trim(),

                            message =
                                message,

                            time =
                                System
                                    .currentTimeMillis()
                        )
                    )
                }

                scope.launch {

                    snackbar.showSnackbar(
                        "پیام با موفقیت ارسال شد."
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
                        horizontal = 16.dp
                    ),
            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp
                )
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
                        value = phone,

                        onValueChange = {
                            phone = it
                        },

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(12.dp),

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

                        singleLine = true,

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

                                    val intent =
                                        Intent(
                                            Intent.ACTION_PICK,
                                            ContactsContract
                                                .CommonDataKinds
                                                .Phone
                                                .CONTENT_URI
                                        )

                                    contactPicker.launch(
                                        intent
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

                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    KeyboardType.Phone
                            ),

                        shape =
                            RoundedCornerShape(
                                14.dp
                            )
                    )
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
                            value = message,

                            onValueChange = {
                                message = it
                            },

                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min = 260.dp
                                    ),

                            placeholder = {

                                Text(
                                    "متن طولانی خود را اینجا بنویسید..."
                                )
                            },

                            minLines = 10,
                            maxLines = 20,

                            shape =
                                RoundedCornerShape(
                                    14.dp
                                )
                        )

                        Spacer(
                            Modifier.height(
                                10.dp
                            )
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement
                                    .SpaceBetween
                        ) {

                            Text(
                                "${message.length} کاراکتر",
                                fontSize = 13.sp
                            )

                            Text(
                                "پیام طولانی",
                                fontSize = 13.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color = Green
                            )
                        }
                    }
                }
            }

            item {

                ElevatedCard(
                    colors =
                        CardDefaults
                            .elevatedCardColors(
                                containerColor =
                                    Green.copy(
                                        alpha = 0.10f
                                    )
                            ),

                    shape =
                        RoundedCornerShape(
                            18.dp
                        )
                ) {

                    Row(
                        modifier =
                            Modifier.padding(
                                16.dp
                            ),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .Message,
                            null,
                            tint = Green
                        )

                        Spacer(
                            Modifier.width(
                                10.dp
                            )
                        )

                        Text(
                            "متن بلند به‌صورت پیام SMS چندبخشی استاندارد ارسال می‌شود و در گوشی گیرنده معمولاً به شکل یک پیام بلند نمایش داده می‌شود.",
                            fontSize = 13.sp
                        )
                    }
                }
            }

            item {

                Button(
                    onClick = {

                        if (
                            phone.isBlank()
                        ) {

                            scope.launch {

                                snackbar.showSnackbar(
                                    "شماره گیرنده را وارد کنید."
                                )
                            }

                        } else if (
                            message.isBlank()
                        ) {

                            scope.launch {

                                snackbar.showSnackbar(
                                    "متن پیام را وارد کنید."
                                )
                            }

                        } else if (
                            confirmBeforeSend
                        ) {

                            showConfirm = true

                        } else {

                            sendMessage()
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
                            16.dp
                        ),

                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    Purple
                            )
                ) {

                    Icon(
                        Icons.Outlined.Send,
                        null
                    )

                    Spacer(
                        Modifier.width(
                            8.dp
                        )
                    )

                    Text(
                        "ارسال پیام",
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }

            item {

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,

            modifier =
                Modifier.align(
                    Alignment.BottomCenter
                )
        )
    }

    if (showConfirm) {

        AlertDialog(

            onDismissRequest = {
                showConfirm = false
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
                        "گیرنده:"
                    )

                    Text(
                        phone,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    Text(
                        "متن پیام:"
                    )

                    Spacer(
                        Modifier.height(
                            4.dp
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

                        sendMessage()
                    }
                ) {

                    Text(
                        "ارسال"
                    )
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {

                        showConfirm =
                            false
                    }
                ) {

                    Text(
                        "انصراف"
                    )
                }
            }
        )
    }
}

@Composable
private fun HistoryScreen(
    history: List<HistoryItem>,
    onClear: () -> Unit
) {

    var confirmClear by remember {
        mutableStateOf(false)
    }

    if (history.isEmpty()) {

        Box(
            modifier =
                Modifier.fillMaxSize(),

            contentAlignment =
                Alignment.Center
        ) {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Icon(
                    Icons
                        .Outlined
                        .History,
                    null,

                    modifier =
                        Modifier.size(
                            68.dp
                        )
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Text(
                    "هنوز پیامی ارسال نشده است."
                )
            }
        }

    } else {

        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 16.dp
                    ),

            verticalArrangement =
                Arrangement.spacedBy(
                    10.dp
                )
        ) {

            item {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text(
                        "${history.size} پیام",
                        fontWeight =
                            FontWeight.Bold
                    )

                    TextButton(
                        onClick = {
                            confirmClear = true
                        }
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .DeleteSweep,
                            null
                        )

                        Spacer(
                            Modifier.width(
                                4.dp
                            )
                        )

                        Text(
                            "پاک کردن"
                        )
                    }
                }
            }

            items(
                history,
                key = {
                    it.id
                }
            ) { item ->

                ElevatedCard(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(
                            16.dp
                        )
                ) {

                    Column(
                        modifier =
                            Modifier.padding(
                                14.dp
                            )
                    ) {

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),

                            horizontalArrangement =
                                Arrangement
                                    .SpaceBetween
                        ) {

                            Text(
                                item.phone,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Icon(
                                Icons
                                    .Outlined
                                    .CheckCircle,
                                null,
                                tint = Green
                            )
                        }

                        Spacer(
                            Modifier.height(
                                6.dp
                            )
                        )

                        Text(
                            item.message,
                            maxLines = 5
                        )

                        Spacer(
                            Modifier.height(
                                10.dp
                            )
                        )

                        Text(
                            formatDate(
                                item.time
                            ),
                            fontSize = 12.sp,
                            color = Green
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {

        AlertDialog(

            onDismissRequest = {
                confirmClear = false
            },

            title = {

                Text(
                    "پاک کردن تاریخچه؟"
                )
            },

            text = {

                Text(
                    "تمام پیام‌های ذخیره شده پاک خواهند شد."
                )
            },

            confirmButton = {

                Button(
                    onClick = {

                        confirmClear =
                            false

                        onClear()
                    }
                ) {

                    Text(
                        "پاک کردن"
                    )
                }
            },

            dismissButton = {

                TextButton(
                    onClick = {

                        confirmClear =
                            false
                    }
                ) {

                    Text(
                        "انصراف"
                    )
                }
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    darkTheme: Boolean,
    onDarkThemeChanged:
        (Boolean) -> Unit,

    saveHistory: Boolean,
    onSaveHistoryChanged:
        (Boolean) -> Unit,

    confirmBeforeSend: Boolean,
    onConfirmBeforeSendChanged:
        (Boolean) -> Unit
) {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 16.dp
                ),

        verticalArrangement =
            Arrangement.spacedBy(
                14.dp
            )
    ) {

        item {

            Text(
                "انتخاب تم",
                fontSize = 18.sp,
                fontWeight =
                    FontWeight.Bold
            )

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

                if (!darkTheme) {

                    Button(
                        onClick = {
                            onDarkThemeChanged(
                                false
                            )
                        },

                        modifier =
                            Modifier
                                .weight(1f)
                                .height(70.dp)
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .LightMode,
                            null
                        )

                        Spacer(
                            Modifier.width(
                                6.dp
                            )
                        )

                        Text(
                            "روشن"
                        )
                    }

                } else {

                    OutlinedButton(
                        onClick = {
                            onDarkThemeChanged(
                                false
                            )
                        },

                        modifier =
                            Modifier
                                .weight(1f)
                                .height(70.dp)
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .LightMode,
                            null
                        )

                        Spacer(
                            Modifier.width(
                                6.dp
                            )
                        )

                        Text(
                            "روشن"
                        )
                    }
                }

                if (darkTheme) {

                    Button(
                        onClick = {
                            onDarkThemeChanged(
                                true
                            )
                        },

                        modifier =
                            Modifier
                                .weight(1f)
                                .height(70.dp)
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .DarkMode,
                            null
                        )

                        Spacer(
                            Modifier.width(
                                6.dp
                            )
                        )

                        Text(
                            "تاریک"
                        )
                    }

                } else {

                    OutlinedButton(
                        onClick = {
                            onDarkThemeChanged(
                                true
                            )
                        },

                        modifier =
                            Modifier
                                .weight(1f)
                                .height(70.dp)
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .DarkMode,
                            null
                        )

                        Spacer(
                            Modifier.width(
                                6.dp
                            )
                        )

                        Text(
                            "تاریک"
                        )
                    }
                }
            }
        }

        item {

            SettingCard(
                title =
                    "ذخیره تاریخچه",

                subtitle =
                    "پیام‌های ارسال شده داخل برنامه ذخیره شوند.",

                checked =
                    saveHistory,

                onCheckedChange =
                    onSaveHistoryChanged
            )
        }

        item {

            SettingCard(
                title =
                    "تأیید قبل از ارسال",

                subtitle =
                    "قبل از ارسال، متن و شماره گیرنده نمایش داده شود.",

                checked =
                    confirmBeforeSend,

                onCheckedChange =
                    onConfirmBeforeSendChanged
            )
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
                            16.dp
                        ),

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        Icons
                            .Outlined
                            .Message,
                        null,
                        tint = PurpleLight
                    )

                    Spacer(
                        Modifier.width(
                            10.dp
                        )
                    )

                    Column {

                        Text(
                            "Long SMS Sender",
                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            "نسخه 1.1",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange:
        (Boolean) -> Unit
) {

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

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    title,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(
                        4.dp
                    )
                )

                Text(
                    subtitle,
                    fontSize = 12.sp
                )
            }

            Switch(
                checked = checked,
                onCheckedChange =
                    onCheckedChange
            )
        }
    }
}

private fun formatDate(
    time: Long
): String {

    return SimpleDateFormat(
        "yyyy/MM/dd - HH:mm",
        Locale("fa", "IR")
    ).format(
        Date(time)
    )
}

private object LongSmsSender {

    @Suppress("DEPRECATION")
    fun send(
        phone: String,
        message: String
    ) {

        val destination =
            phone.filter {
                it.isDigit() ||
                    it == '+'
            }

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
            SmsManager.getDefault()

        val parts =
            smsManager.divideMessage(
                message
            )

        if (parts.size > 1) {

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
}
