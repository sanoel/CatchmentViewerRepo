package com.precisionag.watershedviewer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.precisionag.watershedviewer.Field;

public class MainActivity extends Activity {

	private static final int PAUSE = 1;
	private static final int PLAY = 2;

	GroundOverlay prevoverlay;

	Field field;
	List<LatLng> pathPoints;
	PolylineOptions rectOptions;
	Polyline pathTrace;
	LatLng userLocation;
	int mode = PAUSE;
	double density = 0.0;
	LocationManager locationManager;
	Context context = this;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main); 
		Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.field);
		MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
		GoogleMap map = mapFragment.getMap();
		map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
		map.setMyLocationEnabled(true);
		UiSettings uiSettings = map.getUiSettings();

		uiSettings.setRotateGesturesEnabled(false);
		uiSettings.setTiltGesturesEnabled(false);
		uiSettings.setZoomControlsEnabled(false);

		field = new Field(bitmap, new LatLng(0.0, 0.0), new LatLng(0.0, 0.0), 0.0, 0.0);
		mode = 0;
		density = getResources().getDisplayMetrics().density;
		String default_field = "field";
		readDataFile(field, default_field);
		prevoverlay = field.createOverlay(map);
		locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
					context);

			// set title
			alertDialogBuilder.setTitle("GPS is not enabled");

			// set dialog message
			alertDialogBuilder
			.setMessage("Please enable GPS!")
			.setCancelable(false)
			.setPositiveButton("Exit",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					// if this button is clicked, close
					// current activity
					MainActivity.this.finish();
				}
			})
			.setNegativeButton("GPS Settings",new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog,int id) {
					// if this button is clicked, just close
					// the dialog box and do nothing
					startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
				}
			});

			// create alert dialog
			AlertDialog alertDialog = alertDialogBuilder.create();

			// show it
			alertDialog.show();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		// Make an action bar and don't display the app title
		ActionBar actionBar = getActionBar();
		getActionBar().setDisplayShowTitleEnabled(false);
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

		// Build list of available field files from assets folder
		AssetManager assetManager = getAssets();
		String fileArray[] = null;
		try {
			fileArray = assetManager.list("");
		} catch (IOException e) {
			e.printStackTrace();
		}
		final ArrayList<String> fileList = new ArrayList<String>(Arrays.asList(fileArray));
		for(int i = fileList.size() - 1; i >= 0; i--) {
			// this apparently a way to perform a case-insensitive string.contains
			if (Pattern.compile(Pattern.quote(".latlng"), Pattern.CASE_INSENSITIVE).matcher(fileList.get(i)).find()) {
				//split the extension from the string and record it in the list as a drop down item (including the file extension would be ugly)
				String[] splitFile = fileList.get(i).split("\\.(?=[^\\.]+$)");
				fileList.set(i, splitFile[0]);
			}
			else {
				//remove non .latlng files from list of dropdown items
				fileList.remove(i);
			}
		}	
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_item, fileList);
		Spinner spinner = (Spinner) findViewById(R.id.field_spinner);
		spinner.setAdapter(adapter);
		ActionBar.OnNavigationListener navListen = new OnNavigationListener() {

			@Override
			public boolean onNavigationItemSelected(int position, long itemId) {
				String selectedField = fileList.get(position);
				// get the drawable resource ID matching the dropdown item selected
				int bitmapResource = getResources().getIdentifier(selectedField, "drawable", getBaseContext().getPackageName());
				Bitmap bitmap = BitmapFactory.decodeResource(getResources(), bitmapResource);
				field = new Field(bitmap, new LatLng(0.0, 0.0), new LatLng(0.0, 0.0), 0.0, 0.0);

				// Read appropriate files for the field selected from the dropdown and re-overlay that field
				readDataFile(field, selectedField);
				prevoverlay.remove();
				prevoverlay = createOverlay(bitmap, field.fieldBounds);
				return false;
			}
		};
		actionBar.setListNavigationCallbacks(adapter, navListen);
		return true;
	}

	//takes a bitmap, latitude/longitude bounds, and a map to create a map overlay
	//this has been duplicated in the Field class
	private GroundOverlay createOverlay(Bitmap overlayBitmap, LatLngBounds bounds) {
		MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
		BitmapDescriptor image = BitmapDescriptorFactory.fromBitmap(overlayBitmap);
		GoogleMap map = mapFragment.getMap();
		GroundOverlay groundOverlay = map.addGroundOverlay(new GroundOverlayOptions()
		.image(image)
		.positionFromBounds(bounds)
		.transparency(0));
		groundOverlay.setVisible(true);
		return groundOverlay;
	}

	private void readDataFile(Field field, String field_name) {
		try {
			//read data from string
			AssetManager am = getApplicationContext().getAssets();
			BufferedReader dataIO = new BufferedReader(new InputStreamReader(am.open(field_name + ".latlng")));
			String dataString = null;
			dataString = dataIO.readLine();
			double north = Double.parseDouble(dataString);
			dataString = dataIO.readLine();
			double east = Double.parseDouble(dataString);
			dataString = dataIO.readLine();
			double south = Double.parseDouble(dataString);
			dataString = dataIO.readLine();
			double west = Double.parseDouble(dataString);

			LatLng northEast = new LatLng(north, east);
			LatLng southWest = new LatLng(south, west);

			//set corresponding parameters in field
			field.setBounds(new LatLngBounds(northEast, southWest));
			field.setNortheast(northEast);
			field.setSouthwest(southWest);

			dataIO.close();
		}
		catch  (IOException e) {
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle Action Bar Item selection
		
		//this doesn't seem to work. the menu item icons don't change
		if (item.getItemId() == R.id.menu_start_pause) {
			if (mode == PAUSE) {
				item.setIcon(R.drawable.av_play);
				mode = PLAY;
			}
			else if (mode == PLAY)  {
				item.setIcon(R.drawable.av_pause);
				mode = PAUSE;
			}			else if (item.getItemId() == R.id.menu_reset) {
				item.setIcon(R.drawable.av_play);
				mode = PAUSE;
				pathPoints.clear();
			}		}
		return false;
	}
	LocationListener locationListener = new LocationListener() {		public void onLocationChanged(Location location) {			// Called when a new location is found by the network location provider.			userLocation = new LatLng(location.getLatitude(), location.getLongitude());
			if (mode == PLAY) {				if (pathPoints.size() >= 200) {					pathPoints.remove(0);					pathPoints.add(userLocation);				}				else {					pathPoints.add(userLocation);				}				pathTrace.setPoints(pathPoints);			}			double catchmentDouble = field.catchmentFromLatLng(userLocation);			String catchmentString = new DecimalFormat("#.#").format(Math.abs(catchmentDouble));			String CatchmentText;			TextView CatchmentTextView = (TextView) findViewById(R.id.text);
			if (catchmentDouble == 0.0) {				CatchmentText = "You are not in the field.";			}			else {				//String catchmentString = new DecimalFormat("#.#").format(Math.abs(catchmentDouble));				CatchmentText = "Your Catchment: " + catchmentString;			}			CatchmentTextView.setText(CatchmentText);	  		}
		public void onStatusChanged(String provider, int status, Bundle extras) {}
		public void onProviderEnabled(String provider) {}
		public void onProviderDisabled(String provider) {}	};
}