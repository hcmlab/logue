/*
 * TactileFeedback.java
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

package hcm.logue.feedback.feedback;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;

import com.thalmic.myo.Hub;
import com.thalmic.myo.Myo;

import hcm.logue.feedback.Console;
import hcm.logue.feedback.feedback.events.Event;
import hcm.logue.feedback.feedback.events.TactileEvent;
import hcm.ssj.core.Cons;
import hcm.ssj.myo.Vibrate2Command;


/**
 * Created by Johnny on 01.12.2014.
 */
public class TactileFeedback extends Feedback
{
    Activity activity;

    boolean firstCall = true;
    Myo myo = null;
    Vibrate2Command cmd = null;

    long lock = 0;
    byte intensityNew[] = null;

    public TactileFeedback(Activity activity)
    {
        this.activity = activity;
        type = Type.Tactile;
    }

    public void firstCall()
    {
        Hub hub = Hub.getInstance();

        long time = SystemClock.elapsedRealtime();
        while (hub.getConnectedDevices().isEmpty() && SystemClock.elapsedRealtime() - time < Cons.WAIT_SENSOR_CONNECT)
        {
            try {
                Thread.sleep(Cons.SLEEP_ON_IDLE);
            } catch (InterruptedException e) {}
        }

        if (hub.getConnectedDevices().isEmpty())
            throw new RuntimeException("device not found");

        Console.print("connected to Myo");

        myo = hub.getConnectedDevices().get(0);
        cmd = new Vibrate2Command(hub);

        firstCall = false;
    }

    @Override
    public boolean execute(Event event)
    {
        if(firstCall)
            firstCall();

        //update only if the global lock has passed
        if(System.currentTimeMillis() < lock)
        {
            Log.i(name, "ignoring event, lock active for another " + (lock - System.currentTimeMillis()) + "ms");
            return false;
        }

        TactileEvent ev = (TactileEvent) event;
        if(ev == lastEvent)
        {
            //check lock
            //only execute if enough time has passed since last execution of this instance
            if (ev.lockSelf == -1 || System.currentTimeMillis() - ev.lastExecutionTime < ev.lockSelf)
                return false;

            if(ev.multiplier != 1)
            {
                intensityNew = multiply(intensityNew, ev.multiplier);
            }

            Log.i(name, "vibration " +  ev.duration[0] + "/" + (int) intensityNew[0]);
            cmd.vibrate(myo, ev.duration, intensityNew);
        }
        else
        {
            Log.i(name, "vibration " +  ev.duration[0] + "/" + (int)ev.intensity[0]);
            cmd.vibrate(myo, ev.duration, ev.intensity);

            if(intensityNew == null)
                intensityNew = new byte[ev.intensity.length];
            System.arraycopy(ev.intensity, 0, intensityNew, 0, ev.intensity.length);
        }

        //set lock
        if(ev.lock > 0)
            lock = System.currentTimeMillis() + (long) ev.lock;
        else
            lock = 0;

        return true;
    }

    public byte[] multiply(byte[] src, float mult)
    {
        byte dst[] = new byte[src.length];

        int val_int;
        for(int i = 0; i < src.length; ++i)
        {
            val_int = (int)((int)src[i] * mult);
            if(val_int > 255)
                val_int = 255;

            dst[i] = (byte)val_int;
        }

        return dst;
    }
}
