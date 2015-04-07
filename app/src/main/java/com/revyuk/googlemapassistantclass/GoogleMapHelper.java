package com.revyuk.googlemapassistantclass;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.location.*;
import android.location.Location;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import org.apache.http.Header;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.revyuk.googlemapassistantclass.models.*;


/**
 *
 * Created by Vitaly on 05.04.2015.
 *
 */

public class GoogleMapHelper {
    private final Context context;
    private CallbackMapHelper callback;
    private GoogleMap googleMap;
    private AsyncHttpClient httpClient;
    private AsyncHttpResponseHandler handler;
    private Waypoints startPoint = new Waypoints();
    private Waypoints stopPoint = new Waypoints();
    private Gson gson;
    private RouteResponse routeResponse;
    private GeocodingResponse geocodingResponse;
    private boolean draggableAnchor;
    private ArrayList<Waypoints> waypoints = new ArrayList<>();


    public class Waypoints {
        public Marker marker;
        public String id;
        public LatLng position;
        public String title;
        public String snippet;
    }
    /**
     * Constants
     */
    public static final int MAX_WAYPOINTS = 6;
    public static final String ROUTES_API_URL = "http://maps.googleapis.com/maps/api/directions/json";
    public static final String GEOCODING_API_URL = "http://maps.googleapis.com/maps/api/geocode/json";

    /**
     * PUBLIC CONSTRUCTOR
     *
     * required permission:
     * <uses-permission android:name="android.permission.INTERNET"/>
     * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
     * <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
     * <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
     *
     * @param context application activity context
     * @param googleMap GoogleMap
     */
    public GoogleMapHelper(Context context, GoogleMap googleMap) {
        this.context = context;
        this.googleMap = googleMap;
        try {
            callback = (CallbackMapHelper) context;
        } catch (ClassCastException e) {
            throw new ClassCastException("You have implement GoogleMapHelper.CallbackMapHelper interface.");
        }
        init();
    }

    /**
     * Callback interface
     * returned message of results
     */

    public interface CallbackMapHelper {
        void state(String message);
    }

    /**
     * PUBLIC METHODS
     */

