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

private val Purple = Color(0xFF7C3AED)
private val PurpleDark = Color(0xFF6D28D9)
private val PurpleLight = Color(0xFF9B6CFF)
private val SuccessGreen = Color(0xFF22C55E)

class MainActivity : ComponentActivity() {

    private var pendingSmsAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher =
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

                    permissionLauncher.launch(
                        Manifest.permission.SEND_SMS
                    )
                }
            }
        }
    }
}

private enum class AppPage {
    SEND,
    HISTORY,
    SETTINGS
}

private data class HistoryItem(
    val id: String,
    val phone: String,
    val message: String,
    val parts: Int,
    val time: Long
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
            prefs.edit()
                .putBoolean("dark_theme", value)
                .apply()
        }

    var saveHistory: Boolean
        get() = prefs.getBoolean("save_history", true)
        set(value) {
            prefs.edit()
                .putBoolean("save_history", value)
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

            val array =
                JSONArray(
                    prefs.getString(
                        "history",
                        "[]"
                    ) ?: "[]"
                )

            buildList {

                for (i in 0 until array.length()) {

                    val obj =
                        array.getJSONObject(i)

                    add(
                        HistoryItem(
                            id = obj.optString("id"),
                            phone = obj.optString("phone"),
                            message = obj.optString("message"),
                            parts = obj.optInt("parts", 1),
                            time = obj.optLong("time")
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

            val obj = JSONObject()

            obj.put("id", item.id)
            obj.put("phone", item.phone)
            obj.put("message", item.message)
            obj.put("parts", item.parts)
            obj.put("time", item.time)

            array.put(obj)
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
    requestSmsPermissionAndRun:
        ((() -> Unit)) -> Unit
) {

    val context = LocalContext.current

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
            AppPage.SEND
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
                secondary = SuccessGreen,
                background = Color(0xFF090B10),
                surface = Color(0xFF15181D),
                surfaceVariant = Color(0xFF20242A),
                onBackground = Color.White,
                onSurface = Color.White
            )

        } else {

            lightColorScheme(
                primary = PurpleDark,
                secondary = SuccessGreen,
                background = Color(0xFFF7F7FA),
                surface = Color.White,
                surfaceVariant = Color(0xFFF0F0F5),
                onBackground = Color(0xFF151518),
                onSurface = Color(0xFF151518)
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

                                    AppPage.SEND ->
                                        "ارسال SMS طولانی"

                                    AppPage.HISTORY ->
                                        "تاریخچه پیام‌ها"

                                    AppPage.SETTINGS ->
                                        "تنظیمات"
                                },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 20.dp,
                                        vertical = 18.dp
                                    ),
                            fontWeight =
                                FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                },

                bottomBar = {

                    NavigationBar {

                        NavigationBarItem(
                            selected =
                                page == AppPage.SEND,
                            onClick = {
                                page =
                                    AppPage.SEND
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
                                page ==
                                    AppPage.HISTORY,
                            onClick = {
                                page =
                                    AppPage.HISTORY
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
                                page ==
                                    AppPage.SETTINGS,
                            onClick = {
                                page =
                                    AppPage.SETTINGS
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

                        AppPage.SEND -> {

                            SendScreen(
                                saveHistory =
                                    saveHistory,
                                confirmBeforeSend =
                                    confirmBeforeSend,
                                onSent = {

                                    history.add(
                                        0,
                                        it
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
                                },
                                requestSmsPermissionAndRun =
                                    requestSmsPermissionAndRun
                            )
                        }

                        AppPage.HISTORY -> {

                            HistoryScreen(
                                history =
                                    history,
                                onClear = {

                                    history.clear()

                                    prefs.saveHistory(
                                        history
                                    )
                                }
                            )
                        }

                        AppPage.SETTINGS -> {

                            SettingsScreen(
                                darkTheme =
                                    darkTheme,
                                onDarkThemeChanged = {

                                    darkTheme = it
                                    prefs.darkTheme = it
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
    onSent: (HistoryItem) -> Unit,
    requestSmsPermissionAndRun:
        ((() -> Unit)) -> Unit
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

    var showPreview by remember {
        mutableStateOf(false)
    }

    val parts =
        remember(message) {
            SmsSender.divideMessage(
                message
            )
        }

    val partCount =
        if (message.isBlank())
            0
        else
            parts.size.coerceAtLeast(1)

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
                                    cursor.moveToFirst()
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

    fun sendNow() {

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

        requestSmsPermissionAndRun {

            try {

                val sentParts =
                    SmsSender.send(
                        phone,
                        message
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
                            parts =
                                sentParts,
                            time =
                                System
                                    .currentTimeMillis()
                        )
                    )
                }

                scope.launch {

                    snackbar.showSnackbar(
                        "پیام $sentParts بخشی ارسال شد."
                    )
                }

            } catch (e: Exception) {

                scope.launch {

                    snackbar.showSnackbar(
                        e.message
                            ?: "ارسال ناموفق بود."
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
                                "مثلاً 09123456789"
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

                                    contactPicker
                                        .launch(
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
                                        min = 220.dp
                                    ),
                            placeholder = {

                                Text(
                                    "پیام طولانی خود را اینجا بنویسید..."
                                )
                            },
                            minLines = 8,
                            maxLines = 16,
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
                                "$partCount بخش SMS",
                                fontWeight =
                                    FontWeight.Bold,
                                color =
                                    if (
                                        partCount > 0
                                    )
                                        SuccessGreen
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .onSurface
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
                                16.dp
                            )
                    ) {

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Icon(
                                Icons
                                    .Outlined
                                    .Message,
                                null,
                                tint =
                                    PurpleLight
                            )

                            Spacer(
                                Modifier.width(
                                    8.dp
                                )
                            )

                            Text(
                                "تعداد پیام‌های ارسالی: $partCount",
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Spacer(
                            Modifier.height(
                                14.dp
                            )
                        )

                        Row(
                            modifier =
                                Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    6.dp
                                )
                        ) {

                            repeat(6) { index ->

                                Box(
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .height(
                                                7.dp
                                            )
                                            .background(
                                                if (
                                                    index <
                                                    partCount
                                                        .coerceAtMost(
                                                            6
                                                        )
                                                )
                                                    SuccessGreen
                                                else
                                                    MaterialTheme
                                                        .colorScheme
                                                        .onSurface
                                                        .copy(
                                                            alpha =
                                                                0.12f
                                                        ),
                                                RoundedCornerShape(
                                                    50.dp
                                                )
                                            )
                                )
                            }
                        }

                        if (partCount > 6) {

                            Spacer(
                                Modifier.height(
                                    8.dp
                                )
                            )

                            Text(
                                "+ ${partCount - 6} بخش دیگر",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {

                Button(
                    onClick = {

                        if (
                            confirmBeforeSend &&
                            phone.isNotBlank() &&
                            message.isNotBlank()
                        ) {

                            showPreview =
                                true

                        } else {

                            sendNow()
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
                                    PurpleDark
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

                ElevatedCard(
                    colors =
                        CardDefaults
                            .elevatedCardColors(
                                containerColor =
                                    SuccessGreen
                                        .copy(
                                            alpha =
                                                0.10f
                                        )
                            )
                ) {

                    Row(
                        modifier =
                            Modifier.padding(
                                14.dp
                            )
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .Info,
                            null,
                            tint =
                                SuccessGreen
                        )

                        Spacer(
                            Modifier.width(
                                10.dp
                            )
                        )

                        Text(
                            "پیام طولانی به‌صورت خودکار به چند SMS تقسیم می‌شود. ممکن است اپراتور هزینه هر بخش را جداگانه حساب کند.",
                            fontSize = 13.sp
                        )
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

        SnackbarHost(
            hostState = snackbar,
            modifier =
                Modifier.align(
                    Alignment.BottomCenter
                )
        )
    }

    if (showPreview) {

        AlertDialog(
            onDismissRequest = {
                showPreview = false
            },
            icon = {

                Icon(
                    Icons
                        .Outlined
                        .Preview,
                    null
                )
            },
            title = {

                Text(
                    "پیش‌نمایش ارسال"
                )
            },
            text = {

                Column {

                    Text(
                        "گیرنده: $phone"
                    )

                    Spacer(
                        Modifier.height(
                            6.dp
                        )
                    )

                    Text(
                        "تعداد بخش‌ها: $partCount"
                    )

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

                        showPreview =
                            false

                        sendNow()
                    }
                ) {

                    Text(
                        "ارسال ($partCount)"
                    )
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {

                        showPreview =
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
                            70.dp
                        )
                )

                Spacer(
                    Modifier.height(
                        12.dp
                    )
                )

                Text(
                    "هنوز پیامی در تاریخچه نیست."
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
                        "${history.size} پیام ذخیره شده",
                        fontWeight =
                            FontWeight.Bold
                    )

                    TextButton(
                        onClick = {
                            confirmClear =
                                true
                        }
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .DeleteSweep,
                            null
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

                        Text(
                            item.phone,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            Modifier.height(
                                5.dp
                            )
                        )

                        Text(
                            item.message,
                            maxLines = 4
                        )

                        Spacer(
                            Modifier.height(
                                8.dp
                            )
                        )

                        Text(
                            "${item.parts} بخش • ${
                                formatDate(
                                    item.time
                                )
                            }",
                            fontSize = 12.sp,
                            color =
                                SuccessGreen
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {

        AlertDialog(
            onDismissRequest = {
                confirmClear =
                    false
            },
            title = {
                Text(
                    "پاک کردن تاریخچه؟"
                )
            },
            text = {
                Text(
                    "تمام پیام‌های ذخیره‌شده پاک می‌شوند."
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
                "تم برنامه",
                fontWeight =
                    FontWeight.Bold,
                fontSize = 18.sp
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
                                .height(70.dp),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        PurpleDark
                                )
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .Brightness7,
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
                                .Brightness7,
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
                                .height(70.dp),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        PurpleDark
                                )
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
                    "پیام‌های ارسال‌شده در برنامه ذخیره شوند.",
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
                    "قبل از ارسال تعداد بخش‌های SMS نمایش داده شود.",
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

                Column(
                    modifier =
                        Modifier.padding(
                            16.dp
                        )
                ) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            Icons
                                .Outlined
                                .Shield,
                            null,
                            tint =
                                SuccessGreen
                        )

                        Spacer(
                            Modifier.width(
                                8.dp
                            )
                        )

                        Text(
                            "ارسال پیام",
                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Spacer(
                        Modifier.height(
                            8.dp
                        )
                    )

                    Text(
                        "پیام‌ها با سیستم SMS خود اندروید و سیم‌کارت گوشی ارسال می‌شوند."
                    )
                }
            }
        }

        item {

            ElevatedCard(
                modifier =
                    Modifier.fillMaxWidth()
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
                            .Info,
                        null,
                        tint =
                            PurpleLight
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
                            "نسخه 1.0",
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

                Text(
                    subtitle,
                    fontSize = 12.sp
                )
            }

            Switch(
                checked =
                    checked,
                onCheckedChange =
                    onCheckedChange
            )
        }
    }
}

private fun formatDate(
    time: Long
): String {

    if (time <= 0) return ""

    return SimpleDateFormat(
        "yyyy/MM/dd HH:mm",
        Locale("fa", "IR")
    ).format(
        Date(time)
    )
}

private object SmsSender {

    @Suppress("DEPRECATION")
    fun divideMessage(
        message: String
    ): List<String> {

        if (message.isBlank()) {
            return emptyList()
        }

        return try {

            SmsManager
                .getDefault()
                .divideMessage(
                    message
                )

        } catch (_: Exception) {

            listOf(message)
        }
    }

    @Suppress("DEPRECATION")
    fun send(
        phone: String,
        message: String
    ): Int {

        val destination =
            phone.filter {
                it.isDigit() ||
                    it == '+'
            }

        require(
            destination.isNotBlank()
        ) {
            "شماره معتبر نیست."
        }

        require(
            message.isNotBlank()
        ) {
            "متن پیام خالی است."
        }

        val manager =
            SmsManager.getDefault()

        val parts =
            manager.divideMessage(
                message
            )

        if (parts.size <= 1) {

            manager.sendTextMessage(
                destination,
                null,
                message,
                null,
                null
            )

            return 1
        }

        manager
            .sendMultipartTextMessage(
                destination,
                null,
                ArrayList(parts),
                null,
                null
            )

        return parts.size
    }
}
