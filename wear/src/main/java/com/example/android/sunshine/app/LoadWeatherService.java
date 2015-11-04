package com.example.android.sunshine.app;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.util.List;

/**
 * @author Julio Mendoza on 11/3/15.
 */
public class LoadWeatherService extends Service implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String ACK_MESSAGE_KEY = "ack";
    private static final String ACK_MESSAGE = "send_weather";
    private static final String ACK_PATH = "/sunshine_watch/ack";

    private GoogleApiClient apiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(this.getClass().getSimpleName(), "Handle Load Weather");
        apiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        apiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(this.getClass().getSimpleName(), "Connected");
        sendRequestWeatherMessage();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(this.getClass().getSimpleName(), "Connection suspended: " + i);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(this.getClass().getSimpleName(), "" + connectionResult.getErrorMessage());
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendRequestWeatherMessage()
    {
        SendWeatherRequestTask task = new SendWeatherRequestTask();
        task.execute(null, null);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(apiClient!= null && apiClient.isConnected())
        {
            apiClient.disconnect();
        }
    }

    private class SendWeatherRequestTask extends AsyncTask<Void, Void, Void>
    {

        @Override
        protected Void doInBackground(Void... voids) {
            if(apiClient != null && apiClient.isConnected())
            {
                Log.d(this.getClass().getSimpleName(), "Handle Load Weather");
                List<Node> connectedNodes =
                        Wearable.NodeApi.getConnectedNodes(apiClient).await().getNodes();
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
                    DataMap config = new DataMap();
                    config.putString(ACK_MESSAGE_KEY, ACK_MESSAGE);
                    byte[] rawData = config.toByteArray();
                    Wearable.MessageApi.sendMessage(apiClient, node.getId(), ACK_PATH, rawData);
                    Log.d(this.getClass().getSimpleName(), "Message sent");
                }
            }
            return null;
        }
    }
}
