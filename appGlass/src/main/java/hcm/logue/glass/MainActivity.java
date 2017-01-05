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
import android.os.Environment;
import android.util.Log;
import android.util.Xml;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import hcm.logue.feedback.Console;
import hcm.logue.feedback.FeedbackManager;
import hcm.logue.feedback.Options;
import hcm.logue.glass.util.ConfigUtils;
import hcm.ssj.audio.AudioProvider;
import hcm.ssj.audio.Microphone;
import hcm.ssj.core.EventChannel;
import hcm.ssj.core.TheFramework;
import hcm.ssj.ioput.BluetoothConnection;
import hcm.ssj.ioput.BluetoothEventReader;
import hcm.ssj.ioput.BluetoothWriter;

public class MainActivity extends Activity {

    String name = "Logue_Glass_Activity";

    private final String PHONE_MAC = "60:8F:5C:F2:D0:9D";

    private Options conf;
    private Console console;
    private FeedbackManager man;

    private TheFramework ssj;

    @Override
    protected void onCreate(Bundle bundle) {

        //setup app
        super.onCreate(bundle);

        //Remove title bar
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        //Remove notification bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(hcm.logue.glass.R.layout.activity);

        //setup an SSJ pipeline to send sensor data to SSI
        ssj = TheFramework.getFramework();

        Microphone mic = new Microphone();
        ssj.addSensor(mic);

        AudioProvider audio = new AudioProvider();
        audio.options.sampleRate.set(16000);
        audio.options.scale.set(false);
        mic.addProvider(audio);

        EventChannel channel = null;
        BluetoothWriter socket = new BluetoothWriter();
        socket.options.connectionName.set("audio");
        socket.options.connectionType.set(BluetoothConnection.Type.CLIENT);
        socket.options.serverAddr.set(PHONE_MAC);
        ssj.addConsumer(socket, audio, 0.1, 0);

        BluetoothEventReader eventReader = new BluetoothEventReader();
        eventReader.options.connectionName.set("logue");
        eventReader.options.connectionType.set(BluetoothConnection.Type.CLIENT);
        eventReader.options.serverAddr.set(PHONE_MAC);
        channel = ssj.registerEventProvider(eventReader);
        ssj.addComponent(eventReader);

        //setup up the logic
        conf = new Options();
        console = new Console();
        man = new FeedbackManager(this);
        man.registerEventChannel(channel);

        //load config file
        load(man, conf, "config.xml", false);

        //initialize gui
        console.setup(this, R.id.layout_table);
        man.initClasses();

        //start threads
        if(man != null)
            new Thread(man).start();
        ssj.Start();
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
            ssj.Stop();
            Log.i(name, "stopping console");
            console.close();
            Log.i(name, "stopping manager");
            man.close();
            Log.i(name, "manager stopped");
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

    private void load(FeedbackManager man, Options conf, String filename, boolean lookOnDevice)
    {
        try
        {
            XmlPullParser parser;
            InputStream in;

            if(lookOnDevice)
            {
                //look for config file on sdcard first
                File sdcard = Environment.getExternalStorageDirectory();
                File folder = new File(sdcard.getPath() + "/logue");
                if (!folder.exists() && !folder.isDirectory())
                {
                    if (!folder.mkdirs())
                        Log.e("Activity", "Error creating folder");
                }
                File file = new File(folder, filename);

                if (file.exists())
                    in = new FileInputStream(file);
                else
                {
                    //if not found, copy the one from assets
                    InputStream from = getAssets().open(filename);
                    OutputStream to = new FileOutputStream(file);

                    ConfigUtils.copyFile(from, to);

                    from.close();
                    to.close();

                    //than try loading it again
                    file = new File(folder, filename);
                    in = new FileInputStream(file);
                }
            }
            else
            {
                in = getAssets().open(filename);
            }

            parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);

            parser.setInput(in, null);

            while(parser.next() != XmlPullParser.END_DOCUMENT)
            {
                switch(parser.getEventType())
                {
                    case XmlPullParser.START_TAG:
                        if(parser.getName().equalsIgnoreCase("options"))
                        {
                            conf.load(parser);
                        }
                        else if(parser.getName().equalsIgnoreCase("feedback"))
                        {
                            man.load(parser);
                        }
                }
            }
            in.close();
        }
        catch (Exception e)
        {
            Log.e(name, "exception when parsing config file", e);
        }
    }

}
