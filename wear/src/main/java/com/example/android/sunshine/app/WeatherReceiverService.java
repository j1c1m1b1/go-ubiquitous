package com.example.android.sunshine.app;

import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.concurrent.TimeUnit;

public class WeatherReceiverService extends WearableListenerService
        implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String WEAR_PATH = "/weather_info";

    private static final String LOW_TEMP = "low";
    private static final String HIGH_TEMP = "high";
    private static final String IMAGE = "image";
    private static final String TIMESTAMP = "timestamp";
    private static final String WATCH_FACE_PATH = "/weather_info/wear";

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.i(this.getClass().getSimpleName(), "Data changed");

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
        }
        if (!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult =
                    mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

            if (!connectionResult.isSuccess()) {
                Log.e(this.getClass().getSimpleName(), "Failed to connect to GoogleApiClient.");
                return;
            }
        }

        for(DataEvent dataEvent : dataEvents)
        {
            if(dataEvent.getType() == DataEvent.TYPE_CHANGED)
            {
                DataMap dataMap =
                        DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                String path = dataEvent.getDataItem().getUri().getPath();
                if(path.equals(WEAR_PATH))
                {
                    double low = dataMap.getDouble(LOW_TEMP);
                    double high = dataMap.getDouble(HIGH_TEMP);
                    byte[] bitmapBytes = dataMap.getByteArray(IMAGE);

                    sendDataToWatchFace(low, high, bitmapBytes);
                    break;
                }
            }
        }

    }

    private void sendDataToWatchFace(double low, double high, byte[] byteArray)
    {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WATCH_FACE_PATH);
        putDataMapRequest.getDataMap().putDouble(LOW_TEMP, low);
        putDataMapRequest.getDataMap().putDouble(HIGH_TEMP, high);
        putDataMapRequest.getDataMap().putByteArray(IMAGE, byteArray);
        putDataMapRequest.getDataMap().putLong(TIMESTAMP, System.currentTimeMillis());


        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataMapRequest.asPutDataRequest())
                .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (Log.isLoggable(this.getClass().getSimpleName(), Log.DEBUG)) {
                            Log.d(this.getClass().getSimpleName(),
                                    "putDataItem result status: " + dataItemResult.getStatus());
                        }
                    }
                });
    }

    @Override
    public void onConnected(Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }
}
