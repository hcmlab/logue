/*
 * Console.java
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

package hcm.logue.feedback;

import android.app.Activity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Johnny on 29.01.2015.
 */
public class Console extends Thread {

    final static int NUM_LINES = 2;
    final static int TIMEOUT = 100; //in ms

    protected Activity _activity;
    protected static ArrayList<String> _buffer = new ArrayList<String>();
    String _text;

    protected static ReentrantLock _lock = new ReentrantLock();

    protected boolean _terminate = false;
    protected boolean _safeToKill = false;

    protected TextView _textView;

    public Console()
    {}

    public static void print(String text)
    {
        _lock.lock();

        _buffer.add(text); //push

        if(_buffer.size() > NUM_LINES)
            _buffer.remove(0); //pop front

        _lock.unlock();
    }

    public void setup(Activity act, int layout_id)
    {
        _activity = act;
        TableLayout table = (TableLayout) _activity.findViewById(layout_id);

        TableRow tr = new TableRow(act.getApplicationContext());
        tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 0.1f));

        _textView = new TextView(act.getApplicationContext());
        TableRow.LayoutParams params = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT, 1f);

        if(table.getChildCount() > 0)
            params.span = ((TableRow)table.getChildAt(0)).getChildCount();

        _textView.setLayoutParams(params);
        _textView.setTextSize(14);

        tr.addView(_textView);
        table.addView(tr);

        start();
    }

    public void close() throws InterruptedException
    {
        _terminate = true;
        while(!_safeToKill)
            sleep(200);
    }

    public void terminate()
    {

    }

    @Override
    public void run()
    {
        while(!_terminate)
        {
            try {
                _lock.lock();

                _text = "";
                for(String s : _buffer)
                {
                    _text += "> " + s + '\n';
                }

                _lock.unlock();

                _activity.runOnUiThread(new Runnable() {
                    public void run() {
                    _textView.setText(_text);
                    }
                });

                sleep(TIMEOUT);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        _safeToKill = true;
    }
}
