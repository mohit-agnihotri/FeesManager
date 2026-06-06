package com.example.feesmanager.utils

import android.util.Log

/**
 * NotificationHelper — STUBBED.
 *
 * The deployed Supabase schema does not include `notifications` or `fcm_token` columns.
 * All notification methods are no-ops that log silently.
 * To enable notifications, add a `notifications` table and `fcm_token` column to profiles.
 */
object NotificationHelper {

    private const val TAG = "NotificationHelper"

    /** No-op: FCM token storage not available without fcm_token column on profiles. */
    fun saveToken(token: String = "") {
        Log.d(TAG, "saveToken: stubbed — no fcm_token column in schema")
    }

    fun notifyTeacher(teacherId: String, studentName: String, amount: Int) {
        Log.d(TAG, "notifyTeacher: stubbed — $studentName paid ₹$amount")
    }

    fun notifyStudent(teacherId: String, studentId: String, amount: Int, status: String) {
        Log.d(TAG, "notifyStudent: stubbed — $status ₹$amount")
    }

    fun notifyFeesDue(teacherId: String, studentId: String, studentName: String, amount: Int) {
        Log.d(TAG, "notifyFeesDue: stubbed — $studentName ₹$amount")
    }

    fun notifyJoinRequest(teacherId: String, studentName: String, className: String) {
        Log.d(TAG, "notifyJoinRequest: stubbed — $studentName for $className")
    }

    fun notifyApproved(teacherId: String, studentId: String, studentName: String) {
        Log.d(TAG, "notifyApproved: stubbed — $studentName")
    }
}