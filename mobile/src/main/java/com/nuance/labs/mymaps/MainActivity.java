package com.nuance.labs.mymaps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
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

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, SharedPreferences.OnSharedPreferenceChangeListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private MapView mapView;
    private GoogleMap map;
    private ArrayList<LatLng> markerPoints = new ArrayList<>();
    private FloatingActionButton listenToUserBtn;

    private Session speechSession;
    private Transaction recoTx;
    private String baseNearbyUrl = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?";

    private SharedPreferences sharedPreferences;
    private Transaction.Options recoTxOptions = new Transaction.Options();
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

        String url = baseNearbyUrl;

        if (domain.equals("restaurantDomain")) {
            url += "&type=restaurant";

            if (concepts.has("distance_modifier")) {
                JSONObject distance_modifier = ((JSONArray) concepts.get("distance_modifier")).getJSONObject(0);
                url += "&radius=" + 100;
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

            DownloadTask downloadTask = new DownloadTask(new ParserNearestPlaceTask());

            // Start downloading json data from Google Directions API
            downloadTask.execute(url);
        }

        // https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=-33.8670522,151.1957362&radius=10&type=restaurant&keyword=burger%20king
    }

    private String apiKey() {
        String myApiKey = "";
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(this.getPackageName(), PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            myApiKey = bundle.getString("com.google.android.maps.v2.API_KEY");
        } catch (PackageManager.NameNotFoundException e) {
        } catch (NullPointerException e) {
        }
        return myApiKey;
    }

    private void showMessage(String msg) {
        Snackbar mySnackbar = Snackbar.make(findViewById(R.id.mainCoordinatorLayout), msg, Snackbar.LENGTH_LONG);
        mySnackbar.show();
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

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == Configuration.PERMISSION_REQUEST_MICROPHONE) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                JSONObject appServerData = new JSONObject();
                recoTx = speechSession.recognizeWithService(Configuration.CONTEXT_TAG, appServerData, recoTxOptions, recoListener);
            }
        } else if (requestCode == Configuration.PERMISSION_REQUEST_LOCATION) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeMap();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Gets the MapView from the XML layout and creates it
        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        listenToUserBtn = findViewById(R.id.listenToUserBtn);

        speechSession = Session.Factory.session(this, Configuration.SERVER_URI, Configuration.APP_KEY);
        recoTxOptions.setRecognitionType(RecognitionType.DICTATION);
        recoTxOptions.setDetection(DetectionType.Short);
        recoTxOptions.setResultDeliveryType(ResultDeliveryType.FINAL);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String syncConnPref = sharedPreferences.getString("language_code", "eng-USA");
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        recoTxOptions.setLanguage(new Language(syncConnPref));

        // Gets to GoogleMap from the MapView and does initialization stuff
        mapView.getMapAsync(this);
    }

    @SuppressLint("MissingPermission")
    private void initializeMap() {
        map.setMyLocationEnabled(true);
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        Location location = locationManager.getLastKnownLocation(locationManager.getBestProvider(criteria, false));
        if (location != null) {
            baseNearbyUrl += "location=" + location.getLatitude() + "," + location.getLongitude();
            baseNearbyUrl += "&key=" + apiKey();
            markerPoints.add(new LatLng(location.getLatitude(), location.getLongitude()));
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(), location.getLongitude()), 13));

            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))      // Sets the center of the map to location user
                    .zoom(14)                   // Sets the zoom
                    .bearing(90)                // Sets the orientation of the camera to east
                    .tilt(0)                   // Sets the tilt of the camera to 30 degrees
                    .build();                   // Creates a CameraPosition from the builder
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
        }

        MapsInitializer.initialize(this);
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
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private String getDirectionsUrl(LatLng origin, LatLng dest) {

        // Origin of route
        String str_origin = "origin=" + origin.latitude + "," + origin.longitude;

        // Destination of route
        String str_dest = "destination=" + dest.latitude + "," + dest.longitude;

        // Sensor enabled
        String sensor = "sensor=false";
        String mode = "mode=driving";

        // Building the parameters to the web service
        String parameters = str_origin + "&" + str_dest + "&" + sensor + "&" + mode;

        // Output format
        String output = "json";

        // Building the url to the web service
        String url = "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters;


        return url;
    }

    private String downloadUrl(String strUrl) throws IOException {
        String data = "";
        InputStream iStream = null;
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(strUrl);

            urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.connect();

            iStream = urlConnection.getInputStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(iStream));

            StringBuffer sb = new StringBuffer();

            String line = "";
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

            data = sb.toString();

            br.close();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            iStream.close();
            urlConnection.disconnect();
        }
        return data;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        String syncConnPref = sharedPreferences.getString("language_code", "eng-USA");
        recoTxOptions.setLanguage(new Language(syncConnPref));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        map.getUiSettings().setMyLocationButtonEnabled(true);
        map.getUiSettings().setCompassEnabled(true);
        map.getUiSettings().setZoomControlsEnabled(true);
        if (ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(getBaseContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            initializeMap();
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    Configuration.PERMISSION_REQUEST_LOCATION);
        }
    }

    public void drawPath() {
        map.clear();

        MarkerOptions options = new MarkerOptions();
        options.position(markerPoints.get(1));
        options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
        map.addMarker(options);

        // Checks, whether start and end locations are captured
        LatLng origin = (LatLng) markerPoints.get(0);
        LatLng dest = (LatLng) markerPoints.get(1);

        // Getting URL to the Google Directions API
        String url = getDirectionsUrl(origin, dest);

        DownloadTask downloadTask = new DownloadTask(new ParserTask());

        // Start downloading json data from Google Directions API
        downloadTask.execute(url);
    }

    private class DownloadTask extends AsyncTask {
        private AsyncTask asyncTask;

        public DownloadTask(AsyncTask asyncTask) {
            this.asyncTask = asyncTask;
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            asyncTask.execute((String) o);
        }

        @Override
        protected Object doInBackground(Object[] objects) {

            String data = "";

            try {
                data = downloadUrl((String) objects[0]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return data;
        }
    }

    private class ParserNearestPlaceTask extends AsyncTask<Object, Integer, LatLng> {
        @Override
        protected void onPostExecute(LatLng s) {
            super.onPostExecute(s);
            if (markerPoints.size() == 2) {
                markerPoints.remove(1);
            }
            markerPoints.add(s);
            drawPath();
        }

        @Override
        protected LatLng doInBackground(Object... objs) {
            try {
                JSONObject object = new JSONObject(objs[0].toString());
                JSONArray results = (JSONArray) object.get("results");
                if (results.length() > 0) {
                    JSONObject res = (JSONObject) results.get(0);
                    JSONObject geometry = res.getJSONObject("geometry");
                    JSONObject location = geometry.getJSONObject("location");

                    LatLng coords = new LatLng(location.getDouble("lat"), location.getDouble("lng"));
                    return coords;
                }
                return null;
            } catch (JSONException e) {
                return null;
            }
        }
    }

    private class ParserTask extends AsyncTask<Object, Integer, List<List<HashMap<String, String>>>> {

        // Parsing the data in non-ui thread
        @Override
        protected List<List<HashMap<String, String>>> doInBackground(Object... jsonData) {

            JSONObject jObject;
            List<List<HashMap<String, String>>> routes = null;

            try {
                jObject = new JSONObject(jsonData[0].toString());
                DirectionsJSONParser parser = new DirectionsJSONParser();

                routes = parser.parse(jObject);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return routes;
        }

        @Override
        protected void onPostExecute(List<List<HashMap<String, String>>> result) {
            ArrayList points = null;
            PolylineOptions lineOptions = null;
            MarkerOptions markerOptions = new MarkerOptions();

            for (int i = 0; i < result.size(); i++) {
                points = new ArrayList();
                lineOptions = new PolylineOptions();

                List<HashMap<String, String>> path = result.get(i);

                for (int j = 0; j < path.size(); j++) {
                    HashMap<String, String> point = path.get(j);

                    double lat = Double.parseDouble(point.get("lat"));
                    double lng = Double.parseDouble(point.get("lng"));
                    LatLng position = new LatLng(lat, lng);

                    points.add(position);
                }

                lineOptions.addAll(points);
                lineOptions.width(12);
                lineOptions.color(Color.RED);
                lineOptions.geodesic(true);

            }

// Drawing polyline in the Google Map for the i-th route
            map.addPolyline(lineOptions);
        }
    }
}
