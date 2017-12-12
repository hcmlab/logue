/*
 * MainActivity.java
 * Copyright (c) 2015
 * Author: Ionut Damian
 * *****************************************************
 * This file is part of the Logue project developed at the Lab for Human Centered Multimedia
 * of the University of Augsburg.
 *
 * The applications and libraries are free software; you can redistribute them and/or modify them
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or any later version.
 *
 * The software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this library; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package hcm.logue.glass;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TableLayout;

import hcm.ssj.androidSensor.AndroidSensor;
import hcm.ssj.androidSensor.AndroidSensorChannel;
import hcm.ssj.androidSensor.SensorType;
import hcm.ssj.audio.AudioChannel;
import hcm.ssj.audio.Microphone;
import hcm.ssj.body.OverallActivation;
import hcm.ssj.core.EventChannel;
import hcm.ssj.core.Pipeline;
import hcm.ssj.core.SSJException;
import hcm.ssj.event.FloatsEventSender;
import hcm.ssj.feedback.FeedbackManager;
import hcm.ssj.ioput.BluetoothConnection;
import hcm.ssj.ioput.BluetoothEventReader;
import hcm.ssj.ioput.BluetoothWriter;
import hcm.ssj.signal.MvgAvgVar;

public class MainActivity extends Activity {

    String name = "Logue_Glass_Activity";

    private final String PHONE_NAME = "Nexus 6P";
	private boolean receiveDataFromPhone = true;

    private Pipeline ssj;

	@Override
    protected void onCreate(Bundle bundle) {

        //setup app
        super.onCreate(bundle);

        //Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //Remove notification bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(hcm.logue.glass.R.layout.activity);

        try
        {
            //setup an SSJ pipeline to send sensor data to SSI
            ssj = Pipeline.getInstance();
            ssj.options.bufferSize.set(10f);

            Microphone mic = new Microphone();
            AudioChannel audio = new AudioChannel();
            audio.options.sampleRate.set(8000);
            audio.options.scale.set(false);
            ssj.addSensor(mic, audio);

			EventChannel activity_channel;
			if(receiveDataFromPhone)
			{
				BluetoothWriter socket = new BluetoothWriter();
				socket.options.connectionName.set("audio");
				socket.options.connectionType.set(BluetoothConnection.Type.CLIENT);
				socket.options.serverName.set(PHONE_NAME);
				ssj.addConsumer(socket, audio, 0.2, 0);

				BluetoothEventReader eventReader = new BluetoothEventReader();
				eventReader.options.connectionName.set("logue");
				eventReader.options.connectionType.set(BluetoothConnection.Type.CLIENT);
				eventReader.options.serverName.set(PHONE_NAME);
				activity_channel = ssj.registerEventProvider(eventReader);
			}
			else
			{
				AndroidSensor androidSensor = new AndroidSensor();
				AndroidSensorChannel acc = new AndroidSensorChannel();
				acc.options.sensorType.set(SensorType.LINEAR_ACCELERATION);
				ssj.addSensor(androidSensor, acc);

				OverallActivation activity = new OverallActivation();
				ssj.addTransformer(activity, acc, 0.1, 5.0);

				MvgAvgVar _activityf = new MvgAvgVar();
				_activityf.options.window.set(10.);
				ssj.addTransformer(_activityf, activity, 0.1, 0);

				FloatsEventSender evactivity = new FloatsEventSender();
				evactivity.options.sender.set("SSJ");
				evactivity.options.event.set("OverallActivation");
				ssj.addConsumer(evactivity, _activityf, 0.1 * 5, 0);
				activity_channel = ssj.registerEventProvider(evactivity);
			}

			FeedbackManager feedback = new FeedbackManager();
			feedback.options.strategy.set("publicSpeaking.xml");
			feedback.options.fromAssets.set(true);
            feedback.options.progression.set(10f);
            feedback.options.regression.set(10f);
            int layout_id = this.getResources().getIdentifier("layout_table", "id", this.getPackageName());
            feedback.options.layout.set((TableLayout) this.findViewById(layout_id));
			ssj.registerEventListener(feedback, activity_channel);
		}
		catch (SSJException e)
		{
			Log.e("Logue", e.getMessage(), e);
		}

        //start threads
        ssj.start();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                finish();
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override
    protected void onDestroy() {

        try {
            Log.i(name, "stopping SSJ");
            ssj.stop();
			ssj.clear();
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        super.onDestroy();
        Log.i(name, "shut down completed");
    }

    public void onBackPressed()
    {
        finish();
    }

    public void onPause()
    {
        super.onPause();
        finish();
    }
}
