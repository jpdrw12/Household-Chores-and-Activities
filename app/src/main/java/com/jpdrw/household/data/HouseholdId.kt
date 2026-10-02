package com.jpdrw.household.data

import android.content.Context

private const val PREFS_NAME = "household_id_prefs"
private const val KEY = "household_id"

/** No ambiguous-looking characters (0/O, 1/I/L) — this gets read aloud or typed by hand when one
 *  device joins another's household. */
private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
private const val CODE_LENGTH = 8

/**
 * The household code that scopes every Firestore path (see AssigneeSync's doc comment for why
 * this exists at all — it's what actually separates one household's data from every other
 * install of this app, replacing an earlier hardcoded path every install shared). Stored in plain
 * SharedPreferences rather than the DataStore AppPrefs uses elsewhere: it's one small string, read
 * once synchronously at app startup before Repository/the sync classes are constructed, and a
 * synchronous read keeps that construction path simple rather than threading an async Flow
 * through Repository's constructor.
 */
object HouseholdId {
    /** Reads the stored code, or generates and persists a new random one on first launch. */
    fun getOrCreate(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY, null)?.let { return it }
        val generated = (1..CODE_LENGTH).map { CODE_ALPHABET.random() }.joinToString("")
        prefs.edit().putString(KEY, generated).apply()
        return generated
    }

    /** Switches this device to an existing household's code (typed in from another device) —
     *  takes effect on next app launch, since the sync classes are constructed once at startup
     *  with a fixed household id, not designed to be swapped mid-session. */
    fun join(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, code.trim().uppercase())
            .apply()
    }
}
