package com.precisionag.watershedviewer;

import java.text.DecimalFormat;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.precisionag.watershedviewer.Field;

public class Field {
	//bitmap represents rasterized elevation data
	Bitmap watershedBitmap;
	
	//defines the edges of the field
	LatLngBounds fieldBounds;
	LatLng sw;
	LatLng ne;
	
	//constructor method
	public Field(Bitmap bitmap, LatLng southwest, LatLng northeast, double minHeight, double maxHeight) {
		watershedBitmap = bitmap;
		sw = southwest;
		ne = northeast;
		fieldBounds = new LatLngBounds(sw, ne);
	}
	
	public void setBounds(LatLngBounds bounds) {
		fieldBounds = bounds;
	}
	
	public void setNortheast(LatLng northeast) {
		ne = northeast;
	}
	
	public void setSouthwest(LatLng southwest) {
		sw = southwest;
	}
	
	//creates an overlay view of the field on the specified map object
	public GroundOverlay createOverlay(GoogleMap map) {
		GroundOverlay groundOverlay = map.addGroundOverlay(new GroundOverlayOptions()
	     .image(BitmapDescriptorFactory.fromBitmap(watershedBitmap))
	     .positionFromBounds(fieldBounds)
	     .transparency(0));
		groundOverlay.setVisible(true);
		return groundOverlay;
	}
	
	//returns catchment ID of given point
	public double catchmentFromLatLng(LatLng point) {
		if (fieldBounds.contains(point)) {
			//use linear interpolation to figure out which pixel to get data from
			//should be accurate since fields <= ~1 mile wide
			double north = ne.longitude;
			double east = ne.latitude;
			double south = sw.longitude;
			double west = sw.latitude;
			
			int width = watershedBitmap.getWidth();
			int height = watershedBitmap.getHeight();
			
			double x = (double)width*(point.latitude-west)/(east-west);
			double y = (double)height*(point.longitude-south)/(north-south);
			
			//retrieve packed int
			int catchmentIdPixel = watershedBitmap.getPixel((int)x, (int)y);
			double catchmentId = catchmentIdPixel / 50;
			return catchmentId;
			
		}
		else {
			//point isn't in the field
			return 0.0;
		}
	}	
}