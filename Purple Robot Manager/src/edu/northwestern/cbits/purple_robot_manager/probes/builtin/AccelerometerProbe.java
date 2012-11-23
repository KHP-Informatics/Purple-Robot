package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import edu.northwestern.cbits.purple_robot_manager.R;

public class AccelerometerProbe extends ContinuousProbe implements SensorEventListener
{
	private static int BUFFER_SIZE = 40;

	private static String[] fieldNames = { "X", "Y", "Z" };

	private double lastSeen = 0;
	private long lastFrequencyLookup = 0;
	private long frequency = 1000;

	private float valueBuffer[][] = new float[3][BUFFER_SIZE];
	private int accuracyBuffer[] = new int[BUFFER_SIZE];
	private double timeBuffer[] = new double[BUFFER_SIZE];

	private int bufferIndex  = 0;

	public Bundle formattedBundle(Context context, Bundle bundle)
	{
		Bundle formatted = super.formattedBundle(context, bundle);

		double[] eventTimes = bundle.getDoubleArray("EVENT_TIMESTAMP");
		float[] x = bundle.getFloatArray("X");
		float[] y = bundle.getFloatArray("Y");
		float[] z = bundle.getFloatArray("Z");

		ArrayList<String> keys = new ArrayList<String>();

		SimpleDateFormat sdf = new SimpleDateFormat(context.getString(R.string.display_date_format));

		if (x == null || y == null || z == null)
		{

		}
		else if (eventTimes.length > 1)
		{
			Bundle readings = new Bundle();

			for (int i = 0; i < eventTimes.length; i++)
			{
				String formatString = String.format(context.getString(R.string.display_gyroscope_reading), x[i], y[i], z[i]);

				double time = eventTimes[i];

				Date d = new Date((long) time);

				String key = sdf.format(d);

				readings.putString(key, formatString);

				keys.add(key);
			}

			if (keys.size() > 0)
				readings.putStringArrayList("KEY_ORDER", keys);


			formatted.putBundle(context.getString(R.string.display_accelerometer_readings), readings);
		}
		else if (eventTimes.length > 0)
		{
			String formatString = String.format(context.getString(R.string.display_gyroscope_reading), x[0], y[0], z[0]);

			double time = eventTimes[0];

			Date d = new Date((long) time);

			formatted.putString(sdf.format(d), formatString);
		}

		return formatted;
	};

	public long getFrequency()
	{
		long now = System.currentTimeMillis();

		if (now - this.lastFrequencyLookup > 5000 && this._context != null)
		{
			SharedPreferences prefs = ContinuousProbe.getPreferences(this._context);

			frequency = Long.parseLong(prefs.getString("config_probe_accelerometer_built_in_frequency", "1000"));

			int bufferSize = 1000 / (int) frequency;

			if (timeBuffer.length != bufferSize)
			{
				if (bufferSize < 1)
					bufferSize = 1;

				synchronized(this)
				{
					bufferIndex = 0;

					valueBuffer = new float[3][bufferSize];
					accuracyBuffer = new int[bufferSize];
					timeBuffer = new double[bufferSize];
				}
			}

			this.lastFrequencyLookup = now;
		}

		return frequency;
	}

	public String name(Context context)
	{
		return "edu.northwestern.cbits.purple_robot_manager.probes.builtin.AccelerometerProbe";
	}

	public int getTitleResource()
	{
		return R.string.title_builtin_accelerometer_probe;
	}

	public int getCategoryResource()
	{
		return R.string.probe_environment_category;
	}

	public boolean isEnabled(Context context)
	{
        SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        sensors.unregisterListener(this);

    	Log.e("PRM", "CHECK ENABLE");

        if (super.isEnabled(context))
        {
        	Log.e("PRM", "ENBLING SUPER");

        	this._context = context.getApplicationContext();

        	SharedPreferences prefs = ContinuousProbe.getPreferences(context);

        	if (prefs.getBoolean("config_probe_accelerometer_built_in_enabled", true))
        	{
            	Log.e("PRM", "ENBLING ACCELEROMETER");

            	sensors.registerListener(this, sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST, null);

        		return true;
        	}
        }

        Log.e("PRM", "ENABLE OFF");

        return false;
	}

	public void onSensorChanged(SensorEvent event)
	{
		final double now = (double) System.currentTimeMillis();

		if (now - this.lastSeen > this.getFrequency() && bufferIndex <= timeBuffer.length)
		{
			synchronized(this)
			{
				double elapsed = (double) SystemClock.elapsedRealtime();
				double boot = (now - elapsed) * 1000 * 1000;

				double timestamp = event.timestamp + boot;

				timeBuffer[bufferIndex] = timestamp / 1000000;

				accuracyBuffer[bufferIndex] = event.accuracy;

				for (int i = 0; i < event.values.length; i++)
				{
					valueBuffer[i][bufferIndex] = event.values[i];
				}

				bufferIndex += 1;

				if (bufferIndex >= timeBuffer.length)
				{
					Sensor sensor = event.sensor;

					Bundle data = new Bundle();

					Bundle sensorBundle = new Bundle();
					sensorBundle.putFloat("MAXIMUM_RANGE", sensor.getMaximumRange());
					sensorBundle.putString("NAME", sensor.getName());
					sensorBundle.putFloat("POWER", sensor.getPower());
					sensorBundle.putFloat("RESOLUTION", sensor.getResolution());
					sensorBundle.putInt("TYPE", sensor.getType());
					sensorBundle.putString("VENDOR", sensor.getVendor());
					sensorBundle.putInt("VERSION", sensor.getVersion());

					data.putDouble("TIMESTAMP", now / 1000);

					data.putString("PROBE", this.name(this._context));

					data.putBundle("SENSOR", sensorBundle);

					data.putDoubleArray("EVENT_TIMESTAMP", timeBuffer);
					data.putIntArray("ACCURACY", accuracyBuffer);

					for (int i = 0; i < fieldNames.length; i++)
					{
						data.putFloatArray(fieldNames[i], valueBuffer[i]);
					}

					this.transmitData(data);

					bufferIndex = 0;
				}

				this.lastSeen = now;
			}
		}
	}

	public String getPreferenceKey()
	{
		return "accelerometer_built_in";
	}

	public int getResourceFrequencyLabels()
	{
		return R.array.probe_acceleration_frequency_labels;
	}

	public int getResourceFrequencyValues()
	{
		return R.array.probe_acceleration_frequency_values;
	}

	public String summarizeValue(Context context, Bundle bundle)
	{
		float xReading = bundle.getFloatArray("X")[0];
		float yReading = bundle.getFloatArray("Y")[0];
		float zReading = bundle.getFloatArray("Z")[0];

		return String.format(context.getResources().getString(R.string.summary_accelerator_probe), xReading, yReading, zReading);
	}
}