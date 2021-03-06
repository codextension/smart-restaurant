package com.nuance.labs.mymaps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.hound.android.libphs.PhraseSpotterStream;
import com.hound.android.sdk.Search;
import com.hound.android.sdk.VoiceSearch;
import com.hound.android.sdk.VoiceSearchInfo;
import com.hound.android.sdk.VoiceSearchListener;
import com.hound.android.sdk.audio.SimpleAudioByteStreamSource;
import com.hound.android.sdk.util.HoundRequestInfoFactory;
import com.hound.core.model.sdk.HoundRequestInfo;
import com.hound.core.model.sdk.HoundResponse;
import com.hound.core.model.sdk.PartialTranscript;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.nuance.speechkit.Audio;
import com.nuance.speechkit.DetectionType;
import com.nuance.speechkit.Interpretation;
import com.nuance.speechkit.Language;
import com.nuance.speechkit.Recognition;
import com.nuance.speechkit.RecognitionType;
import com.nuance.speechkit.ResultDeliveryType;
import com.nuance.speechkit.Session;
import com.nuance.speechkit.Transaction;
import com.nuance.speechkit.TransactionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements PermissionsListener, OnMapReadyCallback, SharedPreferences.OnSharedPreferenceChangeListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private MapView mapView;
    private MapboxMap mapboxMap;
    private FloatingActionButton listenToUserBtn;
    private PermissionsManager permissionsManager;
    private MapboxNavigation navigation;
    private Session speechSession;
    private Transaction recoTx;
    private NavigationMapRoute navigationMapRoute;
    private VoiceSearch voiceSearch;
    private SharedPreferences sharedPreferences;
    private Transaction.Options recoTxOptions = new Transaction.Options();
    private boolean houndify = false;
    private Snackbar mySnackbar;
    private MyCurrentLoctionListener locationListener = new MyCurrentLoctionListener();
    private LocationManager locationManager;

    private Transaction.Listener recoListener = new Transaction.Listener() {
        @Override
        public void onStartedRecording(Transaction transaction) {
            listenToUserBtn.setImageResource(android.R.drawable.presence_audio_online);
            super.onStartedRecording(transaction);
        }

        @Override
        public void onFinishedRecording(Transaction transaction) {
            listenToUserBtn.setImageResource(android.R.drawable.ic_btn_speak_now);
        }

        @Override
        public void onRecognition(Transaction transaction, Recognition recognition) {
            showMessage(recognition.getText());
        }

        @Override
        public void onInterpretation(Transaction transaction, Interpretation interpretation) {
            JSONObject result = interpretation.getResult();
            try {
                driveMeThere(result);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceResponse(Transaction transaction, JSONObject jsonObject) {
            super.onServiceResponse(transaction, jsonObject);
        }

        @Override
        public void onAudio(Transaction transaction, Audio audio) {
            super.onAudio(transaction, audio);
        }

        @Override
        public void onSuccess(Transaction transaction, String s) {
            super.onSuccess(transaction, s);
        }

        @Override
        public void onError(Transaction transaction, String s, TransactionException e) {
            showMessage(s);
            super.onError(transaction, s, e);
        }
    };

    private void driveMeThere(JSONObject result) throws JSONException {
        JSONObject interpretations = ((JSONArray) result.get("interpretations")).getJSONObject(0);
        JSONObject action = interpretations.getJSONObject("action");
        JSONObject concepts = interpretations.getJSONObject("concepts");
        JSONObject intent = action.getJSONObject("intent");

        String domain = intent.getString("value");

        String url = "";

        if (domain.equals("restaurantDomain")) {
            url += "&type=restaurant";

            if (concepts.has("distance_modifier")) {
                JSONObject distance_modifier = ((JSONArray) concepts.get("distance_modifier")).getJSONObject(0);
                url += "&radius=" + 2000;
            }
            if (concepts.has("restaurant_name")) {
                JSONObject restaurant_name = ((JSONArray) concepts.get("restaurant_name")).getJSONObject(0);
                String restaurant = restaurant_name.getString("literal");
                url += "&keyword=" + restaurant;
            }
            if (concepts.has("price_modifier")) {
                JSONObject price_modifier = ((JSONArray) concepts.get("price_modifier")).getJSONObject(0);
                String price = price_modifier.getString("literal");
            }
        }

        //TODO: missing the binding with mapbox api to show on the map.
    }

    private final VoiceSearchListener voiceListener = new VoiceSearchListener() {

        /**
         * Called every time a new partial transcription is received from the Hound server.
         * This is used for providing feedback to the user of the server's interpretation of
         * their query.
         *
         * @param transcript
         */
        @Override
        public void onTranscriptionUpdate(final PartialTranscript transcript) {
            switch (voiceSearch.getState()) {
                case STATE_STARTED:
                    Log.d("MyMaps", "Listening...");
                    listenToUserBtn.setImageResource(android.R.drawable.presence_audio_online);
                    break;

                case STATE_SEARCHING:
                    Log.d("MyMaps", "Receiving...");
                    listenToUserBtn.setImageResource(android.R.drawable.ic_btn_speak_now);
                    break;

                default:
                    Log.w("MyMaps", "Unknown");
                    listenToUserBtn.setImageResource(android.R.drawable.ic_btn_speak_now);
                    break;
            }

            showMessage(transcript.getPartialTranscript());
        }

        @Override
        public void onResponse(final HoundResponse response, final VoiceSearchInfo info) {
            voiceSearch = null;

            // Make sure the request succeeded with OK
            if (!response.getStatus().equals(HoundResponse.Status.OK)) {
                Log.e("MyMaps", "Request failed with: " + response.getErrorMessage());
                return;
            }


            if (response.getResults().isEmpty()) {
                Log.i("MyMaps", "No Results");
                return;
            }
            try {
                Log.i("MyMaps", info.getContentBody());
                JSONObject jsonObject = new JSONObject(info.getContentBody());
                JSONObject allresults = jsonObject.getJSONArray("AllResults").getJSONObject(0);
                JSONObject ConversationState = allresults.getJSONObject("ConversationState");
                JSONObject ResponseEntities = ConversationState.getJSONObject("ResponseEntities");
                JSONArray Where = ResponseEntities.getJSONArray("Where");
                Double Latitude = Where.getJSONObject(0).getDouble("Latitude");
                Double Longitude = Where.getJSONObject(0).getDouble("Longitude");
                takeMeThere(Point.fromLngLat(Longitude, Latitude));
            } catch (final JSONException ex) {
            }
        }

        /**
         * Called if the search fails do to some kind of error situation.
         *
         * @param ex
         * @param info
         */
        @Override
        public void onError(final Exception ex, final VoiceSearchInfo info) {
            voiceSearch = null;

            Log.e("MyMaps", "Something went wrong");
        }

        /**
         * Called when the recording phase is completed.
         */
        @Override
        public void onRecordingStopped() {
            Log.i("MyMaps", "Receiving...");
        }

        /**
         * Called if the user aborted the search.
         *
         * @param info
         */
        @Override
        public void onAbort(final VoiceSearchInfo info) {
            voiceSearch = null;
            Log.i("MyMaps", "Aborted");
        }
    };

    public void gotoMyCoords(View view) {
        enableLocationPlugin();
    }

    public static void setLocation(final HoundRequestInfo requestInfo, final Location location) {
        if (location != null) {
            requestInfo.setLatitude(location.getLatitude());
            requestInfo.setLongitude(location.getLongitude());
            requestInfo.setPositionHorizontalAccuracy((double) location.getAccuracy());
        }
    }

    private void showMessage(String msg) {
        mySnackbar.setText(msg);
        if (!mySnackbar.isShown()) {
            mySnackbar.show();
        }
    }

    public void listenToUser(View view) {
        if (houndify) {
            if (voiceSearch == null) {
                startSearch(new SimpleAudioByteStreamSource(), false);
            } else {
                voiceSearch.stopRecording();
            }
            Search.setDebug(true);
        } else {
            if (recoTx != null) {
                recoTx.cancel();
                recoTx = null;
            }

            if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                JSONObject appServerData = new JSONObject();
                recoTx = speechSession.recognizeWithService(Configuration.CONTEXT_TAG, appServerData, recoTxOptions, recoListener);
            } else {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                        Configuration.PERMISSION_REQUEST_MICROPHONE);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private HoundRequestInfo getHoundRequestInfo(boolean enforceWakeUpPattern) {
        final HoundRequestInfo requestInfo = HoundRequestInfoFactory.getDefault(this);

        // Client App is responsible for providing a UserId for their users which is meaningful
        // to the client.
        requestInfo.setUserId("User ID");
        // Each request must provide a unique request ID.
        requestInfo.setRequestId(UUID.randomUUID().toString());
        // Providing the user's location is useful for geographic queries, such as, "Show me
        // restaurants near me".
        setLocation(
                requestInfo,
                locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER));

        if (enforceWakeUpPattern) {
            // In 'Instant Trigger Mode', we expect the wake-up phrase to be always there.
            // If mismatch happens, the Houndify platform may consider the phrase spotting
            // to be false-positive, and simply ignore the voice search.
            requestInfo.setWakeUpPattern("\"OK Hound\"");
        } else {
            requestInfo.setWakeUpPattern(PhraseSpotterStream.PATTERN);
        }

        return requestInfo;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Mapbox.getInstance(this, "pk.eyJ1IjoieGVvbm9zIiwiYSI6ImNqanEzOGMzODV5aWszcG8zZ2NuYWRwY2MifQ.r0EfxOqx3A4CgXOIeKBwuQ");
        navigation = new MapboxNavigation(this, "pk.eyJ1IjoieGVvbm9zIiwiYSI6ImNqanEzOGMzODV5aWszcG8zZ2NuYWRwY2MifQ.r0EfxOqx3A4CgXOIeKBwuQ");
        LocationEngine locationEngine = new LocationEngineProvider(this).obtainBestLocationEngineAvailable();
        navigation.setLocationEngine(locationEngine);
        setContentView(R.layout.activity_main);
        mapView = (MapView) findViewById(R.id.mapView);
        mapView.setStyleUrl("mapbox://styles/mapbox/streets-v10");
        mapView.onCreate(savedInstanceState);

        listenToUserBtn = findViewById(R.id.listenToUserBtn);

        speechSession = Session.Factory.session(this, Configuration.SERVER_URI, Configuration.APP_KEY);
        recoTxOptions.setRecognitionType(RecognitionType.DICTATION);
        recoTxOptions.setDetection(DetectionType.Short);
        recoTxOptions.setResultDeliveryType(ResultDeliveryType.PROGRESSIVE);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String syncConnPref = sharedPreferences.getString("language_code", "eng-USA");
        houndify = sharedPreferences.getString("src_provider", "Nuance").equals("Houndify");
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        recoTxOptions.setLanguage(new Language(syncConnPref));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    Configuration.PERMISSION_REQUEST_LOCATION);
        } else {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }
        mySnackbar = Snackbar.make(findViewById(R.id.mainCoordinatorLayout), "", Snackbar.LENGTH_LONG);
        mySnackbar.setDuration(5000);

        mapView.getMapAsync(this);

    }

    public void takeMeThere(Point destination) {
        Point origin = Point.fromLngLat(locationListener.getLocation().getLongitude(), locationListener.getLocation().getLatitude());
        // Point.fromLngLat(48.765693, 9.170267);

        NavigationRoute.builder(this)
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        navigationMapRoute.addRoutes(response.body().routes());
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable t) {

                    }
                });

    }

    private void startSearch(InputStream inputStream, boolean enforceWakeUpPattern) {
        if (voiceSearch != null) {
            return; // We are already searching
        }
        listenToUserBtn.setImageResource(android.R.drawable.presence_audio_online);

        voiceSearch =
                new VoiceSearch.Builder().setRequestInfo(getHoundRequestInfo(enforceWakeUpPattern))
                        .setClientId("cRJ11Gn2_IOmqj6gOyj5GA==")
                        .setClientKey("aVU1qvoR7gjxwexeM9k2TS11afkmMLmaHCbwti2qPiF4wZJQL38M0gY_MhCVivr0XI1YN_PLk-lbsAUQttbGFQ==")
                        .setListener(voiceListener)
                        .setAudioSource(inputStream)
                        .build();

        voiceSearch.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.toolbar_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivity(i);
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);

        }
    }


    @Override
    public void onResume() {
        mapView.onResume();
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        navigation.endNavigation();
        navigation.onDestroy();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        String syncConnPref = sharedPreferences.getString("language_code", "eng-USA");
        recoTxOptions.setLanguage(new Language(syncConnPref));
        houndify = sharedPreferences.getString("src_provider", "Nuance").equals("Houndify");
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
        navigationMapRoute = new NavigationMapRoute(navigation, mapView, mapboxMap,
                "admin-3-4-boundaries-bg");
    }

    @SuppressWarnings({"MissingPermission"})
    private void enableLocationPlugin() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            mapboxMap.addMarker(new MarkerOptions()
                    .position(new LatLng(locationListener.getLocation().getLatitude(), locationListener.getLocation().getLongitude()))
            );
            mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(new LatLng(locationListener.getLocation().getLatitude(), locationListener.getLocation().getLongitude()))
                    .zoom(12).build()), 2000, null);
        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, "we need special access", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            enableLocationPlugin();
        } else {
            Toast.makeText(this, "Permission not granted", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
