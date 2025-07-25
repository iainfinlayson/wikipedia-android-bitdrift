package org.wikipedia.settings

import android.location.Location
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.logging.HttpLoggingInterceptor
import org.wikipedia.BuildConfig
import org.wikipedia.R
import org.wikipedia.WikipediaApp
import org.wikipedia.analytics.SessionData
import org.wikipedia.analytics.eventplatform.AppSessionEvent
import org.wikipedia.analytics.eventplatform.StreamConfig
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.donate.DonationResult
import org.wikipedia.games.onthisday.OnThisDayGameNotificationState
import org.wikipedia.json.JsonUtil
import org.wikipedia.page.PageTitle
import org.wikipedia.page.action.PageActionItem
import org.wikipedia.page.tabs.Tab
import org.wikipedia.readinglist.recommended.RecommendedReadingListSource
import org.wikipedia.readinglist.recommended.RecommendedReadingListUpdateFrequency
import org.wikipedia.readinglist.recommended.SourceWithOffset
import org.wikipedia.suggestededits.SuggestedEditsRecentEditsFilterTypes
import org.wikipedia.theme.Theme.Companion.fallback
import org.wikipedia.util.DateUtil.dbDateFormat
import org.wikipedia.util.DateUtil.dbDateParse
import org.wikipedia.util.ReleaseUtil.isDevRelease
import org.wikipedia.util.StringUtil
import org.wikipedia.watchlist.WatchlistFilterTypes
import java.util.Date

/** Shared preferences utility for convenient POJO access.  */
object Prefs {

    var appChannel
        get() = PrefsIoUtil.getString(R.string.preference_key_app_channel, null)
        set(channel) = PrefsIoUtil.setString(R.string.preference_key_app_channel, channel)

    val appChannelKey
        get() = PrefsIoUtil.getKey(R.string.preference_key_app_channel)

    // The app install ID uses readingAppInstallID for backwards compatibility with analytics.
    var appInstallId
        get() = PrefsIoUtil.getString(R.string.preference_key_reading_app_install_id, null)
        set(id) = PrefsIoUtil.setString(R.string.preference_key_reading_app_install_id, id)

    var currentThemeId
        get() = PrefsIoUtil.getInt(R.string.preference_key_color_theme, fallback.marshallingId)
        set(theme) = PrefsIoUtil.setInt(R.string.preference_key_color_theme, theme)

    var previousThemeId
        get() = PrefsIoUtil.getInt(R.string.preference_key_previous_color_theme, fallback.marshallingId)
        set(theme) = PrefsIoUtil.setInt(R.string.preference_key_previous_color_theme, theme)

