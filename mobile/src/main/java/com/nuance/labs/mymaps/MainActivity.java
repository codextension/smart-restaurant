package com.nuance.labs.mymaps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdate;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
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

import java.util.List;

public class MainActivity extends AppCompatActivity implements PermissionsListener, OnMapReadyCallback, SharedPreferences.OnSharedPreferenceChangeListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private MapView mapView;
    private MapboxMap mapboxMap;
    private FloatingActionButton listenToUserBtn;
    private PermissionsManager permissionsManager;

    private Session speechSession;
    private Transaction recoTx;

    private SharedPreferences sharedPreferences;
    private Transaction.Options recoTxOptions = new Transaction.Options();

    private MyCurrentLoctionListener locationListener = new MyCurrentLoctionListener();
    private LocationManager locationManager;
    /**
     * if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
     * initializeMap();
     * } else {
     * requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
     * Configuration.PERMISSION_REQUEST_LOCATION);
     * }
     */

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
    }


    private void showMessage(String msg) {
        Snackbar mySnackbar = Snackbar.make(findViewById(R.id.mainCoordinatorLayout), msg, Snackbar.LENGTH_LONG);
        mySnackbar.show();
    }

    public void gotoMyCoords(View view) {
        enableLocationPlugin();
    }

    public void listenToUser(View view) {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Mapbox.getInstance(this, "pk.eyJ1IjoieGVvbm9zIiwiYSI6ImNqanEzOGMzODV5aWszcG8zZ2NuYWRwY2MifQ.r0EfxOqx3A4CgXOIeKBwuQ");
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
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        recoTxOptions.setLanguage(new Language(syncConnPref));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    Configuration.PERMISSION_REQUEST_LOCATION);
        }else{
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
        }

        mapView.getMapAsync(this);

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
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.mapboxMap = mapboxMap;
    }

    @SuppressWarnings({"MissingPermission"})
    private void enableLocationPlugin() {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {
            mapboxMap.addMarker(new MarkerOptions()
                    .position(new LatLng(locationListener.getLocation().getLatitude(), locationListener.getLocation().getLongitude()))
            );
            mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(new CameraPosition.Builder().target(new LatLng(locationListener.getLocation().getLatitude(), locationListener.getLocation().getLongitude()))
                    .zoom(8).build()), 2000, null);
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
/*        if (requestCode == Configuration.PERMISSION_REQUEST_MICROPHONE) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                JSONObject appServerData = new JSONObject();
                recoTx = speechSession.recognizeWithService(Configuration.CONTEXT_TAG, appServerData, recoTxOptions, recoListener);
            }
        } else if (requestCode == Configuration.PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
            }
        }*/
    }
}
