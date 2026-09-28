package com.embyplayernext.he.util

import android.content.Context
import java.util.UUID

object DeviceId {
    fun get(context: Context): String {
        val p = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)
        return p.getString("device_id", null) ?: UUID.randomUUID().toString().also { p.edit().putString("device_id", it).apply() }
    }
}
