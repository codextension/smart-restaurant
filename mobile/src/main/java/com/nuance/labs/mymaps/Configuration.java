package com.nuance.labs.mymaps;

import android.net.Uri;

import com.nuance.speechkit.PcmFormat;

public class Configuration {

    //All fields are required.
    //Your credentials can be found in your Nuance Developers portal, under "Manage My Apps".
    public static final String APP_KEY = "8c9f30ba729386c2ba363e0744b165df18449bc17b0df7992cf51cb0e410dc70af0c2a229fb0e459e622602ad3c509eb0aa4ac85b2a41fde993b8e7d4c6aedfb";
    public static final String APP_ID = "NMDPPRODUCTION_Nuance_MyMap_20180414112542";
    public static final String SERVER_HOST = "ltt.nmdp.nuancemobility.net";
    public static final String SERVER_PORT = "443";

    public static final String LANGUAGE = "eng-USA";

    public static final Uri SERVER_URI = Uri.parse("nmsps://" + APP_ID + "@" + SERVER_HOST + ":" + SERVER_PORT);

    //Only needed if using NLU
    public static final String CONTEXT_TAG = "!NLU_CONTEXT_TAG!";

    public static final PcmFormat PCM_FORMAT = new PcmFormat(PcmFormat.SampleFormat.SignedLinear16, 16000, 1);
    public static final String LANGUAGE_CODE = (Configuration.LANGUAGE.contains("!") ? "eng-USA" : Configuration.LANGUAGE);


    public static final int PERMISSION_REQUEST_MICROPHONE = 0;
    public static final int PERMISSION_REQUEST_LOCATION = 1;
}
