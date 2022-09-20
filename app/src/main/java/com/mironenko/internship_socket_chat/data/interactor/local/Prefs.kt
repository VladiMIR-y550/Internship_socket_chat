package com.mironenko.internship_socket_chat.data.interactor.local

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject

class Prefs @Inject constructor(
    private val sharedPreferences: SharedPreferences
) : LocalDataSource {

    override var loginPref: String
        get() = sharedPreferences.getString(SAVED_USER_LOGIN, "") ?: ""
        set(value) = sharedPreferences.edit {
            putString(SAVED_USER_LOGIN, value)
        }
}

private const val SAVED_USER_LOGIN = "SAVED USER LOGIN"