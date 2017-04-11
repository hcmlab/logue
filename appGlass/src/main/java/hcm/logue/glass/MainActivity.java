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

import hcm.ssj.audio.AudioChannel;
import hcm.ssj.audio.Microphone;
import hcm.ssj.core.EventChannel;
import hcm.ssj.core.Pipeline;
import hcm.ssj.core.SSJException;
import hcm.ssj.feedback.FeedbackManager;
import hcm.ssj.ioput.BluetoothConnection;
import hcm.ssj.ioput.BluetoothEventReader;
import hcm.ssj.ioput.BluetoothWriter;

public class MainActivity extends Activity {

    String name = "Logue_Glass_Activity";

    private final String PHONE_MAC = "60:8F:5C:F2:D0:9D";

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

            Microphone mic = new Microphone();
            AudioChannel audio = new AudioChannel();
            audio.options.sampleRate.set(8000);
            audio.options.scale.set(false);
            ssj.addSensor(mic, audio);

			BluetoothWriter socket = new BluetoothWriter();
			socket.options.connectionName.set("audio");
			socket.options.connectionType.set(BluetoothConnection.Type.CLIENT);
			socket.options.serverName.set("Nexus 6P");
			ssj.addConsumer(socket, audio, 0.2, 0);

			BluetoothEventReader eventReader = new BluetoothEventReader();
			eventReader.options.connectionName.set("logue");
			eventReader.options.connectionType.set(BluetoothConnection.Type.CLIENT);
			eventReader.options.serverName.set("Nexus 6P");
			EventChannel channel = ssj.registerEventProvider(eventReader);

			FeedbackManager feedback = new FeedbackManager(this);
			feedback.options.strategy.set("config.xml");
			feedback.options.fromAsset.set(true);
			ssj.registerEventListener(feedback, channel);
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
