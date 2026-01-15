#!/usr/bin/env bash
set -euo pipefail

echo "Creating hatotyper files..."

# root
mkdir -p .github/workflows
cat > settings.gradle <<'EOF'
rootProject.name = "hatotyper"
include ":app"
EOF

cat > build.gradle <<'EOF'
// Root build.gradle (Groovy)
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath "com.android.tools.build:gradle:7.4.2"
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0"
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
EOF

cat > .github/workflows/android-debug-build.yml <<'EOF'
name: Build debug APK

on:
  workflow_dispatch:
  push:
    branches:
      - main

jobs:
  build:
    name: Build debug APK
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Set up JDK 11
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '11'

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: ${{ runner.os }}-gradle-${{ hashFiles('**/gradle-wrapper.properties') }}
          restore-keys: |
            ${{ runner.os }}-gradle-

      - name: Assemble debug APK
        run: ./gradlew clean assembleDebug --no-daemon

      - name: Upload debug APK artifact
        uses: actions/upload-artifact@v3
        with:
          name: hatotyper-debug-apk
          path: app/build/outputs/apk/debug/*.apk
EOF

# app module
mkdir -p app
cat > app/build.gradle <<'EOF'
plugins {
    id 'com.android.application'
    id 'kotlin-android'
}

android {
    compileSdk 33

    defaultConfig {
        applicationId "com.hatori.hatotyper"
        minSdk 24
        targetSdk 33
        versionCode 1
        versionName "0.1"
    }

    signingConfigs {
        release {
            // For release signing, uncomment and configure.
            // storeFile file("keystore/hatotyper.jks")
            // storePassword "<store-password>"
            // keyAlias "hatotyper-key"
            // keyPassword "<key-password>"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix ".debug"
            versionNameSuffix "-debug"
            debuggable true
            buildConfigField "boolean", "ENABLE_VERBOSE_LOG", "true"
        }
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
            signingConfig signingConfigs.release
            buildConfigField "boolean", "ENABLE_VERBOSE_LOG", "false"
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.8.0"
    implementation 'com.google.mlkit:text-recognition:16.1.0'
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.core:core-ktx:1.9.0'
    implementation 'com.google.android.material:material:1.9.0'
    implementation 'androidx.recyclerview:recyclerview:1.3.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
}
EOF

# AndroidManifest and xml
mkdir -p app/src/main
cat > app/src/main/AndroidManifest.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest package="com.hatori.hatotyper"
    xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

    <application
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true">

        <activity android:name=".MainActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity android:name=".CalibrationActivity" />
        <activity android:name=".MappingListActivity" />

        <service
            android:name=".ScreenCaptureService"
            android:exported="false" />

        <service
            android:name=".MyAccessibilityService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="true">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

    </application>
</manifest>
EOF

mkdir -p app/src/main/res/xml
cat > app/src/main/res/xml/accessibility_service_config.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowContentChanged|typeViewClicked|typeViewFocused|typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:settingsActivity="com.hatori.hatotyper.MainActivity"
/>
EOF

# res values and layouts
mkdir -p app/src/main/res/values
cat > app/src/main/res/values/strings.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">hatotyper</string>

    <!-- MainActivity -->
    <string name="title_main">hatotyper</string>
    <string name="hint_target">検出ワード (ターゲット)</string>
    <string name="hint_input">入力する文字列</string>
    <string name="btn_start_capture">画面キャプチャ開始 (MediaProjection)</string>
    <string name="btn_calibration">キャリブレーション（キーボード座標登録）</string>
    <string name="btn_guide_acc">アクセシビリティ / オーバーレイ許可の案内</string>
    <string name="btn_test_input">テスト入力</string>
    <string name="btn_manage_mappings">マッピング管理</string>
    <string name="notice_permissions">注意: Accessibility・オーバーレイ・画面キャプチャの許可が必要です。</string>

    <!-- Guide / Permission -->
    <string name="guide_acc_title">アクセシビリティを有効にしてください</string>
    <string name="guide_acc_message">設定 → アクセシビリティ → hatotyper を有効にしてください。\n\nまたキャリブレーション（キーボードのキー位置登録）をするために「他のアプリの上に表示」許可が必要です。</string>
    <string name="btn_open_accessibility_settings">アクセシビリティ設定へ</string>
    <string name="btn_open_overlay_settings">オーバーレイ許可</string>

    <!-- Calibration -->
    <string name="title_calibration">キャリブレーション</string>
    <string name="calib_info">手順:\n1) 設定→他のアプリの上に表示 を許可\n2) キーボードを表示してから「オーバーレイでキャリブレーション開始」を押す\n3) 登録したいキーをタップ→文字を入力して保存</string>
    <string name="btn_start_overlay">オーバーレイでキャリブレーション開始</string>
    <string name="btn_clear_keys">保存済みキーをクリア</string>
    <string name="overlay_started">Overlay started. キーボードを表示した状態で登録したいキーをタップしてください。</string>
    <string name="saved_keys_cleared">保存済みキーをクリアしました。</string>
    <string name="dialog_register_key_title">キーを登録</string>
    <string name="dialog_register_key_hint">例: a または 1</string>
    <string name="dialog_register_key_message">この座標をどの文字として登録しますか？\n\nx=%1$d, y=%2$d</string>
    <string name="dialog_save">保存</string>
    <string name="dialog_cancel">キャンセル</string>
    <string name="dialog_done">完了</string>

    <!-- Mapping List / Management -->
    <string name="title_mapping_list">マッピング管理</string>
    <string name="fab_add_mapping">追加</string>
    <string name="dialog_add_mapping_title">マッピングを追加</string>
    <string name="dialog_edit_mapping_title">マッピングを編集</string>
    <string name="hint_mapping_trigger">認識する文字（トリガー）</string>
    <string name="hint_mapping_output">入力する文字列（出力）</string>
    <string name="hint_mapping_priority">優先度（整数, 大きいほど優先）</string>
    <string name="delete_confirm_title">削除確認</string>
    <string name="delete_confirm_message">マッピング "%1$s" → "%2$s" を削除しますか？</string>

    <!-- Misc -->
    <string name="no_mappings">登録されたマッピングはありません。</string>
    <string name="accessibility_not_connected">Accessibilityサービスが接続されていません。設定で有効にしてください。</string>
    <string name="test_input_completed">テスト入力を実行しました。</string>
</resources>
EOF

mkdir -p app/src/main/res/layout
cat > app/src/main/res/layout/activity_main.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:padding="16dp">

    <LinearLayout
        android:orientation="vertical"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <EditText
            android:id="@+id/etTarget"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="@string/hint_target" />

        <EditText
            android:id="@+id/etInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:hint="@string/hint_input" />

        <Button
            android:id="@+id/btnStartCapture"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/btn_start_capture" />

        <Button
            android:id="@+id/btnCalibration"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/btn_calibration" />

        <Button
            android:id="@+id/btnGuideAcc"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/btn_guide_acc" />

        <Button
            android:id="@+id/btnManageMappings"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/btn_manage_mappings" />

        <Button
            android:id="@+id/btnTestInput"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/btn_test_input" />

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/notice_permissions" />

    </LinearLayout>
</ScrollView>
EOF

cat > app/src/main/res/layout/activity_calibration.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="vertical"
    android:padding="16dp"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:id="@+id/tvInfo"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/title_calibration"
        android:padding="8dp"/>

    <Button
        android:id="@+id/btnStartOverlay"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/btn_start_overlay" />

    <Button
        android:id="@+id/btnClear"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/btn_clear_keys" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="@string/calib_info" />
</LinearLayout>
EOF

cat > app/src/main/res/layout/activity_mapping_list.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.appcompat.widget.Toolbar
        android:id="@+id/toolbar"
        android:layout_height="wrap_content"
        android:layout_width="match_parent"
        android:background="?attr/colorPrimary"
        android:minHeight="?attr/actionBarSize"
        android:title="@string/title_mapping_list"
        android:titleTextColor="@android:color/white"/>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvMappings"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_marginTop="?attr/actionBarSize"
        android:padding="8dp"
        android:clipToPadding="false"/>

    <com.google.android.material.floatingactionbutton.FloatingActionButton
        android:id="@+id/fabAdd"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:contentDescription="@string/fab_add_mapping"
        app:srcCompat="@android:drawable/ic_input_add"
        app:layout_anchor="@id/rvMappings"
        app:layout_anchorGravity="bottom|end"
        android:layout_margin="16dp"/>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
EOF

cat > app/src/main/res/layout/item_mapping.xml <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:orientation="horizontal"
    android:padding="8dp"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical">

    <ImageView
        android:id="@+id/ivHandle"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:src="@android:drawable/ic_menu_sort_by_size"
        android:contentDescription="drag handle"
        android:layout_marginEnd="8dp"
        android:tint="#666"/>

    <LinearLayout
        android:orientation="vertical"
        android:layout_weight="1"
        android:layout_width="0dp"
        android:layout_height="wrap_content">

        <TextView
            android:id="@+id/tvTrigger"
            android:textStyle="bold"
            android:textSize="16sp"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"/>

        <TextView
            android:id="@+id/tvOutput"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:textColor="#444444"/>
    </LinearLayout>

    <TextView
        android:id="@+id/tvPriority"
        android:layout_width="36dp"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:textSize="14sp"/>

    <ImageButton
        android:id="@+id/btnUp"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:src="@android:drawable/arrow_up_float"
        android:contentDescription="priority up"
        android:background="?android:attr/selectableItemBackgroundBorderless"/>

    <ImageButton
        android:id="@+id/btnDown"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:src="@android:drawable/arrow_down_float"
        android:contentDescription="priority down"
        android:background="?android:attr/selectableItemBackgroundBorderless"/>

    <Switch
        android:id="@+id/switchEnabled"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>

    <ImageButton
        android:id="@+id/btnEdit"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:src="@android:drawable/ic_menu_edit"
        android:contentDescription="edit"
        android:background="?android:attr/selectableItemBackgroundBorderless"/>

    <ImageButton
        android:id="@+id/btnDelete"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:src="@android:drawable/ic_menu_delete"
        android:contentDescription="delete"
        android:background="?android:attr/selectableItemBackgroundBorderless"/>
</LinearLayout>
EOF

# Kotlin sources
mkdir -p app/src/main/java/com/hatori/hatotyper

cat > app/src/main/java/com/hatori/hatotyper/MappingStorage.kt <<'EOF'
package com.hatori.hatotyper

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Mapping(
    val id: String,
    val trigger: String,
    val output: String,
    val enabled: Boolean = true,
    val priority: Int = 0
)

object MappingStorage {
    private const val PREF = "mapping_prefs"
    private const val KEY_MAPPINGS = "mappings_json"

    private fun getPrefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun loadAll(ctx: Context): List<Mapping> {
        val raw = getPrefs(ctx).getString(KEY_MAPPINGS, "[]") ?: "[]"
        val arr = JSONArray(raw)
        val list = mutableListOf<Mapping>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id", UUID.randomUUID().toString())
            val trigger = o.optString("trigger", "")
            val output = o.optString("output", "")
            val enabled = o.optBoolean("enabled", true)
            val priority = o.optInt("priority", 0)
            if (trigger.isNotEmpty()) {
                list.add(Mapping(id, trigger, output, enabled, priority))
            }
        }
        return list.sortedWith(compareByDescending<Mapping> { it.priority })
    }

    fun saveAll(ctx: Context, mappings: List<Mapping>) {
        val arr = JSONArray()
        for (m in mappings) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("trigger", m.trigger)
            o.put("output", m.output)
            o.put("enabled", m.enabled)
            o.put("priority", m.priority)
            arr.put(o)
        }
        getPrefs(ctx).edit().putString(KEY_MAPPINGS, arr.toString()).apply()
    }

    fun add(ctx: Context, trigger: String, output: String, enabled: Boolean = true, priority: Int = 0): Mapping {
        val list = loadAll(ctx).toMutableList()
        val m = Mapping(UUID.randomUUID().toString(), trigger, output, enabled, priority)
        list.add(m)
        saveAll(ctx, list)
        return m
    }

    fun update(ctx: Context, id: String, trigger: String, output: String, enabled: Boolean, priority: Int) {
        val list = loadAll(ctx).map {
            if (it.id == id) Mapping(id, trigger, output, enabled, priority) else it
        }
        saveAll(ctx, list)
    }

    fun remove(ctx: Context, id: String) {
        val list = loadAll(ctx).filter { it.id != id }
        saveAll(ctx, list)
    }

    fun setEnabled(ctx: Context, id: String, enabled: Boolean) {
        val list = loadAll(ctx).map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        saveAll(ctx, list)
    }

    fun setPriority(ctx: Context, id: String, priority: Int) {
        val list = loadAll(ctx).map {
            if (it.id == id) it.copy(priority = priority) else it
        }
        saveAll(ctx, list)
    }

    fun clear(ctx: Context) {
        getPrefs(ctx).edit().remove(KEY_MAPPINGS).apply()
    }
}
EOF

cat > app/src/main/java/com/hatori/hatotyper/KeyMapStorage.kt <<'EOF'
package com.hatori.hatotyper

import android.content.Context
import org.json.JSONObject

data class KeyCoord(val x: Float, val y: Float)

object KeyMapStorage {
    private const val PREF = "keymap_prefs"
    private const val KEY_MAP = "key_map_json"

    fun saveKey(context: Context, char: String, coord: KeyCoord) {
        if (char.isEmpty()) return
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val json = JSONObject(prefs.getString(KEY_MAP, "{}") ?: "{}")
        val o = JSONObject()
        o.put("x", coord.x)
        o.put("y", coord.y)
        json.put(char, o)
        prefs.edit().putString(KEY_MAP, json.toString()).apply()
    }

    fun loadAll(context: Context): Map<String, KeyCoord> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_MAP, "{}") ?: "{}"
        val json = JSONObject(raw)
        val keys = mutableMapOf<String, KeyCoord>()
        val it = json.keys()
        while (it.hasNext()) {
            val k = it.next()
            val o = json.getJSONObject(k)
            val x = o.getDouble("x").toFloat()
            val y = o.getDouble("y").toFloat()
            keys[k] = KeyCoord(x, y)
        }
        return keys
    }

    fun getCoord(context: Context, char: String): KeyCoord? {
        return loadAll(context)[char]
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_MAP).apply()
    }
}
EOF

cat > app/src/main/java/com/hatori/hatotyper/MappingAdapter.kt <<'EOF'
package com.hatori.hatotyper

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class MappingAdapter(
    private val listener: Listener,
    private val startDrag: ((RecyclerView.ViewHolder) -> Unit)? = null
) : ListAdapter<Mapping, MappingAdapter.VH>(DIFF) {

    interface Listener {
        fun onEdit(mapping: Mapping)
        fun onDelete(mapping: Mapping)
        fun onToggleEnabled(mapping: Mapping, enabled: Boolean)
        fun onChangePriority(mapping: Mapping, newPriority: Int)
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val tmp = currentList.toMutableList()
        val item = tmp.removeAt(fromPosition)
        tmp.add(toPosition, item)
        submitList(tmp)
    }

    fun getCurrentListCopy(): List<Mapping> = currentList.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_mapping, parent, false)
        return VH(v as android.view.ViewGroup)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = getItem(position)
        holder.bind(m)
    }

    inner class VH(itemView: ViewGroup) : RecyclerView.ViewHolder(itemView) {
        private val ivHandle: ImageView = itemView.findViewById(R.id.ivHandle)
        private val tvTrigger: TextView = itemView.findViewById(R.id.tvTrigger)
        private val tvOutput: TextView = itemView.findViewById(R.id.tvOutput)
        private val tvPriority: TextView = itemView.findViewById(R.id.tvPriority)
        private val btnUp: ImageButton = itemView.findViewById(R.id.btnUp)
        private val btnDown: ImageButton = itemView.findViewById(R.id.btnDown)
        private val swEnabled: Switch = itemView.findViewById(R.id.switchEnabled)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(m: Mapping) {
            tvTrigger.text = m.trigger
            tvOutput.text = m.output
            tvPriority.text = m.priority.toString()

            swEnabled.setOnCheckedChangeListener(null)
            swEnabled.isChecked = m.enabled
            swEnabled.setOnCheckedChangeListener { _, checked ->
                listener.onToggleEnabled(m, checked)
            }

            btnEdit.setOnClickListener { listener.onEdit(m) }
            btnDelete.setOnClickListener { listener.onDelete(m) }

            btnUp.setOnClickListener { listener.onChangePriority(m, m.priority + 1) }
            btnDown.setOnClickListener { listener.onChangePriority(m, m.priority - 1) }

            ivHandle.setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    startDrag?.invoke(this)
                }
                false
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Mapping>() {
            override fun areItemsTheSame(oldItem: Mapping, newItem: Mapping): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Mapping, newItem: Mapping): Boolean = oldItem == newItem
        }
    }
}
EOF

cat > app/src/main/java/com/hatori/hatotyper/MappingListActivity.kt <<'EOF'
package com.hatori.hatotyper

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MappingListActivity : AppCompatActivity(), MappingAdapter.Listener {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: MappingAdapter
    private lateinit var fab: FloatingActionButton
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapping_list)

        rv = findViewById(R.id.rvMappings)
        fab = findViewById(R.id.fabAdd)

        adapter = MappingAdapter(this) { viewHolder ->
            if (::itemTouchHelper.isInitialized) itemTouchHelper.startDrag(viewHolder)
        }
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.onItemMove(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                persistOrder()
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(rv)

        fab.setOnClickListener { showAddDialog() }

        refreshList()
    }

    private fun persistOrder() {
        val current = adapter.getCurrentListCopy()
        val n = current.size
        val reordered = current.mapIndexed { index, mapping ->
            mapping.copy(priority = n - index)
        }
        MappingStorage.saveAll(this, reordered)
        adapter.submitList(reordered)
    }

    private fun refreshList() {
        val list = MappingStorage.loadAll(this)
        adapter.submitList(list)
    }

    private fun showAddDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,12,24,0) }
        val etTrigger = EditText(this).apply { hint = getString(R.string.hint_mapping_trigger); inputType = InputType.TYPE_CLASS_TEXT }
        val etOutput = EditText(this).apply { hint = getString(R.string.hint_mapping_output); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        val etPriority = EditText(this).apply { hint = getString(R.string.hint_mapping_priority); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }

        layout.addView(etTrigger)
        layout.addView(etOutput)
        layout.addView(etPriority)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_mapping_title))
            .setView(layout)
            .setPositiveButton("保存") { _, _ ->
                val t = etTrigger.text.toString().trim()
                val o = etOutput.text.toString()
                val p = etPriority.text.toString().toIntOrNull() ?: 0
                if (t.isNotEmpty()) {
                    MappingStorage.add(this, t, o, true, p)
                    refreshList()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showEditDialog(m: Mapping) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,12,24,0) }
        val etTrigger = EditText(this).apply { setText(m.trigger); inputType = InputType.TYPE_CLASS_TEXT }
        val etOutput = EditText(this).apply { setText(m.output); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        val etPriority = EditText(this).apply { setText(m.priority.toString()); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED }

        layout.addView(etTrigger)
        layout.addView(etOutput)
        layout.addView(etPriority)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_edit_mapping_title))
            .setView(layout)
            .setPositiveButton("更新") { _, _ ->
                val t = etTrigger.text.toString().trim()
                val o = etOutput.text.toString()
                val p = etPriority.text.toString().toIntOrNull() ?: m.priority
                if (t.isNotEmpty()) {
                    MappingStorage.update(this, m.id, t, o, m.enabled, p)
                    refreshList()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun onEdit(mapping: Mapping) = showEditDialog(mapping)

    override fun onDelete(mapping: Mapping) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_confirm_title))
            .setMessage(getString(R.string.delete_confirm_message, mapping.trigger, mapping.output))
            .setPositiveButton("削除") { _, _ ->
                MappingStorage.remove(this, mapping.id)
                refreshList()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun onToggleEnabled(mapping: Mapping, enabled: Boolean) {
        MappingStorage.setEnabled(this, mapping.id, enabled)
        refreshList()
    }

    override fun onChangePriority(mapping: Mapping, newPriority: Int) {
        MappingStorage.setPriority(this, mapping.id, newPriority)
        refreshList()
    }
}
EOF

cat > app/src/main/java/com/hatori/hatotyper/MainActivity.kt <<'EOF'
package com.hatori.hatotyper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    companion object {
        const val REQ_MEDIA_PROJ = 1001
    }

    private lateinit var mpManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val etTarget = findViewById<EditText>(R.id.etTarget)
        val etInput = findViewById<EditText>(R.id.etInput)
        val btnStartCapture = findViewById<Button>(R.id.btnStartCapture)
        val btnGuideAcc = findViewById<Button>(R.id.btnGuideAcc)
        val btnCalib = findViewById<Button>(R.id.btnCalibration)
        val btnTest = findViewById<Button>(R.id.btnTestInput)
        val btnManage = findViewById<Button>(R.id.btnManageMappings)

        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        etTarget.setText(prefs.getString("targetWord", ""))
        etInput.setText(prefs.getString("inputWord", ""))

        btnStartCapture.setOnClickListener {
            prefs.edit()
                .putString("targetWord", etTarget.text.toString())
                .putString("inputWord", etInput.text.toString())
                .apply()
            val intent = mpManager.createScreenCaptureIntent()
            startActivityForResult(intent, REQ_MEDIA_PROJ)
        }

        btnGuideAcc.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.guide_acc_title))
                .setMessage(getString(R.string.guide_acc_message))
                .setPositiveButton(getString(R.string.btn_open_accessibility_settings)) { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNeutralButton(getString(R.string.btn_open_overlay_settings)) { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
                .show()
        }

        btnCalib.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        btnManage.setOnClickListener {
            startActivity(Intent(this, MappingListActivity::class.java))
        }

        btnTest.setOnClickListener {
            val input = etInput.text.toString()
            MyAccessibilityService.instance?.let { svc ->
                if (svc.currentFocusedCanSetText()) {
                    svc.setTextToFocusedField(input)
                } else {
                    svc.performTapForText(input)
                }
            } ?: run {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.guide_acc_title))
                    .setMessage(getString(R.string.accessibility_not_connected))
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJ && resultCode == Activity.RESULT_OK && data != null) {
            val svc = Intent(this, ScreenCaptureService::class.java)
            svc.putExtra("resultCode", resultCode)
            svc.putExtra("data", data)
            startForegroundService(svc)
        }
    }
}
EOF

cat > app/src/main/java/com/hatori/hatotyper/CalibrationActivity.kt <<'EOF'
package com.hatori.hatotyper

import android.app.AlertDialog
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class CalibrationActivity : AppCompatActivity() {
    companion object {
        private const val REQ_OVERLAY = 2001
    }

    private var overlayView: View? = null
    private lateinit var wm: WindowManager
    private lateinit var infoTv: TextView
    private var capturing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibration)

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        infoTv = findViewById(R.id.tvInfo)
        val btnStart = findViewById<Button>(R.id.btnStartOverlay)
        val btnClear = findViewById<Button>(R.id.btnClear)

        btnStart.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivityForResult(intent, REQ_OVERLAY)
                return@setOnClickListener
            }
            startOverlayCapture()
        }

        btnClear.setOnClickListener {
            KeyMapStorage.clear(this)
            updateInfo(getString(R.string.saved_keys_cleared))
        }

        updateInfo()
    }

    private fun updateInfo(msg: String? = null) {
        val m = KeyMapStorage.loadAll(this)
        infoTv.text = (msg ?: "Saved keys: ${m.keys.joinToString(", ")}")
    }

    private fun startOverlayCapture() {
        if (capturing) return
        capturing = true

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val overlay = object : View(this) {
            override fun onTouchEvent(ev: MotionEvent): Boolean {
                if (ev.action == MotionEvent.ACTION_DOWN) {
                    val rawX = ev.rawX
                    val rawY = ev.rawY
                    runOnUiThread {
                        askCharAndSave(rawX, rawY)
                    }
                    return true
                }
                return super.onTouchEvent(ev)
            }
        }

        overlay.setBackgroundColor(0x00000000)
        overlay.isClickable = true
        overlay.isFocusable = true

        overlayView = overlay
        wm.addView(overlayView, params)
        updateInfo(getString(R.string.overlay_started))
    }

    private fun stopOverlay() {
        overlayView?.let {
            try { wm.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
        capturing = false
        updateInfo()
    }

    private fun askCharAndSave(rawX: Float, rawY: Float) {
        val ed = android.widget.EditText(this)
        ed.hint = getString(R.string.dialog_register_key_hint)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_register_key_title))
            .setMessage(getString(R.string.dialog_register_key_message, rawX.roundToInt(), rawY.roundToInt()))
            .setView(ed)
            .setPositiveButton(getString(R.string.dialog_save)) { _, _ ->
                val txt = ed.text.toString()
                if (txt.isNotEmpty()) {
                    val key = txt.trim().substring(0, 1)
                    KeyMapStorage.saveKey(this, key, KeyCoord(rawX, rawY))
                    updateInfo("Saved: ${key} => (${rawX.roundToInt()}, ${rawY.roundToInt()})")
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .setNeutralButton(getString(R.string.dialog_done)) { _, _ ->
                stopOverlay()
            }
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayCapture()
            } else {
                updateInfo("オーバーレイ許可が必要です。設定で許可してください。")
            }
        }
    }

    override fun onDestroy() {
        stopOverlay()
        super.onDestroy()
    }
}
EOF

cat > app/src/main/java/com/hatori/hatotyper/MyAccessibilityService.kt <<'EOF'
package com.hatori.hatotyper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {
    companion object {
        var instance: MyAccessibilityService? = null
        private const val TAG = "MyAccService"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        if (BuildConfig.ENABLE_VERBOSE_LOG) Log.i(TAG, "AccessibilityService connected")
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    fun currentFocusedCanSetText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return focus != null
    }

    fun setTextToFocusedField(text: String) {
        val root = rootInActiveWindow ?: return
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focus != null) {
            val args = android.os.Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }

    fun performTapForText(text: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.w(TAG, "dispatchGesture requires API 24+")
            return
        }
        if (text.isEmpty()) return

        val keyMap = KeyMapStorage.loadAll(this)
        if (keyMap.isEmpty()) {
            Log.w(TAG, "No keymap saved")
            return
        }

        val gestureBuilder = GestureDescription.Builder()
        var startTime: Long = 0
        val tapDuration = 50L
        val interDelay = 120L

        text.forEach { ch ->
            val key = ch.toString()
            val coord = keyMap[key]
            if (coord == null) {
                Log.w(TAG, "No coord for char='$key', skipping")
                startTime += tapDuration + interDelay
                return@forEach
            }
            val path = Path().apply { moveTo(coord.x, coord.y) }
            val stroke = GestureDescription.StrokeDescription(path, startTime, tapDuration)
            gestureBuilder.addStroke(stroke)
            startTime += tapDuration + interDelay
        }

        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i(TAG, "Gesture completed for text='$text'")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Gesture cancelled")
            }
        }, null)
    }
}
EOF

cat > app/src/main/java/com/hatori/hatotyper/ScreenCaptureService.kt <<'EOF'
package com.hatori.hatotyper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.ImageFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.app.Activity
import android.util.Log
import android.os.SystemClock
import android.graphics.Bitmap

class ScreenCaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private lateinit var ocr: OCRProcessor
    private val TAG = "ScreenCaptureSvc"

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("CaptureThread")
        thread.start()
        handler = Handler(thread.looper)
        ocr = OCRProcessor(this) { recognizedText ->
            val mappings = MappingStorage.loadAll(this).filter { it.enabled }
            var handled = false
            if (mappings.isNotEmpty()) {
                for (m in mappings) {
                    try {
                        if (recognizedText.contains(m.trigger, ignoreCase = true)) {
                            MyAccessibilityService.instance?.let { svc ->
                                if (svc.currentFocusedCanSetText()) {
                                    svc.setTextToFocusedField(m.output)
                                } else {
                                    svc.performTapForText(m.output)
                                }
                            }
                            handled = true
                            break
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "mapping handle error: ${e.message}")
                    }
                }
            }

            if (!handled) {
                val prefs = getSharedPreferences("app", Context.MODE_PRIVATE)
                val target = prefs.getString("targetWord", "") ?: ""
                val input = prefs.getString("inputWord", "") ?: ""
                if (target.isNotEmpty() && recognizedText.contains(target)) {
                    MyAccessibilityService.instance?.let { svc ->
                        if (svc.currentFocusedCanSetText()) {
                            svc.setTextToFocusedField(input)
                        } else {
                            svc.performTapForText(input)
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channelId = createNotificationChannel()
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("hatotyper")
            .setContentText("Monitoring screen for target words")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        startForeground(2, notif)

        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data = intent.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY

        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)
        startCapture()
        return START_STICKY
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
        mediaProjection?.createVirtualDisplay(
            "ocr-cap",
            width, height, density,
            0,
            imageReader?.surface, null, handler
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val bitmap = ImageUtil.imageToBitmap(image)
                ocr.processBitmap(bitmap)
            } catch (e: Exception) {
                Log.w(TAG, "image processing error: ${e.message}")
            } finally {
                image.close()
            }
        }, handler)
    }

    private fun createNotificationChannel(): String {
        val channelId = "hatotyper_channel"
        val channelName = "hatotyper"
        val nm = getSystemService(NotificationManager::class.java)
        if (nm != null) {
            val ch = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(ch)
        }
        return channelId
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mediaProjection?.stop()
        imageReader?.close()
        super.onDestroy()
    }
}
EOF

cat > app/src/main/java/com/hatori/hatotyper/OCRProcessor.kt <<'EOF'
package com.hatori.hatotyper

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.util.Log

class OCRProcessor(private val ctx: Context, private val onTextFound: (String) -> Unit) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val TAG = "OCRProcessor"

    fun processBitmap(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text ?: ""
                if (fullText.isNotBlank()) {
                    if (BuildConfig.ENABLE_VERBOSE_LOG) Log.d(TAG, "OCR text: $fullText")
                    onTextFound(fullText)
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "OCR failed: ${e.message}")
            }
    }
}
EOF

cat > app/src/main/java/com/hatori/hatotyper/ImageUtil.kt <<'EOF'
package com.hatori.hatotyper

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream
import android.graphics.Rect
import android.graphics.BitmapFactory

object ImageUtil {
    fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes
        val yBuffer = plane[0].buffer
        val uBuffer = plane[1].buffer
        val vBuffer = plane[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 80, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
EOF

echo "All files created. Review changes, then run:"
echo "  git add -A"
echo "  git commit -m \"Add hatotyper initial files\""
echo "  git push origin <branch>"
echo ""
echo "If you want me to produce a git patch instead, tell me."