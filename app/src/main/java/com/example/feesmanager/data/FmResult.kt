package com.example.feesmanager.data

/**
 * FmResult — Final stabilized result wrapper.
 * Using 'Fm' prefix to avoid ALL naming conflicts with common types.
 * Using 'content' instead of 'data' to avoid property shadowing.
 */
sealed class FmResult<out T> {
    /** Operation in progress. */
    object Loading : FmResult<Nothing>()

    /** Operation succeeded. */
    data class Success<out T>(val content: T) : FmResult<T>()

    /** Operation failed. */
    data class Error(
        val message: String,
        val cause: Exception? = null
    ) : FmResult<Nothing>()
}
