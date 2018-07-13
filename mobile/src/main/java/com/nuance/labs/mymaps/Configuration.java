package com.nuance.labs.mymaps;

import android.net.Uri;

public class Configuration {

    // Credentials
    public static final String APP_KEY = "571a6135dfc94bd09579cab93fbf5daef5d1a4f9a92627a6efa22b344d25a4a43f05d91ece24ee649eb91ddd787a8c6e4c3067f5568e9c4531c7797eec1d232b"; //"421dca0a102398aabc513780f012b98f16846542ee1982c1529e40d6825962c5772dcf875068320cb9698daa7b463991ca9ed4b8b7567909f4b9df7713423c8a";
    public static final String APP_ID = "NMDPPRODUCTION_Nuance_AutoEval1320170816154340"; //  "NMDPTRIAL_elie_khoury_nuance_com20180403090600";

    // Server
    public static final String SERVER_HOST = "somcvtp4-test-tls-http.nuancemobility.net"; //"nmsps.dev.nuance.com";
    public static final String SERVER_PORT = "443";
    public static final Uri SERVER_URI = Uri.parse("nmsps://" + APP_ID + "@" + SERVER_HOST + ":" + SERVER_PORT);

    // Language
    public static final String LANGUAGE = "deu-DEU";

    // Only needed if using NLU
    public static final String CONTEXT_TAG = "AUTO_EVAL_CONTENT"; // "M11664_A3464";

    public static final int PERMISSION_REQUEST_MICROPHONE = 0;
    public static final int PERMISSION_REQUEST_LOCATION = 1;
}
