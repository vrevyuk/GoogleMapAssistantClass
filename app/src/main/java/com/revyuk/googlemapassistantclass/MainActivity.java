package com.revyuk.googlemapassistantclass;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;


public class MainActivity extends ActionBarActivity implements GoogleMapHelper.CallbackMapHelper, View.OnClickListener {
    Context context = this;
    SupportMapFragment mapFragment;
    GoogleMapHelper helper;
    EditText editText;
    int mapclickstate;
    LatLng beginPoint, finishPoint;

    public static final LatLng myhome = new LatLng(49.953447, 36.260326);
    public static final LatLng lviv = new LatLng(49.842730346390404, 24.03391107916832);
    public static final LatLng ashan = new LatLng(49.77361297607422,24.010786056518555);

    @Override
    public void state(String message) {
        if(message.equals("geocoding_OK")) {
            String[] items = new String[helper.getGeoResolvedAddress().size()];
            for(int i=0; i<helper.getGeoResolvedAddress().size(); i++) {
                items[i] = helper.getGeoResolvedAddress().get(i).formatted_address;
            }
            new AlertDialog.Builder(this).setItems(items, new MyOnClickListener()).show();
        } else new AlertDialog.Builder(context).setMessage(message).setPositiveButton("ok", null).setCancelable(false).show();
    }

    class MyOnClickListener implements DialogInterface.OnClickListener {

        @Override
        public void onClick(DialogInterface dialog, int which) {
            helper.showAddress(which);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();

        Log.d("XXX", "Create");
        mapFragment = new SupportMapFragment();
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(GoogleMap googleMap) {
                googleMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
                    @Override
                    public void onMapLongClick(LatLng latLng) {
                        switch (mapclickstate) {
                            case 1:
                                helper.addWaypoint(latLng, "Anchor #"+(helper.waypointsCount()+1), "Intermediate anchor");
                                mapclickstate = 0;
                                break;
                            case 2:
                                beginPoint = latLng;
                                mapclickstate = 3;
                                Toast.makeText(MainActivity.this, "LongClick to map for add finish point", Toast.LENGTH_SHORT).show();
                                break;
                            case 3:
                                finishPoint = latLng;
                                helper.traceRoute(beginPoint, "Start", "start position", finishPoint, "Finish", "finish position");
                                break;
                        }
                    }
                });
                helper = new GoogleMapHelper(context, googleMap);
                helper.setDraggableAnchor(true);
            }
        });
        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, mapFragment)
                .commit();
        editText = (EditText) findViewById(R.id.edit_text);
        findViewById(R.id.button1).setOnClickListener(this);
        findViewById(R.id.button2).setOnClickListener(this);
        findViewById(R.id.button3).setOnClickListener(this);
        findViewById(R.id.button4).setOnClickListener(this);
        findViewById(R.id.button5).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button1:
                helper.traceRoute(myhome, "Start", "start position", lviv, "Finish", "finish position");
                break;
            case R.id.button2:
                helper.clearRoutes();
                mapclickstate = 0;
                break;
            case R.id.button3:
                mapclickstate = 1;
                Toast.makeText(this, "LongClick to map for add intermediate point", Toast.LENGTH_SHORT).show();
                break;
            case R.id.button4:
                mapclickstate = 2;
                Toast.makeText(this, "LongClick to map for add start point", Toast.LENGTH_SHORT).show();
                break;
            case R.id.button5:
                helper.getLocationByAddressName(editText.getText().toString());
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if(mapclickstate > 0 ) {
            mapclickstate = 0;
            return;
        } else super.onBackPressed();
    }
}
