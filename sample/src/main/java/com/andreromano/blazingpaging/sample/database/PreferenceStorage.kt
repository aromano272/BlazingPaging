/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andreromano.blazingpaging.sample.database

import android.content.SharedPreferences
import com.andreromano.blazingpaging.sample.reddit.model.AccessTokenResult
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.util.*
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Storage for app and user preferences.
 */
interface PreferenceStorage {

    var redditDeviceId: String
    var redditAccessToken: AccessTokenResult?

    fun clearAll()
}

/**
 * [PreferenceStorage] impl backed by [android.content.SharedPreferences].
 */
class SharedPreferenceStorage constructor(
    private val prefs: SharedPreferences,
    moshi: Moshi,
) : PreferenceStorage {

    override var redditDeviceId: String by NonnullStringPreference(prefs, UUID.randomUUID().toString())

    override var redditAccessToken: AccessTokenResult? by JsonPreference(prefs, moshi.adapter(AccessTokenResult::class.java), null)

    override fun clearAll() {
        prefs.edit().clear().commit()
    }

    companion object {

    }
}

class BooleanPreference(
    private val preferences: SharedPreferences,
    private val defaultValue: Boolean,
) : ReadWriteProperty<Any, Boolean> {

    override fun getValue(thisRef: Any, property: KProperty<*>): Boolean {
        return preferences.getBoolean(property.name, defaultValue)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Boolean) {
        preferences.edit().putBoolean(property.name, value).commit()
    }
}

class StringPreference(
    private val preferences: SharedPreferences,
    private val defaultValue: String?,
) : ReadWriteProperty<Any, String?> {

    override fun getValue(thisRef: Any, property: KProperty<*>): String? {
        return preferences.getString(property.name, defaultValue)
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String?) {
        preferences.edit().putString(property.name, value).commit()
    }
}

class NonnullStringPreference(
    private val preferences: SharedPreferences,
    private val defaultValue: String,
) : ReadWriteProperty<Any, String> {

    override fun getValue(thisRef: Any, property: KProperty<*>): String {
        return preferences.getString(property.name, defaultValue)!!
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: String) {
        preferences.edit().putString(property.name, value).commit()
    }
}

class NonnullStringSetPreference(
    private val preferences: SharedPreferences,
    private val defaultValue: Set<String>,
) : ReadWriteProperty<Any, Set<String>> {

    override fun getValue(thisRef: Any, property: KProperty<*>): Set<String> {
        return preferences.getStringSet(property.name, defaultValue)!!
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Set<String>) {
        preferences.edit().putStringSet(property.name, value).commit()
    }
}

class LongPreference(
    private val preferences: SharedPreferences,
    private val defaultValue: Long?,
) : ReadWriteProperty<Any, Long?> {

    override fun getValue(thisRef: Any, property: KProperty<*>): Long? {
        return if (preferences.contains(property.name)) {
            val value = preferences.getLong(property.name, Long.MIN_VALUE)
            return if (value != Long.MIN_VALUE) {
                value
            } else {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long?) {
        if (value != null) {
            preferences.edit().putLong(property.name, value).apply()
        } else {
            preferences.edit().remove(property.name).apply()
        }
    }
}

class NonnullLongPreference(
    private val preferences: SharedPreferences,
    private val defaultValue: Long,
) : ReadWriteProperty<Any, Long> {

    override fun getValue(thisRef: Any, property: KProperty<*>): Long {
        return if (preferences.contains(property.name)) {
            val value = preferences.getLong(property.name, Long.MIN_VALUE)
            return if (value != Long.MIN_VALUE) {
                value
            } else {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Long) {
        preferences.edit().putLong(property.name, value).apply()
    }
}

class IntPreference(
    private val preferences: SharedPreferences,
    private val defaultValue: Int?,
) : ReadWriteProperty<Any, Int?> {

    override fun getValue(thisRef: Any, property: KProperty<*>): Int? {
        return if (preferences.contains(property.name)) {
            val value = preferences.getInt(property.name, Int.MIN_VALUE)
            return if (value != Int.MIN_VALUE) {
                value
            } else {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Int?) {
        if (value != null) {
            preferences.edit().putInt(property.name, value).commit()
        } else {
            preferences.edit().remove(property.name).commit()
        }
    }
}

class DoublePreference(
    private val preferences: SharedPreferences,
    private val defaultValue: Double?,
) : ReadWriteProperty<Any, Double?> {

    override fun getValue(thisRef: Any, property: KProperty<*>): Double? {
        return if (preferences.contains(property.name)) {
            val value = preferences.getFloat(property.name, Float.MIN_VALUE)
            return if (value != Float.MIN_VALUE) {
                value.toDouble()
            } else {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Double?) {
        if (value != null) {
            preferences.edit().putFloat(property.name, value.toFloat()).commit()
        } else {
            preferences.edit().remove(property.name).commit()
        }
    }
}

class FloatPreference(
    private val preferences: SharedPreferences,
    private val defaultValue: Float?,
) : ReadWriteProperty<Any, Float?> {

    override fun getValue(thisRef: Any, property: KProperty<*>): Float? {
        return if (preferences.contains(property.name)) {
            val value = preferences.getFloat(property.name, Float.MIN_VALUE)
            return if (value != Float.MIN_VALUE) {
                value
            } else {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: Float?) {
        if (value != null) {
            preferences.edit().putFloat(property.name, value).commit()
        } else {
            preferences.edit().remove(property.name).commit()
        }
    }
}

class JsonPreference<T>(
    private val preferences: SharedPreferences,
    private val adapter: JsonAdapter<T>,
    private val defaultValue: T,
) : ReadWriteProperty<Any, T?> {

    override fun getValue(thisRef: Any, property: KProperty<*>): T? {
        return preferences.getString(property.name, null)?.let { adapter.fromJson(it) } ?: defaultValue
    }

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T?) {
        if (value != null) {
            preferences.edit().putString(property.name, adapter.toJson(value)).commit()
        } else {
            preferences.edit().remove(property.name).commit()
        }
    }
}
