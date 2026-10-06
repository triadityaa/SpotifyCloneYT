package com.plcoding.spotifycloneyt.other

import androidx.annotation.StringRes

sealed interface Resource<out T> {

    data object Loading : Resource<Nothing>

    data class Success<T>(val data: T) : Resource<T>

    data class Error(@StringRes val messageRes: Int) : Resource<Nothing>
}