    /**
     *
      * @return coordinates of my location
     */
    public LatLng getMyLocation() {
        LocationManager locationManager;
        Boolean isGPSProvider=false, isNETWORKProvider=false;
        android.location.Location location;
        LatLng curr_pos = new LatLng(0,0);

        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if(locationManager!=null) {
            isGPSProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNETWORKProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if(isNETWORKProvider) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 10, new HelperLocationListener());
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if(location!=null) {
                    curr_pos = new LatLng(location.getLatitude(), location.getLongitude());
                }
            } else if(isGPSProvider) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 10, new HelperLocationListener());
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if(location!=null) {
                    curr_pos = new LatLng(location.getLatitude(), location.getLongitude());
                }
            } else {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                context.startActivity(intent);
            }
        } else {
            Log.d("XXX", "Location manager is not found.");
        }
        return curr_pos;
    }

    /**
     * Resolve address via text to LatLng coordinates list
     * @param address postal address
     */
    public void getLocationByAddressName(String address) {
        if(address == null) address = "";
        RequestParams params = new RequestParams();
        params.add("address",address);
        params.add("sensor","true");
        params.add("region","UA");
        params.add("language", "UA");
        params.setContentEncoding("UTF-8");
        Log.d("XXX", GEOCODING_API_URL+"?"+params.toString());
        googleRequest(2, GEOCODING_API_URL, params);
    }

    /**
     * List of google geocoding result https://developers.google.com/maps/documentation/geocoding/
     * @return list
     */
    public List<GeocodingResult> getGeoResolvedAddress () {
        if(geocodingResponse == null) return null;
        return Arrays.asList(geocodingResponse.results);
    }

    /**
     * Move camera on selected address
     * @param indexOfList void
     */
    public void showAddress(int indexOfList) {
        if(geocodingResponse == null || indexOfList >= geocodingResponse.results.length) return;

        Bounds bounds = geocodingResponse.results[indexOfList].geometry.viewport;
        LatLngBounds latLngBounds = new LatLngBounds.Builder()
                .include(new LatLng(bounds.northeast.lat, bounds.northeast.lng))
                .include(new LatLng(bounds.southwest.lat, bounds.southwest.lng)).build();
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(latLngBounds, 100);
        googleMap.moveCamera(cameraUpdate);
    }

    /**
     * see below
     */
    public void traceRoute() {
        if(startPoint != null && stopPoint != null) {
            traceRoute(startPoint.position, startPoint.title, startPoint.snippet, stopPoint.position, stopPoint.title, stopPoint.snippet);
        }
    }

    /**
     *
     * @param beginPoint LatLng coordinates of begin
     * @param beginTitle title for first anchor
     * @param beginSnippet subtitle for first anchor
     * @param endPoint LatLng coordinates of end of route
     * @param endTitle title for last anchor
     * @param endSnippet subtitle for last anchor
     */
    public void traceRoute(LatLng beginPoint, String beginTitle, String beginSnippet, LatLng endPoint, String endTitle, String endSnippet) {
        if(startPoint.id == null) { startPoint.position = beginPoint; startPoint.title = beginTitle; startPoint.snippet = beginSnippet; }
        if(stopPoint.id == null) { stopPoint.position = endPoint; stopPoint.title = endTitle; stopPoint.snippet = endSnippet; }

        RequestParams params = new RequestParams();
        params.setContentEncoding("UTF-8");
        params.add("origin", beginPoint.latitude + "," + beginPoint.longitude);
        params.add("destination", endPoint.latitude+","+endPoint.longitude);
        params.add("sensor", "false");
        params.add("mode", "driving");
        params.add("alternatives", "true");
        params.add("region", "UA");

        if(waypoints.size()>0) {
            List<String> str_points = new ArrayList<>();
            for(Waypoints point: waypoints) {
                str_points.add(point.position.latitude+","+point.position.longitude);
            }
            params.add("waypoints", TextUtils.join("|", str_points));
        }
        googleRequest(1, ROUTES_API_URL, params);
    }


    /**
     * clear all googlemap, points, results
     */
    public void clearRoutes() {
        startPoint = new Waypoints(); stopPoint = new Waypoints(); routeResponse = null; googleMap.clear(); waypoints.clear();
    }

    /**
     * Add new point in current route and map
     * @param point LatLng coordinate of new point
     * @param title title of new point
     * @param snippet subtitle of new point
     */
    public void addWaypoint(LatLng point, String title, String snippet) {
        if(startPoint != null && stopPoint != null) {
            if(waypoints.size() < MAX_WAYPOINTS) {
                Waypoints wp = new Waypoints();
                wp.position = point;
                wp.title = title;
                wp.snippet = snippet;
                waypoints.add(wp);
                traceRoute();
            } else { callback.state("Too many waypoints. max is 6."); }
        } else { callback.state("Cannot assigned to start and/or stop points"); }
    }

    /**
     * Return list all intermediate point of route
     * @return list waipoints
     */
    public List<Waypoints> listWaypoints() {
        return waypoints;
    }

    /**
     * Remove waypoint from list and map
     * @param id String value from list of waypoints
     */
    public void removeWaypoint(String id) {
        boolean removed=false;
        for(Waypoints w:waypoints) {
            if(w.id.equals(id)) removed = waypoints.remove(w);
        }
        if(removed) traceRoute();
    }

    /**
     * Set permission for drag anchors on map. Set/unset self listener for GoogleMap.OnMarkerDragListener
     * @param draggable true or false
     */
    public void setDraggableAnchor(boolean draggable) {
        if(googleMap != null) {
            draggableAnchor = draggable;
            if(draggableAnchor) {
                googleMap.setOnMarkerDragListener(new HelperOnMarkerDragListener());
            } else {
                googleMap.setOnMarkerDragListener(null);
            }
        }
    }

    /**
     * Count of found routes
     * @return int
     */
    public int routesCount() {
        return routeResponse.routes.length;
    }

    /**
     * Get list of found routes
     * @return
     */
    public List<Routes> getRoutes() {
        return Arrays.asList(routeResponse.routes);
    }

    /**
     * Get count of present waypoints
     * @return int
     */
    public int waypointsCount() {
        return waypoints.size();
    }

    /**
     * Encoder google maps polyline string
     * @param encoded input encoded string
     * @return list of LatLng points
     */

    public static List<LatLng> decodePolyline(String encoded) {
        // this is not my function. honestly taken on StackOverflow )))

        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }

    /** PRIVATE SECTION
     *
     *
     * initialisation all inner variables and objects
     */
    private void init() {
        gson = new GsonBuilder().serializeNulls().create();
        if(httpClient == null) httpClient = new AsyncHttpClient();
        httpClient.setConnectTimeout(5000);
        httpClient.setResponseTimeout(5000);
        httpClient.setURLEncodingEnabled(true);
        if(googleMap != null) {
            googleMap.setMyLocationEnabled(true);
            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
            googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(getMyLocation(), 12)));
        } else callback.state("Google map cannot found");
    }

    /**
     * Draw routes and anchors on google map
     */
    private void drawOnMap() {
        if(routeResponse != null) {
            //Log.d("XXX", routeResponse.status);
            //Log.d("XXX", "size:"+routeResponse.routes.length);
            googleMap.clear();
            for (Routes route:routeResponse.routes) {

                List<LatLng> points = decodePolyline(route.overview_polyline.points);
                LatLngBounds bounds = LatLngBounds.builder()
                        .include(new LatLng(route.bounds.northeast.lat, route.bounds.northeast.lng))
                        .include(new LatLng(route.bounds.southwest.lat, route.bounds.southwest.lng))
                        .build();
                CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, 100);
                googleMap.moveCamera(cameraUpdate);

                PolylineOptions polylineOptions = new PolylineOptions();
                polylineOptions.color(Color.RED).width(3).zIndex(100).addAll(points);
                googleMap.addPolyline(polylineOptions);
            }

            MarkerOptions markerOptions;

            markerOptions = new MarkerOptions();
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .position(startPoint.position).title(startPoint.title).snippet(startPoint.snippet).draggable(draggableAnchor);
            startPoint.id = googleMap.addMarker(markerOptions).getId();

            markerOptions = new MarkerOptions();
            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .position(stopPoint.position).title(stopPoint.title).snippet(stopPoint.snippet).draggable(draggableAnchor);
            stopPoint.id = googleMap.addMarker(markerOptions).getId();

            for(Waypoints point: waypoints) {
                markerOptions = new MarkerOptions();
                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                        .position(point.position).title(point.title).snippet(point.snippet).draggable(draggableAnchor);
                Marker marker = googleMap.addMarker(markerOptions);
                marker.showInfoWindow();
                point.id = marker.getId();
            }
        }
    }

    /**
     *
     * @param requestId request id
     * @param url  http://maps.googleapis.com/maps/api/directions/json
     * @param params GET params for request
     */
    private void googleRequest(int requestId, String url, RequestParams params) {
        if(networkState()) new Thread(new GoogleRequest(url, params, new HelperAsyncHttpResponseHandler(requestId))).start();
    }

    /**
     * Check network state on device
     * @return true internet is available else false
     */
    private boolean networkState() {
        boolean ret = false;
        ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager!=null) {
            if(manager.getActiveNetworkInfo().isConnected()) ret = true;
        };
        if(!ret) callback.state("Check and activate available network connections.");
        return ret;
    }

    /**
     * runnable class for creating http request in other thread
     */
    private class GoogleRequest implements Runnable {
        AsyncHttpResponseHandler responseHandler;
        String url;
        RequestParams params;

        public GoogleRequest(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
            this.responseHandler = responseHandler;
            this.url = url;
            this.params = params;
        }

        @Override
        public void run() {
            httpClient.get(url, params, responseHandler);
        }
    }

    /**
     * class for async http response handler
     */
    private class HelperAsyncHttpResponseHandler extends AsyncHttpResponseHandler {
        int requestId;
        public HelperAsyncHttpResponseHandler(int requestId) {
            this.requestId = requestId;
        }

        @Override
        public void onSuccess(int i, Header[] headers, byte[] bytes) {
            String response = new String(bytes);
            //Log.d("XXX", "SUCCES I: " + i + " body: " + response);
            switch (requestId) {
                case 1:
                    try {
                        routeResponse = gson.fromJson(response, RouteResponse.class);
                        drawOnMap();
                        callback.state(routeResponse.status);
                    } catch (JsonSyntaxException e) {
                        e.printStackTrace();
                    }
                    break;
                case 2:
                    try {
                        geocodingResponse = gson.fromJson(response, GeocodingResponse.class);
                        callback.state("geocoding_"+geocodingResponse.status);
                        Log.d("XXX", "size: " + geocodingResponse.results.length + " " + geocodingResponse.results[0].formatted_address);
                    } catch (JsonSyntaxException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }

        @Override
        public void onFailure(int i, Header[] headers, byte[] bytes, Throwable throwable) {
            callback.state(new String(bytes));
        }
    }

    /**
     * implements class for OnMarkerDragListener
     */
    private class HelperOnMarkerDragListener implements GoogleMap.OnMarkerDragListener {

        @Override
        public void onMarkerDragStart(Marker marker) {
        }

        @Override
        public void onMarkerDrag(Marker marker) {
        }

        @Override
        public void onMarkerDragEnd(Marker marker) {
            for(Waypoints w:waypoints) {
                if(w.id.equals(marker.getId())) {
                    w.position = marker.getPosition();
                }
            }
            if(startPoint.id.equals(marker.getId())) startPoint.position = marker.getPosition();
            if(stopPoint.id.equals(marker.getId())) stopPoint.position = marker.getPosition();
            traceRoute();
        }
    }

    private class HelperLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {

        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {

        }
    }

}
