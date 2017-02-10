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

package hcm.logue.phone;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.jjoe64.graphview.GraphView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import hcm.ssj.core.TheFramework;


public class MainActivity extends AppCompatActivity
{
    String _name = "Logue_Worker_Activity";

    private Pipeline _pipe = null;
    private String _header;

    GraphView _graph[];

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String version = null;
        try
        {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        }
        catch (PackageManager.NameNotFoundException e)
        {
            Log.e(_name, "cannot retrieve version number");
        }

        _header = "LogueWorker v" + version + " (SSJ v" + TheFramework.getFramework().getVersion() + ")";

        TextView text = (TextView) findViewById(R.id.txt_header);
        text.setText(_header);

        _graph = new GraphView[2];
        _graph[0] = (GraphView) findViewById(R.id.graph);
        _graph[1] = (GraphView) findViewById(R.id.graph2);

        _graph[0].removeAllSeries();
        _graph[1].removeAllSeries();

        checkStrategyFile("default.xml");

        _pipe = new Pipeline(this, _graph);
    }

    @Override
    protected void onDestroy()
    {
        if (_pipe != null)
        {
            _pipe.terminate();

            while(!_pipe.isRunning())
                try { Thread.sleep(200); }
                catch (Exception e) {}

            _pipe.release();
        }

        super.onDestroy();
        Log.i(_name, "destroyed");
    }

    /**
     * Prevent activity from being destroyed once back button is pressed
     */
    public void onBackPressed()
    {
        moveTaskToBack(true);
    }

    public void onStartPressed(View v)
    {
        Button btn = (Button) findViewById(R.id.btn_start);
        TextView text = (TextView) findViewById(R.id.txt_status);

        if(_pipe.getStatus() == Pipeline.Status.Idle)
        {
            text.setText("> pipeline ready");
            btn.setText(R.string.start);

            _pipe.create();
        }
        else if(!_pipe.isRunning())
        {
            btn.setAlpha(0.5f);
            btn.setEnabled(false);

            text.setText("> starting");
            Thread t = new Thread(_pipe);
            t.start();
        }
        else
        {
            btn.setAlpha(0.5f);
            btn.setEnabled(false);

            text.setText("> stopping");
            _pipe.terminate();
        }
    }

    public void notifyPipeState(final boolean running)
    {
        this.runOnUiThread(new Runnable()
        {
            public void run()
            {
                Button btn = (Button) findViewById(R.id.btn_start);
                TextView text = (TextView) findViewById(R.id.txt_status);

                if (running)
                {
                    text.setText("> running");
                    btn.setText(R.string.stop);
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f);
                } else
                {
                    text.setText("> not running");
                    btn.setText(R.string.start);
                    btn.setEnabled(true);
                    btn.setAlpha(1.0f);
                }
            }
        });
    }

    public void onClosePressed(View v)
    {
        if(_pipe != null && _pipe.getStatus() == Pipeline.Status.Running)
        {
            _pipe.terminate();
        }

        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu items for use in the action bar
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle presses on the action bar items
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent i = new Intent(this, SettingsActivity.class);
                i.putExtra("status", _pipe.getStatus().toString());
                startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    private void checkStrategyFile(String filename)
    {
        try
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

            if (!file.exists())
            {
                //if not found, copy the one from assets
                InputStream from = getAssets().open(filename);
                OutputStream to = new FileOutputStream(file);

                copyFile(from, to);

                from.close();
                to.close();
            }
        }
        catch (Exception e)
        {
            Log.e(_name, "exception when parsing config file", e);
        }
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }
}