    var readingFocusModeEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_reading_focus_mode, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_reading_focus_mode, value)

    var fontFamily
        get() = PrefsIoUtil.getString(R.string.preference_key_font_family, "").orEmpty().ifEmpty { "sans-serif" }
        set(fontFamily) = PrefsIoUtil.setString(R.string.preference_key_font_family, fontFamily)

    var cookies: Map<String, MutableList<Cookie>>
        get() = if (!PrefsIoUtil.contains(R.string.preference_key_cookie_map)) {
            emptyMap()
        } else {
            val map = JsonUtil.decodeFromString<Map<String, MutableList<String>>>(PrefsIoUtil
                .getString(R.string.preference_key_cookie_map, "").orEmpty()).orEmpty()
            map.mapValues { (key, values) ->
                val url = "${WikiSite.DEFAULT_SCHEME}://$key".toHttpUrlOrNull()
                url?.let { values.mapNotNull { value -> Cookie.parse(url, value) } }.orEmpty().toMutableList()
            }
        }
        set(cookieMap) {
            val map = cookieMap.mapValues { (_, cookies) -> cookies.map { it.toString() } }
            PrefsIoUtil.setString(R.string.preference_key_cookie_map, JsonUtil.encodeToString(map))
        }

    var isShowDeveloperSettingsEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_developer_settings, isDevRelease)
        set(enabled) = PrefsIoUtil.setBoolean(R.string.preference_key_show_developer_settings, enabled)

    var mruLanguageCodeList
        get() = StringUtil.csvToList(PrefsIoUtil.getString(R.string.preference_key_language_mru, null).orEmpty())
        set(value) = PrefsIoUtil.setString(R.string.preference_key_language_mru, StringUtil.listToCsv(value))

    var appLanguageCodeList
        get() = StringUtil.csvToList(PrefsIoUtil.getString(R.string.preference_key_language_app, null).orEmpty())
        set(value) = PrefsIoUtil.setString(R.string.preference_key_language_app, StringUtil.listToCsv(value))

    var remoteConfigJson
        get() = PrefsIoUtil.getString(R.string.preference_key_remote_config, "").orEmpty().ifEmpty { "{}" }
        set(json) = PrefsIoUtil.setString(R.string.preference_key_remote_config, json)

    var tabs
        get() = JsonUtil.decodeFromString<List<Tab>>(PrefsIoUtil.getString(R.string.preference_key_tabs, null))
            ?: emptyList()
        set(tabs) = PrefsIoUtil.setString(R.string.preference_key_tabs, JsonUtil.encodeToString(tabs))

    val hasTabs get() = PrefsIoUtil.contains(R.string.preference_key_tabs)

    fun clearTabs() {
        PrefsIoUtil.remove(R.string.preference_key_tabs)
    }

    var hiddenCards: Set<String>
        get() = JsonUtil.decodeFromString<Set<String>>(PrefsIoUtil.getString(R.string.preference_key_feed_hidden_cards, null))
            ?: emptySet()
        set(cards) = PrefsIoUtil.setString(R.string.preference_key_feed_hidden_cards, JsonUtil.encodeToString(cards))

    var sessionData
        get() = JsonUtil.decodeFromString<SessionData>(PrefsIoUtil.getString(R.string.preference_key_session_data, null))
            ?: SessionData()
        set(data) = PrefsIoUtil.setString(R.string.preference_key_session_data, JsonUtil.encodeToString(data))

    // return the timeout, but don't let it be less than the minimum
    val sessionTimeout
        get() = PrefsIoUtil.getInt(
            R.string.preference_key_session_timeout,
            AppSessionEvent.DEFAULT_SESSION_TIMEOUT
        ).coerceAtLeast(AppSessionEvent.MIN_SESSION_TIMEOUT)

    var textSizeMultiplier
        get() = PrefsIoUtil.getInt(R.string.preference_key_text_size_multiplier, 0)
        set(multiplier) = PrefsIoUtil.setInt(R.string.preference_key_text_size_multiplier, multiplier)

    var editingTextSizeMultiplier
        get() = PrefsIoUtil.getInt(R.string.preference_key_editing_text_size_multiplier, 0)
        set(multiplier) = PrefsIoUtil.setInt(R.string.preference_key_editing_text_size_multiplier, multiplier)

    val geoIPCountryOverride
        get() = PrefsIoUtil.getString(R.string.preference_key_announcement_country_override, null)

    val ignoreDateForAnnouncements
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_announcement_ignore_date, false)

    var announcementPauseTime
        get() = PrefsIoUtil.getLong(R.string.preference_key_announcement_pause_time, 0)
        set(time) = PrefsIoUtil.setLong(R.string.preference_key_announcement_pause_time, time)

    val announcementDebugUrl
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_announcement_debug_url, false)

    val announcementCustomTabTestUrl
        get() = PrefsIoUtil.getString(R.string.preference_key_announcement_custom_tab_test_url, null)

    val announcementsVersionCode
        get() = PrefsIoUtil.getInt(R.string.preference_key_announcement_version_code, 0)

    val retrofitLogLevel: HttpLoggingInterceptor.Level
        get() {
            val prefValue = PrefsIoUtil.getString(R.string.preference_key_retrofit_log_level, null)
                ?: return if (isDevRelease) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            return when (prefValue) {
                "BASIC" -> HttpLoggingInterceptor.Level.BASIC
                "HEADERS" -> HttpLoggingInterceptor.Level.HEADERS
                "BODY" -> HttpLoggingInterceptor.Level.BODY
                "NONE" -> HttpLoggingInterceptor.Level.NONE
                else -> HttpLoggingInterceptor.Level.NONE
            }
        }

    val restbaseUriFormat
        get() = PrefsIoUtil.getString(R.string.preference_key_restbase_uri_format, null)
            .orEmpty().ifEmpty { BuildConfig.DEFAULT_RESTBASE_URI_FORMAT }

    val mediaWikiBaseUrl
        get() = PrefsIoUtil.getString(R.string.preference_key_mediawiki_base_uri, "")!!

    val mediaWikiBaseUriSupportsLangCode
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_mediawiki_base_uri_supports_lang_code, true)

    val eventPlatformIntakeUriOverride
        get() = PrefsIoUtil.getString(R.string.preference_key_event_platform_intake_base_uri, "")!!

    fun getLastRunTime(task: String): Long {
        return PrefsIoUtil.getLong(getLastRunTimeKey(task), 0)
    }

    fun setLastRunTime(task: String, time: Long) {
        PrefsIoUtil.setLong(getLastRunTimeKey(task), time)
    }

    private fun getLastRunTimeKey(task: String): String {
        return PrefsIoUtil.getKey(R.string.preference_key_last_run_time_format, task)
    }

    var pageLastShown
        get() = PrefsIoUtil.getLong(R.string.preference_key_page_last_shown, 0)
        set(value) = PrefsIoUtil.setLong(R.string.preference_key_page_last_shown, value)

    val isImageDownloadEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_images, true)

    val isDownloadOnlyOverWiFiEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_download_only_over_wifi, false)

    val isDownloadingReadingListArticlesEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_download_reading_list_articles, true)

    val isLinkPreviewEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_link_previews, true)

    val isCollapseTablesEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_collapse_tables, true)

    fun getReadingListSortMode(defaultValue: Int): Int {
        return PrefsIoUtil.getInt(R.string.preference_key_reading_list_sort_mode, defaultValue)
    }

    fun setReadingListSortMode(sortMode: Int) {
        PrefsIoUtil.setInt(R.string.preference_key_reading_list_sort_mode, sortMode)
    }

    var readingListsPageSaveCount
        get() = PrefsIoUtil.getInt(R.string.preference_key_save_count_reading_lists, 0)
        set(saveCount) = PrefsIoUtil.setInt(R.string.preference_key_save_count_reading_lists, saveCount)

    fun getReadingListPageSortMode(defaultValue: Int): Int {
        return PrefsIoUtil.getInt(R.string.preference_key_reading_list_page_sort_mode, defaultValue)
    }

    fun setReadingListPageSortMode(sortMode: Int) {
        PrefsIoUtil.setInt(R.string.preference_key_reading_list_page_sort_mode, sortMode)
    }

    var loginForceEmailAuth
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_login_force_email_auth, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_login_force_email_auth, value)

    val isMemoryLeakTestEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_memory_leak_test, false)

    var isDescriptionEditTutorialEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_description_edit_tutorial_enabled, true)
        set(enabled) = PrefsIoUtil.setBoolean(R.string.preference_key_description_edit_tutorial_enabled, enabled)

    var lastDescriptionEditTime
        get() = PrefsIoUtil.getLong(R.string.preference_key_last_description_edit_time, 0)
        set(time) = PrefsIoUtil.setLong(R.string.preference_key_last_description_edit_time, time)

    val totalAnonDescriptionsEdited
        get() = PrefsIoUtil.getInt(R.string.preference_key_total_anon_descriptions_edited, 0)

    fun incrementTotalAnonDescriptionsEdited() {
        PrefsIoUtil.setInt(R.string.preference_key_total_anon_descriptions_edited, totalAnonDescriptionsEdited + 1)
    }

    var isReadingListSyncEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_sync_reading_lists, false)
        set(enabled) = PrefsIoUtil.setBoolean(R.string.preference_key_sync_reading_lists, enabled)

    var isReadingListSyncReminderEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_reading_list_sync_reminder_enabled, true)
        set(enabled) = PrefsIoUtil.setBoolean(R.string.preference_key_reading_list_sync_reminder_enabled, enabled)

    var isReadingListLoginReminderEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_reading_list_login_reminder_enabled, true)
        set(enabled) = PrefsIoUtil.setBoolean(R.string.preference_key_reading_list_login_reminder_enabled, enabled)

    var isReadingListsRemoteDeletePending
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_reading_lists_remote_delete_pending, false)
        set(pending) = PrefsIoUtil.setBoolean(R.string.preference_key_reading_lists_remote_delete_pending, pending)

    var isReadingListsRemoteSetupPending
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_reading_lists_remote_setup_pending, false)
        set(pending) = PrefsIoUtil.setBoolean(R.string.preference_key_reading_lists_remote_setup_pending, pending)

    var isInitialOnboardingEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_initial_onboarding_enabled, true)
        set(enabled) = PrefsIoUtil.setBoolean(R.string.preference_key_initial_onboarding_enabled, enabled)

    fun askedForPermissionOnce(permission: String): Boolean {
        return PrefsIoUtil.getBoolean(R.string.preference_key_permission_asked.toString() + permission, false)
    }

    fun setAskedForPermissionOnce(permission: String) {
        PrefsIoUtil.setBoolean(R.string.preference_key_permission_asked.toString() + permission, true)
    }

    var dimDarkModeImages
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_dim_dark_mode_images, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_dim_dark_mode_images, value)

    var notificationUnreadCount
        get() = PrefsIoUtil.getInt(R.string.preference_key_notification_unread_count, 0)
        set(count) = PrefsIoUtil.setInt(R.string.preference_key_notification_unread_count, count)

    var hasAnonymousNotification
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_anon_user_has_notification, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_anon_user_has_notification, value)

    var lastAnonUserWithMessages
        get() = PrefsIoUtil.getString(R.string.preference_key_last_anon_user_with_messages, "")
        set(value) = PrefsIoUtil.setString(R.string.preference_key_last_anon_user_with_messages, value)

    var lastAnonEditTime
        get() = PrefsIoUtil.getLong(R.string.preference_key_last_anon_edit_time, 0)
        set(value) = PrefsIoUtil.setLong(R.string.preference_key_last_anon_edit_time, value)

    var lastAnonNotificationTime
        get() = PrefsIoUtil.getLong(R.string.preference_key_last_anon_notification_time, 0)
        set(value) = PrefsIoUtil.setLong(R.string.preference_key_last_anon_notification_time, value)

    var lastAnonNotificationLang
        get() = PrefsIoUtil.getString(R.string.preference_key_last_anon_notification_lang, "")
        set(value) = PrefsIoUtil.setString(R.string.preference_key_last_anon_notification_lang, value)

    fun preferOfflineContent(): Boolean {
        return PrefsIoUtil.getBoolean(R.string.preference_key_prefer_offline_content, false)
    }

    var feedCardsEnabled
        get() = JsonUtil.decodeFromString<List<Boolean>>(PrefsIoUtil.getString(R.string.preference_key_feed_cards_enabled, null))
            ?: emptyList()
        set(enabledList) = PrefsIoUtil.setString(R.string.preference_key_feed_cards_enabled, JsonUtil.encodeToString(enabledList))

    var feedCardsOrder
        get() = JsonUtil.decodeFromString<List<Int>>(PrefsIoUtil.getString(R.string.preference_key_feed_cards_order, null))
            ?: emptyList()
        set(orderList) = PrefsIoUtil.setString(R.string.preference_key_feed_cards_order, JsonUtil.encodeToString(orderList))

    var feedCardsLangSupported
        get() = JsonUtil.decodeFromString<Map<Int, List<String>>>(PrefsIoUtil.getString(R.string.preference_key_feed_cards_lang_supported, null))
            ?: emptyMap()
        set(langSupportedMap) = PrefsIoUtil.setString(R.string.preference_key_feed_cards_lang_supported, JsonUtil.encodeToString(langSupportedMap))

    var feedCardsLangDisabled
        get() = JsonUtil.decodeFromString<Map<Int, List<String>>>(PrefsIoUtil.getString(R.string.preference_key_feed_cards_lang_disabled, null))
            ?: emptyMap()
        set(langDisabledMap) = PrefsIoUtil.setString(R.string.preference_key_feed_cards_lang_disabled, JsonUtil.encodeToString(langDisabledMap))

    fun resetFeedCustomizations() {
        PrefsIoUtil.remove(R.string.preference_key_feed_hidden_cards)
        PrefsIoUtil.remove(R.string.preference_key_feed_cards_enabled)
        PrefsIoUtil.remove(R.string.preference_key_feed_cards_order)
        PrefsIoUtil.remove(R.string.preference_key_feed_cards_lang_disabled)
    }

    var readingListsLastSyncTime
        get() = PrefsIoUtil.getString(R.string.preference_key_reading_lists_last_sync_time, "")
        set(timeStr) = PrefsIoUtil.setString(R.string.preference_key_reading_lists_last_sync_time, timeStr)

    var readingListsDeletedIds
        get() = JsonUtil.decodeFromString<Set<Long>>(PrefsIoUtil.getString(R.string.preference_key_reading_lists_deleted_ids, null))
            ?: emptySet()
        set(set) = PrefsIoUtil.setString(R.string.preference_key_reading_lists_deleted_ids, JsonUtil.encodeToString(set))

    fun addReadingListsDeletedIds(set: Set<Long>) {
        val maxStoredIds = 256
        val currentSet = readingListsDeletedIds.toMutableSet()
        currentSet.addAll(set)
        readingListsDeletedIds = if (currentSet.size < maxStoredIds) currentSet else set
    }

    var readingListPagesDeletedIds
        get() = JsonUtil.decodeFromString<Set<String>>(PrefsIoUtil.getString(R.string.preference_key_reading_list_pages_deleted_ids, null))
            ?: emptySet()
        set(set) = PrefsIoUtil.setString(R.string.preference_key_reading_list_pages_deleted_ids, JsonUtil.encodeToString(set))

    fun addReadingListPagesDeletedIds(set: Set<String>) {
        val maxStoredIds = 256
        val currentSet = readingListPagesDeletedIds.toMutableSet()
        currentSet.addAll(set)
        readingListPagesDeletedIds = if (currentSet.size < maxStoredIds) currentSet else set
    }

    var showReadingListSyncEnablePrompt
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_reading_lists_sync_prompt, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_show_reading_lists_sync_prompt, value)

    var isReadingListsFirstTimeSync
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_reading_lists_first_time_sync, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_reading_lists_first_time_sync, value)

    var remoteNotificationsSeenTime
        get() = PrefsIoUtil.getString(R.string.preference_key_remote_notifications_seen_time, "").orEmpty()
        set(seenTime) = PrefsIoUtil.setString(R.string.preference_key_remote_notifications_seen_time, seenTime)

    var showHistoryOfflineArticlesToast
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_history_offline_articles_toast, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_history_offline_articles_toast, value)

    var loggedOutInBackground
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_logged_out_in_background, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_logged_out_in_background, value)

    var showDescriptionEditSuccessPrompt
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_description_edit_success_prompt, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_show_description_edit_success_prompt, value)

    var showSuggestedEditsTooltip
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_suggested_edits_tooltip, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_show_suggested_edits_tooltip, value)

    var hasVisitedArticlePage
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_visited_article_page, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_visited_article_page, value)

    var announcementShownDialogs
        get() = JsonUtil.decodeFromString<Set<String>>(PrefsIoUtil.getString(R.string.preference_key_announcement_shown_dialogs, null))
            ?: emptySet()
        set(newAnnouncementIds) {
            val announcementIds = announcementShownDialogs.toMutableSet()
            announcementIds.addAll(newAnnouncementIds)
            PrefsIoUtil.setString(R.string.preference_key_announcement_shown_dialogs, JsonUtil.encodeToString(announcementIds))
        }

    fun resetAnnouncementShownDialogs() {
        PrefsIoUtil.remove(R.string.preference_key_announcement_shown_dialogs)
    }

    var shouldMatchSystemTheme
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_match_system_theme, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_match_system_theme, value)

    var suggestedEditsPauseDate: Date
        get() {
            val pref = PrefsIoUtil.getString(R.string.preference_key_suggested_edits_pause_date, "")
            return if (!pref.isNullOrEmpty()) { dbDateParse(pref) } else Date(0)
        }
        set(date) = PrefsIoUtil.setString(R.string.preference_key_suggested_edits_pause_date, dbDateFormat(date))

    var suggestedEditsPauseReverts
        get() = PrefsIoUtil.getInt(R.string.preference_key_suggested_edits_pause_reverts, 0)
        set(count) = PrefsIoUtil.setInt(R.string.preference_key_suggested_edits_pause_reverts, count)

    fun shouldOverrideSuggestedEditCounts(): Boolean {
        return PrefsIoUtil.getBoolean(R.string.preference_key_suggested_edits_override_counts, false)
    }

    val overrideSuggestedEditCount
        get() = PrefsIoUtil.getInt(R.string.preference_key_suggested_edits_override_edits, 0)

    var overrideSuggestedEditContribution
        get() = PrefsIoUtil.getInt(R.string.preference_key_suggested_edits_override_contribution, 0)
        set(value) = PrefsIoUtil.setInt(R.string.preference_key_suggested_edits_override_contribution, value)

    val overrideSuggestedRevertCount
        get() = PrefsIoUtil.getInt(R.string.preference_key_suggested_edits_override_reverts, 0)

    var installReferrerAttempts
        get() = PrefsIoUtil.getInt(R.string.preference_key_install_referrer_attempts, 0)
        set(attempts) = PrefsIoUtil.setInt(R.string.preference_key_install_referrer_attempts, attempts)

    var showImageTagsOnboarding
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_image_tags_onboarding_shown, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_image_tags_onboarding_shown, value)

    var showImageZoomTooltip
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_image_zoom_tooltip_shown, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_image_zoom_tooltip_shown, value)

    var isSuggestedEditsReactivationPassStageOne
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_suggested_edits_reactivation_pass_stage_one, true)
        set(pass) = PrefsIoUtil.setBoolean(R.string.preference_key_suggested_edits_reactivation_pass_stage_one, pass)

    var temporaryWikitext
        get() = PrefsIoUtil.getString(R.string.preference_key_temporary_wikitext_storage, "")
        set(value) = PrefsIoUtil.setString(R.string.preference_key_temporary_wikitext_storage, value)

    var pushNotificationToken
        get() = PrefsIoUtil.getString(R.string.preference_key_push_notification_token, "").orEmpty()
        set(token) = PrefsIoUtil.setString(R.string.preference_key_push_notification_token, token)

    var pushNotificationTokenOld
        get() = PrefsIoUtil.getString(R.string.preference_key_push_notification_token_old, "").orEmpty()
        set(token) = PrefsIoUtil.setString(R.string.preference_key_push_notification_token_old, token)

    var isPushNotificationTokenSubscribed
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_push_notification_token_subscribed, false)
        set(subscribed) = PrefsIoUtil.setBoolean(R.string.preference_key_push_notification_token_subscribed, subscribed)

    var isPushNotificationOptionsSet
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_push_notification_options_set, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_push_notification_options_set, value)

    val isSuggestedEditsReactivationTestEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_suggested_edits_reactivation_test, false)

    var isSuggestedEditsHighestPriorityEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_suggested_edits_highest_priority_enabled, false)
        set(enabled) = PrefsIoUtil.setBoolean(R.string.preference_key_suggested_edits_highest_priority_enabled, enabled)

    fun incrementExploreFeedVisitCount() {
        PrefsIoUtil.setInt(R.string.preference_key_explore_feed_visit_count, exploreFeedVisitCount + 1)
    }

    val exploreFeedVisitCount
        get() = PrefsIoUtil.getInt(R.string.preference_key_explore_feed_visit_count, 0)

    var selectedLanguagePositionInSearch
        get() = PrefsIoUtil.getInt(R.string.preference_key_selected_language_position_in_search, 0)
        set(position) = PrefsIoUtil.setInt(R.string.preference_key_selected_language_position_in_search, position)

    var showOneTimeSequentialUserStatsTooltip
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_sequential_user_stats_tooltip, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_show_sequential_user_stats_tooltip, value)

    var showSearchTabTooltip
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_search_tab_tooltip, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_show_search_tab_tooltip, value)

    var eventPlatformSessionId
        get() = PrefsIoUtil.getString(R.string.preference_key_event_platform_session_id, null)
        set(sessionId) = PrefsIoUtil.setString(R.string.preference_key_event_platform_session_id, sessionId)

    var notificationExcludedWikiCodes
        get() = JsonUtil.decodeFromString<Set<String>>(PrefsIoUtil.getString(R.string.preference_key_excluded_wiki_codes_notification, null))
            ?: emptySet()
        set(wikis) = PrefsIoUtil.setString(R.string.preference_key_excluded_wiki_codes_notification, JsonUtil.encodeToString(wikis))

    var notificationExcludedTypeCodes
        get() = JsonUtil.decodeFromString<Set<String>>(PrefsIoUtil.getString(R.string.preference_key_excluded_type_codes_notification, null))
            ?: emptySet()
        set(types) = PrefsIoUtil.setString(R.string.preference_key_excluded_type_codes_notification, JsonUtil.encodeToString(types))

    var streamConfigs
        get() = JsonUtil.decodeFromString<Map<String, StreamConfig>>(PrefsIoUtil.getString(R.string.preference_key_event_platform_stored_stream_configs, null))
            ?: emptyMap()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_event_platform_stored_stream_configs, JsonUtil.encodeToString(value))

    var localClassName
        get() = PrefsIoUtil.getString(R.string.preference_key_crash_report_local_class_name, "")
        set(className) = PrefsIoUtil.setString(R.string.preference_key_crash_report_local_class_name, className)

    var autoShowEditNotices
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_auto_show_edit_notices, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_auto_show_edit_notices, value)

    var isEditNoticesTooltipShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_edit_notices_tooltip_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_edit_notices_tooltip_shown, value)

    val hideReadNotificationsEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_notification_hide_read, false)

    var customizeToolbarOrder
        get() = JsonUtil.decodeFromString<List<Int>>(PrefsIoUtil.getString(R.string.preference_key_customize_toolbar_order, null))
            ?: PageActionItem.DEFAULT_TOOLBAR_LIST
        set(orderList) = PrefsIoUtil.setString(R.string.preference_key_customize_toolbar_order, JsonUtil.encodeToString(orderList))

    var customizeToolbarMenuOrder: List<Int>
        get() {
            val notInToolbarList = PageActionItem.entries.map { it.code() }.subtract(customizeToolbarOrder.toSet())
            val currentList = JsonUtil.decodeFromString<List<Int>>(PrefsIoUtil.getString(R.string.preference_key_customize_toolbar_menu_order, null)) ?: PageActionItem.DEFAULT_OVERFLOW_MENU_LIST
            return currentList.union(notInToolbarList).toList()
        }
        set(orderList) = PrefsIoUtil.setString(R.string.preference_key_customize_toolbar_menu_order, JsonUtil.encodeToString(orderList))

    fun resetToolbarAndMenuOrder() {
        PrefsIoUtil.remove(R.string.preference_key_customize_toolbar_order)
        PrefsIoUtil.remove(R.string.preference_key_customize_toolbar_menu_order)
    }

    var showOneTimeCustomizeToolbarTooltip
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_customize_toolbar_tooltip, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_customize_toolbar_tooltip, value)

    var showEditTalkPageSourcePrompt
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_edit_talk_page_source_prompt, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_show_edit_talk_page_source_prompt, value)

    var talkTopicsSortMode
        get() = PrefsIoUtil.getInt(R.string.preference_key_talk_topics_sort_mode, 0)
        set(value) = PrefsIoUtil.setInt(R.string.preference_key_talk_topics_sort_mode, value)

    var editHistoryFilterType
        get() = PrefsIoUtil.getString(R.string.preference_key_edit_history_filter_type, null).orEmpty()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_edit_history_filter_type, value)

    var talkTopicExpandOrCollapseByDefault
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_talk_topic_expand_all, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_talk_topic_expand_all, value)

    var userContribFilterExcludedNs
        get() = JsonUtil.decodeFromString<Set<Int>>(PrefsIoUtil.getString(R.string.preference_key_user_contrib_filter_excluded_ns, null))
                ?: emptySet()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_user_contrib_filter_excluded_ns, JsonUtil.encodeToString(value))

    var userContribFilterLangCode
        get() = PrefsIoUtil.getString(R.string.preference_key_user_contrib_filter_lang_code, WikipediaApp.instance.appOrSystemLanguageCode)!!
        set(value) = PrefsIoUtil.setString(R.string.preference_key_user_contrib_filter_lang_code, value)

    var donationBannerOptIn
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_donation_banner_opt_in, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_donation_banner_opt_in, value)

    var importReadingListsNewInstallDialogShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_import_reading_lists_new_install_dialog_shown, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_import_reading_lists_new_install_dialog_shown, value)

    var importReadingListsDialogShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_import_reading_lists_dialog_shown, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_import_reading_lists_dialog_shown, value)

    var receiveReadingListsData
        get() = PrefsIoUtil.getString(R.string.preference_key_receive_reading_lists_data, null)
        set(value) = PrefsIoUtil.setString(R.string.preference_key_receive_reading_lists_data, value)

    var editSyntaxHighlightEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_edit_syntax_highlight, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_edit_syntax_highlight, value)

    var editMonoSpaceFontEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_edit_monospace_font, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_edit_monospace_font, value)

    var editLineNumbersEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_edit_line_numbers, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_edit_line_numbers, value)

    var editTypingSuggestionsEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_edit_typing_suggestions, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_edit_typing_suggestions, value)

    val useUrlShortenerForSharing
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_reading_lists_share_url_shorten, false)

    var tempAccountWelcomeShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_temp_account_welcome_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_temp_account_welcome_shown, value)

    var tempAccountDialogShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_temp_account_dialog_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_temp_account_dialog_shown, value)

    var tempAccountCreateDay
        get() = PrefsIoUtil.getLong(R.string.preference_key_temp_account_create_day, 0)
        set(value) = PrefsIoUtil.setLong(R.string.preference_key_temp_account_create_day, value)

    var readingListShareTooltipShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_reading_lists_share_tooltip_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_reading_lists_share_tooltip_shown, value)

    var readingListRecentReceivedId
        get() = PrefsIoUtil.getLong(R.string.preference_key_reading_lists_recent_receive_id, -1)
        set(value) = PrefsIoUtil.setLong(R.string.preference_key_reading_lists_recent_receive_id, value)

    var suggestedEditsImageRecsOnboardingShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_se_image_recs_onboarding_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_se_image_recs_onboarding_shown, value)

    var suggestedEditsMachineGeneratedDescriptionTooltipShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_se_machine_generated_descriptions_tooltip_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_se_machine_generated_descriptions_tooltip_shown, value)

    var watchlistExcludedWikiCodes
        get() = JsonUtil.decodeFromString<Set<String>>(PrefsIoUtil.getString(R.string.preference_key_excluded_wiki_codes_watchlist, null))
            ?: emptySet()
        set(wikis) = PrefsIoUtil.setString(R.string.preference_key_excluded_wiki_codes_watchlist, JsonUtil.encodeToString(wikis))

    var watchlistIncludedTypeCodes
        get() = JsonUtil.decodeFromString<Set<String>>(PrefsIoUtil.getString(R.string.preference_key_included_type_codes_watchlist, null))
            ?: WatchlistFilterTypes.DEFAULT_FILTER_TYPE_SET.map { it.id }
        set(types) = PrefsIoUtil.setString(R.string.preference_key_included_type_codes_watchlist, JsonUtil.encodeToString(types))

    var analyticsQueueSize
        get() = PrefsIoUtil.getInt(R.string.preference_key_event_platform_queue_size, 128)
        set(value) = PrefsIoUtil.setInt(R.string.preference_key_event_platform_queue_size, value)

    var recentEditsWikiCode
        get() = PrefsIoUtil.getString(R.string.preference_key_recent_edits_wiki_code, WikipediaApp.instance.appOrSystemLanguageCode).orEmpty()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_recent_edits_wiki_code, value)

    var recentEditsIncludedTypeCodes
        get() = JsonUtil.decodeFromString<Set<String>>(PrefsIoUtil.getString(R.string.preference_key_recent_edits_included_type_codes, null))
            ?: SuggestedEditsRecentEditsFilterTypes.DEFAULT_FILTER_TYPE_SET.map { it.id }
        set(types) = PrefsIoUtil.setString(R.string.preference_key_recent_edits_included_type_codes, JsonUtil.encodeToString(types))

    var recentEditsOnboardingShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_recent_edits_onboarding_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_recent_edits_onboarding_shown, value)

    var showOneTimeSequentialRecentEditsDiffTooltip
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_sequential_recent_edits_diff_tooltip, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_show_sequential_recent_edits_diff_tooltip, value)

    var showOneTimeRecentEditsFeedbackForm
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_show_recent_edits_feedback_form, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_show_recent_edits_feedback_form, value)

    var placesWikiCode
        get() = PrefsIoUtil.getString(R.string.preference_key_places_wiki_code, WikipediaApp.instance.appOrSystemLanguageCode).orEmpty()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_places_wiki_code, value)

    var placesDefaultLocationLatLng
        get(): String? {
            val lanLng = PrefsIoUtil.getString(R.string.preference_key_default_places_location_latlng, null)
            return if (lanLng.isNullOrEmpty()) null else lanLng
        }
        set(set) = PrefsIoUtil.setString(R.string.preference_key_default_places_location_latlng, set)

    var placesLastLocationAndZoomLevel: Pair<Location, Double>?
        get() {
            // latitude|longitude|zoomLevel
            val infoList = PrefsIoUtil.getString(R.string.preference_key_places_last_location_and_zoom_level, null)?.split("|")?.map { it.toDouble() }
            return infoList?.let {
                val location = Location("").apply {
                    latitude = infoList[0]
                    longitude = infoList[1]
                }
                val zoomLevel = infoList[2]
                Pair(location, zoomLevel)
            }
        }
        set(pair) {
            var locationAndZoomLevelString: String? = null
            pair?.let {
                locationAndZoomLevelString = "${pair.first.latitude}|${pair.first.longitude}|${pair.second}"
            }
            PrefsIoUtil.setString(R.string.preference_key_places_last_location_and_zoom_level, locationAndZoomLevelString)
        }

    var recentUsedTemplates
        get() = JsonUtil.decodeFromString<Set<PageTitle>>(PrefsIoUtil.getString(R.string.preference_key_recent_used_templates, null)) ?: emptySet()
        set(set) = PrefsIoUtil.setString(R.string.preference_key_recent_used_templates, JsonUtil.encodeToString(set))

    fun addRecentUsedTemplates(set: Set<PageTitle>) {
        val maxStoredIds = 100
        val currentSet = recentUsedTemplates.toMutableSet()
        currentSet.addAll(set)
        recentUsedTemplates = if (currentSet.size < maxStoredIds) currentSet else set
    }

    var paymentMethodsLastQueryTime
        get() = PrefsIoUtil.getLong(R.string.preference_key_payment_methods_last_query_time, 0)
        set(value) = PrefsIoUtil.setLong(R.string.preference_key_payment_methods_last_query_time, value)

    var paymentMethodsMerchantId
        get() = PrefsIoUtil.getString(R.string.preference_key_payment_methods_merchant_id, null).orEmpty()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_payment_methods_merchant_id, value)

    var paymentMethodsGatewayId
        get() = PrefsIoUtil.getString(R.string.preference_key_payment_methods_gateway_id, null).orEmpty()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_payment_methods_gateway_id, value)

    var isDonationTestEnvironment
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_donation_test_env, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_donation_test_env, value)

    var donationResults
        get() = JsonUtil.decodeFromString<List<DonationResult>>(PrefsIoUtil.getString(R.string.preference_key_donation_results, null)).orEmpty()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_donation_results, JsonUtil.encodeToString(value))

    var lastOtdGameDateOverride
        get() = PrefsIoUtil.getString(R.string.preference_key_otd_game_date_override, null).orEmpty()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_otd_game_date_override, value)

    var otdGameState
        get() = PrefsIoUtil.getString(R.string.preference_key_otd_game_state, null).orEmpty()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_otd_game_state, value)

    var otdGameHistory
        get() = PrefsIoUtil.getString(R.string.preference_key_otd_game_history, null).orEmpty()
        set(value) = PrefsIoUtil.setString(R.string.preference_key_otd_game_history, value)

    var otdGameQuestionsPerDay
        get() = PrefsIoUtil.getInt(R.string.preference_key_otd_game_num_questions, 5)
        set(value) = PrefsIoUtil.setInt(R.string.preference_key_otd_game_num_questions, value)

    var otdEntryDialogShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_otd_entry_dialog_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_otd_entry_dialog_shown, value)

    var otdGameFirstPlayedShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_otd_game_first_played_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_otd_game_first_played_shown, value)

    var otdNotificationState: OnThisDayGameNotificationState
        get() = PrefsIoUtil.getString(R.string.preference_key_otd_notification_state, null)?.let {
            OnThisDayGameNotificationState.valueOf(it)
        } ?: OnThisDayGameNotificationState.NO_INTERACTED
        set(value) = PrefsIoUtil.setString(R.string.preference_key_otd_notification_state, value.name)

    var isOtdSoundOn
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_otd_sound_on, true)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_otd_sound_on, value)

    var isYearInReviewEnabled: Boolean
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_year_in_review_is_enabled, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_year_in_review_is_enabled, value)

    var yirSurveyShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_yir_survey_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_yir_survey_shown, value)

    var isRecommendedReadingListEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_recommended_reading_list_enabled, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_recommended_reading_list_enabled, value)

    var recommendedReadingListArticlesNumber
        get() = PrefsIoUtil.getInt(R.string.preference_key_recommended_reading_list_articles_number, 5)
        set(value) = PrefsIoUtil.setInt(R.string.preference_key_recommended_reading_list_articles_number, value)

    var recommendedReadingListUpdateFrequency: RecommendedReadingListUpdateFrequency
        get() = PrefsIoUtil.getString(R.string.preference_key_recommended_reading_list_update_frequency, null)?.let {
            RecommendedReadingListUpdateFrequency.valueOf(it)
        } ?: RecommendedReadingListUpdateFrequency.WEEKLY
        set(value) = PrefsIoUtil.setString(R.string.preference_key_recommended_reading_list_update_frequency, value.name)

    var recommendedReadingListSource: RecommendedReadingListSource
        get() = PrefsIoUtil.getString(R.string.preference_key_recommended_reading_list_source, null)?.let {
            RecommendedReadingListSource.valueOf(it)
        } ?: RecommendedReadingListSource.INTERESTS
        set(value) = PrefsIoUtil.setString(R.string.preference_key_recommended_reading_list_source, value.name)

    var recommendedReadingListInterests
        get() = JsonUtil.decodeFromString<List<PageTitle>>(PrefsIoUtil.getString(R.string.preference_key_recommended_reading_list_interests, null)) ?: emptyList()
        set(types) = PrefsIoUtil.setString(R.string.preference_key_recommended_reading_list_interests, JsonUtil.encodeToString(types))

    var isRecommendedReadingListNotificationEnabled
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_recommended_reading_list_notification_enabled, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_recommended_reading_list_notification_enabled, value)

    var recommendedReadingListSourceTitlesWithOffset
        get() = JsonUtil.decodeFromString<List<SourceWithOffset>>(PrefsIoUtil.getString(R.string.preference_key_recommended_reading_list_titles_with_offset, null)) ?: emptyList()
        set(types) = PrefsIoUtil.setString(R.string.preference_key_recommended_reading_list_titles_with_offset, JsonUtil.encodeToString(types))

    var isRecommendedReadingListOnboardingShown
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_recommended_reading_list_onboarding_shown, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_recommended_reading_list_onboarding_shown, value)

    var isNewRecommendedReadingListGenerated
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_recommended_reading_list_new_list_generated, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_recommended_reading_list_new_list_generated, value)

    var resetRecommendedReadingList
        get() = PrefsIoUtil.getBoolean(R.string.preference_key_recommended_reading_list_reset, false)
        set(value) = PrefsIoUtil.setBoolean(R.string.preference_key_recommended_reading_list_reset, value)
}
