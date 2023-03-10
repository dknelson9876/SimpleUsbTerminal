package de.kai_morich.simple_usb_terminal;

class Constants {

    public static final String INTENT_ACTION_HEADING_STATS = "TerminalFragment.RECEIVE_HEADING_STATE";
    public static final String EXTRA_HEADING_STATS = "TerminalFragment.HEADING_EXTRA";
    // values have to be globally unique
    static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";
    static final String INTENT_ACTION_DISCONNECT = BuildConfig.APPLICATION_ID + ".Disconnect";
    static final String NOTIFICATION_CHANNEL = BuildConfig.APPLICATION_ID + ".Channel";
    static final String INTENT_CLASS_MAIN_ACTIVITY = BuildConfig.APPLICATION_ID + ".MainActivity";

    // values have to be unique within each app
    static final int NOTIFY_MANAGER_START_FOREGROUND_SERVICE = 1001;

    private Constants() {}
}
