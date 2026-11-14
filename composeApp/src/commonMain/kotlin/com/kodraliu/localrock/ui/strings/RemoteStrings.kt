package com.kodraliu.localrock.ui.strings

// Remote Control and camera wording for the redesign (track B5). verified = official wording
// (PROPOSAL.md sections 3 and 6), inferred = our guess.
object RemoteStrings {
    const val REMOTE_CONTROL = "Remote Control" // verified
    const val JOYSTICK = "Joystick" // verified
    const val BUTTONS = "Buttons" // verified
    const val ENTRY_REMINDER = "To prevent accidental damage, keep an eye on the robot." // verified
    const val PIN_N_GO = "Pin n Go" // verified
    const val GO_TO = "Go to" // verified
    const val JOYSTICK_HINT = "Push up to drive, sideways to turn. Let go to stop." // inferred
    const val BUTTONS_HINT = "Tap to move the robot" // inferred
    const val BUTTONS_DURATION = "Each tap moves the robot for 1.5 seconds" // inferred
    const val FORWARD = "Forward" // inferred
    const val BACKWARD = "Backward" // inferred
    const val TURN_LEFT = "Turn left" // inferred
    const val TURN_RIGHT = "Turn right" // inferred
    const val STOP = "Stop" // inferred
    const val BACK = "Back" // inferred

    // Camera (Remote Viewing) screen.
    const val REMOTE_VIEWING = "Remote Viewing" // verified
    const val REMOTE_VIEWING_SETTINGS = "Remote Viewing Settings" // verified
    const val SCHEDULED_CRUISE = "Scheduled Cruise" // verified
    const val HISTORY = "History" // verified
    const val CRUISE = "Cruise" // verified
    const val SEARCH_FOR_PET = "Search for the Pet" // verified
    const val RETRY = "Retry" // verified (button_title_retry)
    const val HELP = "Help" // inferred
    const val MORE = "More" // inferred
    const val VIDEO_EXPERIMENTS = "Video experiments" // inferred
    const val VIDEO_LOADING_FAILED = "Video loading failed" // verified (video_load_failed_view)
    const val ERROR_DETAIL = "Error Detail" // verified (video_load_failed_view_reason)
    const val HELP_INTRO = "If the video does not start, try these fixes:" // inferred
    const val HELP_LIKELY = "Most likely for this error" // inferred

    // Remote Viewing settings page (CameraSettingDetail) and its sub pages.
    const val RV_TITLE = "Remote Viewing" // verified (setting_realtime_monitor_switch_title)
    const val RV_INTRO = "Check on your home through the robot's camera anytime, anywhere." // inferred (no English entry; zh setting_realtime_video_switch_detail)
    const val RV_ACTIVATE = "Activate Remote Viewing" // verified (real_time_setting_activation_button_text)
    const val RV_DEACTIVATE = "Deactivate Remote Viewing" // verified (camera_setting_detail1)
    const val RV_DEACTIVATE_CONFIRM = "If turned off, Remote Viewing must be activated again before use." // verified (camera_setting_monitor_off_tip)
    const val RV_ACTIVE_REMINDER =
        "The function can be turned off through the app or press the Reset button on the robot to reset the system to turn it off." // verified (setting_real_time_video_switch_reminder)
    const val RV_CHARGING_ONLY = "Enable Remote Viewing only when the robot is being charged." // verified (charging_dialog_title)
    const val RV_FIX_ERROR = "Please fix the error and try again." // verified (monitor_toast_for_error_malfunctioning)
    const val RV_ACTIVATION_PENDING = "Activation pending..." // verified (real_time_video_waiting_dialog_before_top_title)
    const val RV_TWO_KEY_TOP = "Press the Power button on the robot to activate remote viewing." // verified
    const val RV_TWO_KEY_BODY =
        "To protect your privacy, press the Power button once within one minute. Remote viewing will be activated when you hear the voice message." // verified
    const val RV_THREE_KEY_TOP = "Press and hold the Dock, Power, and Mop Only buttons at the same time." // verified
    const val RV_THREE_KEY_BODY =
        "To protect your privacy, press and hold the Dock, Power, and Mop Only buttons. When a voice message sounds, the remote viewing is activated." // verified
    const val RV_TIMEOUT = "Connection timed out. Check network connection." // verified (localization_strings_Setting_History_index_1)
    const val RV_SECONDS_LEFT = "s left" // inferred

