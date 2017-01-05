/*
 * FeedbackManager.java
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
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import hcm.logue.feedback.feedback.Feedback;
import hcm.logue.feedback.feedback.FeedbackListener;
import hcm.logue.feedback.feedback.events.Event;

/**
 * Created by Johnny on 02.12.2014.
 */
public class FeedbackManager implements Runnable {

    private static int MANAGER_TICK_LENGTH = 100; //ms

    protected String _tag = "Logue_FeedbackManager";

    protected Activity _activity;
    protected ArrayList<hcm.ssj.core.EventChannel> _comm;

    protected boolean _terminate = false;
    protected boolean _safeToKill = false;

    protected ArrayList<Feedback> _classes = new ArrayList<Feedback>();

    protected int _lastBehavEventID = -1;

    protected int level = 0;
    private long lastDesireableState = 0;
    private long progresisonTimeout = 0;
    private int maxLevel = 3;

    public FeedbackManager(Activity act)
    {
        _activity = act;
        _comm = new ArrayList<>();
    }

    public void registerEventChannel(hcm.ssj.core.EventChannel ch)
    {
        _comm.add(ch);
    }

    public void close()
    {
        _terminate = true;
        while(!_safeToKill)
            try { Thread.sleep(200); }
            catch (Exception e) {}
    }

    public void initClasses()
    {
        Event ev = _classes.get(0).getEvents().get(0);
        if(ev != null)
            _classes.get(0).execute(ev);
    }

    public ArrayList<Feedback> getClasses()
    {
        return _classes;
    }

    @Override
    public void run()
    {
        _terminate = false;

        while(!_terminate)
        {
            try {
                update();
                Thread.sleep(MANAGER_TICK_LENGTH);
            }
            catch (Exception e)
            {
                Log.e(_tag, "exception in update loop", e);
            }
        }

        //shut down
        for(Feedback f : _classes)
        {
            f.release();
        }

        _safeToKill = true;
    }

    public void update() {

        if(_comm == null)
            return;

        hcm.ssj.core.event.Event behavEvent = null;

        for (hcm.ssj.core.EventChannel ch : _comm)
        {
            do
            {
                behavEvent = ch.getEvent(_lastBehavEventID + 1, false);
                if (behavEvent != null)
                {
                    _lastBehavEventID = behavEvent.id;
                    process(behavEvent);
                }
            } while (behavEvent != null && !_terminate);
        }

        for(Feedback i : _classes)
        {
            i.update();
        }
    }

    public void process(hcm.ssj.core.event.Event behavEvent) {

        if(_classes.size() == 0)
            return;

        //validate feedback
        for(Feedback i : _classes)
        {
            if(i.getLevel() == level && i.getState() == Feedback.State.Desirable)
            {
                lastDesireableState = System.currentTimeMillis();
            }
        }

        //if all current feedback classes are in a non desirable state, check if we should progress to next level
        if(System.currentTimeMillis() > lastDesireableState + progresisonTimeout)
        {
            //TODO: also go back if everything is peachy
            level++;
            if(level > maxLevel) level = maxLevel;

            lastDesireableState = System.currentTimeMillis();
        }

        //execute feedback
        for(Feedback i : _classes)
        {
            if(i.getLevel() == level)
                i.process(behavEvent);
        }
    }

    public void load(XmlPullParser parser) throws IOException, XmlPullParserException
    {
        parser.require(XmlPullParser.START_TAG, null, "feedback");

        //iterate through classes
        while (parser.next() != XmlPullParser.END_DOCUMENT)
        {
            if(parser.getEventType() == XmlPullParser.START_TAG && parser.getName().equalsIgnoreCase("class"))
            {
                //parse feedback classes
                Feedback c = Feedback.create(parser, _activity);
                _classes.add(c);
            }
            else if(parser.getEventType() == XmlPullParser.END_TAG && parser.getName().equalsIgnoreCase("feedback"))
                break; //jump out once we reach end tag for classes
        }

        parser.require(XmlPullParser.END_TAG, null, "feedback");

        Console.print("loaded " + _classes.size() + " classes");
    }

    public void load(String filename) throws IOException, XmlPullParserException
    {
        InputStream in = _activity.getAssets().open(filename);

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);

        parser.setInput(in, null);

        while(parser.next() != XmlPullParser.END_DOCUMENT)
        {
            switch(parser.getEventType())
            {
                case XmlPullParser.START_TAG:
                    if(parser.getName().equalsIgnoreCase("feedback"))
                    {
                        load(parser);
                    }
            }
        }

        in.close();
    }

    public void addFeedbackListener(FeedbackListener listener)
    {
        for(Feedback i : _classes)
        {
            i.addFeedbackListener(listener);
        }
    }
}
