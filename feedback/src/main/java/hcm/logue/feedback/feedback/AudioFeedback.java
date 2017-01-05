/*
 * VisualFeedback.java
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
import android.content.Context;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;

import hcm.logue.feedback.feedback.events.AudioEvent;
import hcm.logue.feedback.feedback.events.Event;


/**
 * Created by Johnny on 01.12.2014.
 */
public class AudioFeedback extends Feedback
{
    Activity _activity;
    AudioEvent _lastEvent = null;
    long _lastExecutionTime = 0;
    float _intensityNew = 0;

    long _lock = 0;

    SoundPool _player;

    public AudioFeedback(Activity activity)
    {
        _activity = activity;
        _type = Type.Audio;
    }

    public void release()
    {
        _player.release();
    }

    @Override
    public void execute(Event event)
    {
        //update only if the global lock has passed
        if(System.currentTimeMillis() < _lock)
        {
            Log.i(_name, "ignoring event, lock active for another " + (_lock - System.currentTimeMillis()) + "ms");
            return;
        }

        AudioEvent ev = (AudioEvent) event;
        if(ev == null)
        {
            _lastEvent = null;
            return;
        }

        if(ev == _lastEvent)
        {
            //check lock
            //only execute if enough time has passed since last execution of this instance
            if (ev.lockSelf == -1 || System.currentTimeMillis() - _lastExecutionTime < ev.lockSelf)
                return;

            if(ev.multiplier != 1)
            {
                _intensityNew *= ev.multiplier;
                if(_intensityNew > 1)
                    _intensityNew = 1;
            }

            _player.play(ev.soundId, _intensityNew, 1, 1, 0, 1);
            _lastExecutionTime = System.currentTimeMillis();
        }
        else
        {
            _player.play(ev.soundId, ev.intensity, ev.intensity, 1, 0, 1);
            _lastExecutionTime = System.currentTimeMillis();
            _lastEvent = ev;
            _intensityNew = ev.intensity;
        }

        //set lock
        if(ev.lock > 0)
            _lock = System.currentTimeMillis() + (long) ev.lock;
        else
            _lock = 0;
    }

    protected void load(XmlPullParser xml, final Context context)
    {
        super.load(xml, context);

        _player = new SoundPool(4, AudioManager.STREAM_NOTIFICATION, 0);
        for(Event ev : _events)
            ((AudioEvent) ev).registerWithPlayer(_player);
    }
}