    const val PRIVACY_POLICY = "Remote Viewing Privacy Policy" // verified (monitor_privacy_dialog_title)
    const val PRIVACY_POLICY_TITLE = "Remote Viewing privacy policy" // verified (camera_monitor_privacy_title)
    const val PRIVACY_INTRO = "Before starting Remote Viewing, please read and agree to the following privacy policy." // verified (monitor_privacy_dialog_content1)
    const val PRIVACY_AGREED = "Agreed" // inferred
    const val PRIVACY_NOT_AGREED = "Not agreed" // inferred
    const val AGREE = "Agree" // verified (button_title_agree)
    const val REVOKE = "Revoke authorization" // verified (reset_authoration)
    const val REVOKE_TITLE = "Revoke authorization?" // verified (reset_monitor_privacy_dialog_title)
    const val REVOKE_BODY = "Remote Viewing will be disabled. Agreement is be required for reuse." // verified (reset_monitor_privacy_dialog_content)
    const val REVOKE_ENDS_TASK = "Revoke the privacy policy will end the current task." // verified (map_object_cancel_privacy_stop_robot_alert)
    const val END = "End" // verified (map_object_cancel_privacy_stop)
    const val REVOKED = "Revoked successfully" // verified (map_object_cancel_privacy_success)
    const val REVOKE_TIMEOUT = "Revocation timeout. Try again." // verified (map_object_cancel_privacy_timeout)
    const val FAILED = "Failed" // verified (map_object_ignore_failed)

    const val ALERT_LEVEL = "Alert Level" // verified (realtime_video_title)
    const val ALERT_HEADER = "During Remote Viewing" // verified (realtime_video_info)
    const val ALERT_FLASH = "Flash" // verified
    const val ALERT_FLASH_DESC = "The indicator light will flash with no voice alert." // verified
    const val ALERT_GENTLE = "Gentle" // verified
    const val ALERT_GENTLE_DESC = "Voice alerts will sound when entering and exiting Remote Viewing." // verified
    const val ALERT_STRONG = "Strong" // verified
    const val ALERT_STRONG_DESC = "Voice alerts will sound every 15 seconds during Remote Viewing." // verified
    const val ALERT_CN_ONLY = "The official app shows this only for robots in mainland China; other robots may keep their level." // inferred
    const val ALERT_NOT_APPLIED = "The robot kept its alert level." // inferred

    const val CALL_VOLUME = "Call volume" // verified (real_time_video_call_volume_title_text)
    const val CALL_VOLUME_DESC = "Adjusts the robot speaker volume during a call" // verified (real_time_video_call_volume_subtitle_text)

    const val SECURITY = "Security settings" // verified (common_setting_secure_setting)
    const val SET_UP_PASSWORD = "Set up new password" // verified (enable_gesture_password)
    const val PASSWORD_EVERY_TIME = "Once enabled, password is needed to enter Remote Viewing." // verified (need_password_everytime)
    const val CHANGE_PASSWORD = "Change Password" // verified (modify_password)
    const val PASSWORD_ON = "Pattern password is on" // inferred
    const val PASSWORD_OFF = "Pattern password is off" // inferred
    const val PATTERN_PASSWORD = "Pattern Password" // verified (title_password)
    const val DRAW_NEW = "Draw a pattern by connecting at least four dots." // verified (draw_new_gestue_password_tip)
    const val DRAW_EXISTING = "Draw pattern password" // verified (input_gestue_password_tip)
    const val PATTERN_TOO_SHORT = "Pattern password is made up of at least 4 connected dots." // verified (gesture_password_at_least_four_length)
    const val DRAW_AGAIN = "Redraw pattern to verify." // verified (please_draw_again)
    const val NEW_PASSWORD = "New password" // verified (begin_draw_new_gesture_password)
    const val PATTERNS_DIFFER = "Patterns do not match." // verified (password_is_not_same_with_older)
    const val PATTERN_WRONG = "Incorrect pattern. Retry." // verified (password_is_incorrect_tip)
    const val PATTERN_TIMEOUT = "Robot response timeout due to unstable network connection. Restart and try again." // verified (robot_communication_exception)
    const val PATTERN_LOCKED = "Password disabled" // verified (video_func_is_locked)
    const val RETRY_AFTER = "Retry after %ds" // verified (retry_after_30_seconds, with the countdown)
    const val PASSWORD_SET = "Password set." // verified (gesture_password_set_successfully_tip)
    const val PASSWORD_CHANGED = "Password changed." // verified (modify_gesture_password_successfully_tip)
    const val FORGOT_PASSWORD = "Forgot password" // verified (forget_password)
    const val FORGOT_CONFIRM =
        "After confirming, the password will be reset and the video will be closed, and the startup video needs to be reactivated." // verified (reset_gesture_password_tip)
    const val PASSWORD_DISABLED = "Password disabled." // verified (disable_gesture_password_successfully_tip)

    const val CANCEL = "Cancel" // verified (localization_strings_Main_MainPage_11)
    const val OK = "OK" // verified (goto_setting_on)
    const val CAMERA_TUNING = "Camera tuning" // inferred
    const val CAMERA_TUNING_DESC = "Video experiments and SDP options for the live view" // inferred
    const val READ_FAILED = "Could not read the camera settings from the robot." // inferred
}
