package com.example.mallmate40;  // עדכן לפי שם החבילה שלך

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.List;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapsActivity";
    private GoogleMap mMap;
    private Path currentPath; // אם אתה רוצה להציג את המסלול שנאסף

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // קבל את ה-SupportMapFragment ואתחל את המפה
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // קבל את ה-Path מה-Intent אם הועבר (אופציונלי)
        if (getIntent().hasExtra("current_path")) {
            // אם אתה מעביר את ה-Path בין Activities, צריך לממש Parcelable ב-Path
            // currentPath = getIntent().getParcelableExtra("current_path");
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // הפעל שכבת מיקום משתמש (אם יש הרשאות)
        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException e) {
            Log.e(TAG, "No location permissions: " + e.getMessage());
        }

        // הגדר מיקום התחלתי (לדוגמה: תל אביב)
        LatLng telAviv = new LatLng(32.0853, 34.7818);
        mMap.addMarker(new MarkerOptions().position(telAviv).title("תל אביב"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(telAviv, 12));

        // אם יש מסלול, הצג אותו
        displayPathIfAvailable();
    }

    private void displayPathIfAvailable() {
        if (currentPath != null && mMap != null) {
            List<Point> points = currentPath.getPoints();

            if (points.size() > 1) {
                // הכן קו מסלול
                PolylineOptions polylineOptions = new PolylineOptions();

                for (Point point : points) {
                    // המר את קואורדינטות המבנה ל-LatLng (יתכן שתצטרך להמיר)
                    LatLng position = new LatLng(point.getX(), point.getY());
                    polylineOptions.add(position);
                }

                // הוסף את המסלול למפה
                mMap.addPolyline(polylineOptions.width(5).color(0xFF0000FF)); // כחול

                // התמקד במסלול
                if (points.size() > 0) {
                    Point lastPoint = points.get(points.size() - 1);
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(lastPoint.getX(), lastPoint.getY()), 17));
                }
            }
        }
    }
}