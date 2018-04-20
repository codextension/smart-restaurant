package com.nuance.labs.mymaps;

import android.net.Uri;

public class Configuration {

    // Credentials
    public static final String APP_KEY = "421dca0a102398aabc513780f012b98f16846542ee1982c1529e40d6825962c5772dcf875068320cb9698daa7b463991ca9ed4b8b7567909f4b9df7713423c8a";
    public static final String APP_ID = "NMDPTRIAL_elie_khoury_nuance_com20180403090600";

    // Server
    public static final String SERVER_HOST = "nmsps.dev.nuance.com";
    public static final String SERVER_PORT = "443";
    public static final Uri SERVER_URI = Uri.parse("nmsps://" + APP_ID + "@" + SERVER_HOST + ":" + SERVER_PORT);

    // Language
    public static final String LANGUAGE = "eng-USA";

    // Only needed if using NLU
    public static final String CONTEXT_TAG = "M11664_A3464";

    public static final int PERMISSION_REQUEST_MICROPHONE = 0;
    public static final int PERMISSION_REQUEST_LOCATION = 1;
}
