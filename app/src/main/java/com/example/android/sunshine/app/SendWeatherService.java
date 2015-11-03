package com.example.android.sunshine.app;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author Julio Mendoza on 11/3/15.
 */
public class SendWeatherService extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String ACK_MESSAGE_KEY = "ack";
    private static final String ACK_MESSAGE = "send_weather";
    private static final String ACK_PATH = "/sunshine_watch/ack";

    private static final String[] NOTIFY_WEATHER_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC
    };

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    private static final String WEAR_PATH = "/weather_info";
    private static final String LOW_TEMP = "low";
    private static final String HIGH_TEMP = "high";
    private static final String IMAGE = "image";
    private static final String TIMESTAMP = "timestamp";

    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        if(messageEvent.getPath().equals(ACK_PATH))
        {
            byte[] rawData = messageEvent.getData();

            DataMap dataMap = DataMap.fromByteArray(rawData);

            Log.d(this.getClass().getSimpleName(), "Message Received: " + dataMap);

            if (mGoogleApiClient == null) {
                mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this).addApi(Wearable.API).build();
            }
            if (!mGoogleApiClient.isConnected()) {
                ConnectionResult connectionResult =
                        mGoogleApiClient.blockingConnect(30, TimeUnit.SECONDS);

                if (!connectionResult.isSuccess()) {
                    Log.e(this.getClass().getSimpleName(), "Error connecting API Client");
                }
            }

            String message = dataMap.getString(ACK_MESSAGE_KEY);
            if(message.equals(ACK_MESSAGE))
            {
                updateWatch();
            }
        }

    }

    private void updateWatch()
    {
        List<Node> connectedNodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await().getNodes();

        Node node = null;
        for(Node n: connectedNodes)
        {
            if(n.isNearby())
            {
                node = n;
                break;
            }
        }

        if(node != null)
        {
            Context context = getApplicationContext();
            String locationQuery = Utility.getPreferredLocation(context);

            Uri weatherUri = WeatherContract.WeatherEntry
                    .buildWeatherLocationWithDate(locationQuery, System.currentTimeMillis());

            // we'll query our contentProvider, as always
            Cursor cursor = context.getContentResolver().query(weatherUri,
                    NOTIFY_WEATHER_PROJECTION, null, null, null);

            if(cursor!= null && cursor.moveToFirst())
            {
                int weatherId = cursor.getInt(INDEX_WEATHER_ID);
                double high = cursor.getDouble(INDEX_MAX_TEMP);
                double low = cursor.getDouble(INDEX_MIN_TEMP);

                cursor.close();

                int artResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
                String artUrl = Utility.getArtUrlForWeatherCondition(context, weatherId);

                int largeIconWidth = context.getResources()
                        .getDimensionPixelSize(android.R.dimen.notification_large_icon_width);

                int largeIconHeight = context.getResources()
                        .getDimensionPixelSize(android.R.dimen.notification_large_icon_height);

                Bitmap largeIcon;
                try {
                    largeIcon = Glide.with(context)
                            .load(artUrl)
                            .asBitmap()
                            .error(artResourceId)
                            .fitCenter()
                            .into(largeIconWidth, largeIconHeight).get();
                } catch (InterruptedException | ExecutionException e) {
                    Log.e(SendWeatherService.class.getSimpleName(),
                            "Error retrieving large icon from " + artUrl, e);
                    largeIcon = BitmapFactory.decodeResource(context.getResources(), artResourceId);
                }

                PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEAR_PATH);
                putDataMapRequest.getDataMap().putDouble(LOW_TEMP, low);
                putDataMapRequest.getDataMap().putDouble(HIGH_TEMP, high);
                putDataMapRequest.getDataMap().putLong(TIMESTAMP, System.currentTimeMillis());

                if(largeIcon != null)
                {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    largeIcon.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    byte[] byteArray = stream.toByteArray();
                    putDataMapRequest.getDataMap().putByteArray(IMAGE, byteArray);
                }

                PutDataRequest request = putDataMapRequest.asPutDataRequest();
                Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                        .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                            @Override
                            public void onResult(DataApi.DataItemResult dataItemResult) {
                                if(!dataItemResult.getStatus().isSuccess())
                                {
                                    Log.e(SendWeatherService.class.getSimpleName(),
                                            "Wear Data Not sent");
                                }
                                else
                                {
                                    Log.d(SendWeatherService.class.getSimpleName(),
                                            "Wear Data Sent");
                                }
                            }
                        });

            }

        }
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
