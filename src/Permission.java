public enum Permission {
    ACCESS_BACKGROUND_LOCATION("android.permission.ACCESS_BACKGROUND_LOCATION", "BACKGROUND_LOCATION"),
    ACCESS_COARSE_LOCATION("android.permission.ACCESS_COARSE_LOCATION", "COARSE_LOCATION", "LOCATION"),
    ACCESS_FINE_LOCATION("android.permission.ACCESS_FINE_LOCATION", "FINE_LOCATION", "PRECISE_LOCATION"),
    ACCESS_MEDIA_LOCATION("android.permission.ACCESS_MEDIA_LOCATION", "ACCESS_MEDIA_LOCATION"),
    ACTIVITY_RECOGNITION("android.permission.ACTIVITY_RECOGNITION", "ACTIVITY_RECOGNITION", "PHYSICAL_ACTIVITY"),
    ANSWER_PHONE_CALLS("android.permission.ANSWER_PHONE_CALLS", "ANSWER_PHONE_CALLS"),
    BLUETOOTH_ADVERTISE("android.permission.BLUETOOTH_ADVERTISE", "BLUETOOTH_ADVERTISE"),
    BLUETOOTH_CONNECT("android.permission.BLUETOOTH_CONNECT", "BLUETOOTH_CONNECT"),
    BLUETOOTH_SCAN("android.permission.BLUETOOTH_SCAN", "BLUETOOTH_SCAN"),
    BODY_SENSORS("android.permission.BODY_SENSORS", "BODY_SENSORS", "SENSORS"),
    BODY_SENSORS_BACKGROUND("android.permission.BODY_SENSORS_BACKGROUND", "BODY_SENSORS_BACKGROUND", "SENSORS_BACKGROUND"),
    CALL_PHONE("android.permission.CALL_PHONE", "CALL_PHONE"),
    CAMERA("android.permission.CAMERA", "CAMERA", "CAM"),
    FOREGROUND_SERVICE("android.permission.FOREGROUND_SERVICE", "FOREGROUND_SERVICE"),
    GET_ACCOUNTS("android.permission.GET_ACCOUNTS", "GET_ACCOUNTS"),
    INTERNET("android.permission.INTERNET", "INTERNET"),
    MANAGE_ACCOUNTS("android.permission.MANAGE_ACCOUNTS", "MANAGE_ACCOUNTS"),
    MANAGE_EXTERNAL_STORAGE("android.permission.MANAGE_EXTERNAL_STORAGE", "MANAGE_STORAGE", "ALL_FILES"),
    NFC("android.permission.NFC", "NFC"),
    POST_NOTIFICATIONS("android.permission.POST_NOTIFICATIONS", "POST_NOTIFICATIONS", "NOTIFICATIONS"),
    READ_CALENDAR("android.permission.READ_CALENDAR", "READ_CALENDAR", "CALENDAR"),
    READ_CONTACTS("android.permission.READ_CONTACTS", "READ_CONTACTS", "CONTACTS"),
    READ_EXTERNAL_STORAGE("android.permission.READ_EXTERNAL_STORAGE", "READ_STORAGE"),
    READ_MEDIA_AUDIO("android.permission.READ_MEDIA_AUDIO", "READ_MEDIA_AUDIO", "AUDIO"),
    READ_MEDIA_IMAGES("android.permission.READ_MEDIA_IMAGES", "READ_MEDIA_IMAGES", "IMAGES"),
    READ_MEDIA_VIDEO("android.permission.READ_MEDIA_VIDEO", "READ_MEDIA_VIDEO", "VIDEO"),
    READ_PHONE_STATE("android.permission.READ_PHONE_STATE", "PHONE_STATE", "PHONE"),
    RECORD_AUDIO("android.permission.RECORD_AUDIO", "RECORD_AUDIO", "MICROPHONE", "MIC"),
    RECEIVE_SMS("android.permission.RECEIVE_SMS", "RECEIVE_SMS"),
    REQUEST_INSTALL_PACKAGES("android.permission.REQUEST_INSTALL_PACKAGES", "REQUEST_INSTALL_PACKAGES"),
    SCHEDULE_EXACT_ALARM("android.permission.SCHEDULE_EXACT_ALARM", "SCHEDULE_EXACT_ALARM", "ALARM"),
    SEND_SMS("android.permission.SEND_SMS", "SEND_SMS", "SMS"),
    SYSTEM_ALERT_WINDOW("android.permission.SYSTEM_ALERT_WINDOW", "SYSTEM_ALERT_WINDOW"),
    USE_BIOMETRIC("android.permission.USE_BIOMETRIC", "USE_BIOMETRIC"),
    WRITE_CALENDAR("android.permission.WRITE_CALENDAR", "WRITE_CALENDAR"),
    WRITE_CONTACTS("android.permission.WRITE_CONTACTS", "CONTACTS_WRITE"),
    WRITE_EXTERNAL_STORAGE("android.permission.WRITE_EXTERNAL_STORAGE", "WRITE_STORAGE");

    public final String name;
    public final String[] aliases;

    Permission(String name, String... aliases) {
        this.name = name;
        this.aliases = aliases;
    }

    public static Permission from(String alias) {
        for (Permission perm : values()) {
            for (String a : perm.aliases) {
                if (a.equalsIgnoreCase(alias)) {
                    return perm;
                }
            }
        }
        return null;
    }
}
