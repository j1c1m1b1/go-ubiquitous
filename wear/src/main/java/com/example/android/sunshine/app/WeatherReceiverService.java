package com.example.android.sunshine.app;

import android.util.Log;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Arrays;

public class WeatherReceiverService extends WearableListenerService
{

    private static final String WEAR_PATH = "/weather_info";

    private static final String LOW_TEMP = "low";
    private static final String HIGH_TEMP = "high";
    private static final String IMAGE = "image";

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.i(this.getClass().getSimpleName(), "Data changed");

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

                    Log.i(this.getClass().getSimpleName(), "Info - High: " + high +
                            ", Low: " + low + ", Image: " + Arrays.toString(bitmapBytes));
                }
            }
        }

    }
}
