package com.globallogic.rtsptestapp.navigation;

import android.app.Presentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.location.Location;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.Surface;
import android.view.View;

import com.globallogic.rtsptestapp.R;
import com.globallogic.rtsptestapp.RtspServer;
import com.globallogic.rtsptestapp.SessionBuilder;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.services.android.navigation.ui.v5.NavigationView;
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions;
import com.mapbox.services.android.navigation.ui.v5.OnNavigationReadyCallback;
import com.mapbox.services.android.navigation.ui.v5.camera.DynamicCamera;
import com.mapbox.services.android.navigation.ui.v5.listeners.NavigationListener;
import com.mapbox.services.android.navigation.v5.location.replay.ReplayRouteLocationEngine;
import com.mapbox.services.android.navigation.v5.milestone.Milestone;
import com.mapbox.services.android.navigation.v5.milestone.MilestoneEventListener;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgressState;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

import static android.content.Context.DISPLAY_SERVICE;
import static com.mapbox.mapboxsdk.Mapbox.getApplicationContext;

public class NavigationController implements OnNavigationReadyCallback, NavigationListener,
        ProgressChangeListener, MilestoneEventListener {


    private static final double ORIGIN_LONGITUDE = -3.714873;
    private static final double ORIGIN_LATITUDE = 40.397389;
    private static final double DESTINATION_LONGITUDE = -3.166243;
    private static final double DESTINATION_LATITUDE = 40.650514;

    public static final int PRESENTATION_WIDTH = 300;
    public static final int PRESENTATION_HEIGHT = 300;
    public static final int PRESENTATION_FRAMERATE = 30;
    public static final int PRESENTATION_BITRATE = 6000000;
    private static final int PRESENTATION_DENSITY = 960;



    private static final String TAG = NavigationController.class.getSimpleName();
    private List<NavigationView> mNavigationViewList;
    private FragmentActivity mContext;
    private Presentation mPresentation;
    private DirectionsRoute mDirectionsRoute;
    private boolean isRouteFetched = false;
    private int readyViewsCount = 0;


    private Surface inputSurface;
    private boolean socketStarted = false;
    VirtualDisplay display;



    public NavigationController(FragmentActivity fragmentActivity) {
        this.mContext = fragmentActivity;
        mNavigationViewList = new ArrayList<>(2);
        createPresentation();
        startPresentation();

    }

    public NavigationView createNavigationView(View container) {


        NavigationView view = new NavigationView(mContext);
        mNavigationViewList.add(view);

        return view;
    }

    public void startPresentation() {
        mPresentation.show();
    }

    private void createPresentation() {

        DisplayManager dm = (DisplayManager) getApplicationContext().getSystemService(DISPLAY_SERVICE);

        display = dm.createVirtualDisplay("Recording Display", PRESENTATION_WIDTH,
                PRESENTATION_HEIGHT, PRESENTATION_DENSITY, null, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                null, null);

        if (mPresentation != null)
            throw new RuntimeException("Presentation already created");

        NavigationView navigationView = createNavigationView(null);

        mPresentation = new Presentation(mContext, display.getDisplay());
        mPresentation.setContentView(navigationView);

        // Sets the port of the RTSP server to 1234
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.putString(RtspServer.KEY_PORT, String.valueOf(12345));
        editor.apply();


    }


    private synchronized void fetchRoute(Point origin, Point destination) {
        if (isRouteFetched)
            return;
        isRouteFetched = true;
        NavigationRoute.builder(mContext)
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new SimplifiedCallback() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        Log.w(TAG, "onResponse: startNavigation" );
                        mDirectionsRoute = response.body().routes().get(0);
                        startNavigation();
                    }
                });
    }

    private void startNavigation() {
        Log.w(TAG, "startNavigation: " );
        if (mDirectionsRoute == null) {
            return;
        }
        NavigationViewOptions options = NavigationViewOptions.builder()

                .directionsRoute(mDirectionsRoute)
                .shouldSimulateRoute(true)
                .navigationListener(this)
                .progressChangeListener(this)
                .milestoneEventListener(this)
                .locationEngine(new ReplayRouteLocationEngine())
                .build();

        for (NavigationView nav : mNavigationViewList) {
            nav.startNavigation(options);
        }

        // DONT TOUCH! This is magic!
        mNavigationViewList.get(1).retrieveMapboxNavigation().setCameraEngine(new DynamicCamera(mNavigationViewList.get(0).retrieveNavigationMapboxMap().retrieveMap()));

        startRecording();
    }

    private void stopNavigation() {
        updateWasNavigationStopped(true);
        stopRecording();
    }

    public void updateWasNavigationStopped(boolean wasNavigationStopped) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(mContext.getString(R.string.was_navigation_stopped), wasNavigationStopped);
        editor.apply();
    }

    @Override
    public synchronized void onNavigationReady(boolean isRunning) {

        Log.e(TAG, "onNavigationReady "+isRunning);
        Point origin = Point.fromLngLat(ORIGIN_LONGITUDE, ORIGIN_LATITUDE);
        Point destination = Point.fromLngLat(DESTINATION_LONGITUDE, DESTINATION_LATITUDE);

        readyViewsCount++;

        // Fetch route after all views are ready
        if (readyViewsCount == mNavigationViewList.size()) {
            fetchRoute(origin, destination);
        }

    }

    @Override
    public void onCancelNavigation() {

        for (NavigationView nav : mNavigationViewList) {
            nav.stopNavigation();
        }
        stopNavigation();
        startNavigation();
    }

    @Override
    public void onNavigationFinished() {

    }

    @Override
    public void onNavigationRunning() {

    }

    @Override
    public void onMilestoneEvent(RouteProgress routeProgress, String instruction, Milestone milestone) {

    }

    @Override
    public void onProgressChange(Location location, RouteProgress routeProgress) {

        if (routeProgress.currentState() == RouteProgressState.ROUTE_ARRIVED) {
            stopNavigation();
            startNavigation();
        }
    }


    public void initialize() {
        for (NavigationView nav : mNavigationViewList) {
            nav.initialize(this);
        }
    }

    public void onCreate(@Nullable Bundle savedInstanceState) {

        for (NavigationView nav : mNavigationViewList) {
            nav.onCreate(savedInstanceState);
        }
    }

    public void onStart() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onStart();
        }
    }

    public void onResume() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onResume();
        }
    }

    public void onSaveInstanceState(@NonNull Bundle outState) {
        for (NavigationView nav : mNavigationViewList) {
            nav.onSaveInstanceState(outState);
        }
    }

    public void onRestoreInstanceState(@Nullable Bundle savedInstanceState) {
        for (NavigationView nav : mNavigationViewList) {
            nav.onRestoreInstanceState(savedInstanceState);
        }
    }

    public void onPause() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onPause();
        }
        stopRecording();
    }

    public void onStop() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onStop();
        }
    }

    public void onLowMemory() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onLowMemory();
        }
    }

    public void onDestroy() {
        for (NavigationView nav : mNavigationViewList) {
            nav.onDestroy();
        }
    }

    public void startRecording() {
        Log.d(TAG, "Start recording");
        socketStarted=true;
        // Configures the SessionBuilder
        SessionBuilder.getInstance()
                .setVirtualDisplay(display)
                .setPreviewOrientation(90)
                .setContext(getApplicationContext())
                .setVideoEncoder(SessionBuilder.VIDEO_H264);

        mContext.startService(new Intent(mContext, RtspServer.class));

    }

    public void stopRecording() {
        Log.d(TAG, "stop recording");
        releaseEncoders();
    }


    private void releaseEncoders() {
        if (inputSurface != null) {
            inputSurface.release();
            inputSurface = null;
        }
    }

    public boolean isRecording() {
        return socketStarted;
    }

}
