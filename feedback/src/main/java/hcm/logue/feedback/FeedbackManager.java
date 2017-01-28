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

import hcm.logue.feedback.classes.Feedback;
import hcm.logue.feedback.classes.FeedbackListener;
import hcm.logue.feedback.events.Event;

/**
 * Created by Johnny on 02.12.2014.
 */
public class FeedbackManager implements Runnable {

    private static int MANAGER_TICK_LENGTH = 100; //ms

    protected String tag = "Logue_FeedbackManager";

    protected Activity activity;
    protected ArrayList<hcm.ssj.core.EventChannel> comm;
    protected Options options;

    protected boolean terminate = false;
    protected boolean safeToKill = false;

    protected ArrayList<Feedback> classes = new ArrayList<Feedback>();

    protected int lastBehavEventID = -1;

    protected int level = 0;
    private long lastDesireableState;
    private long lastUndesireableState;
    private long progressionTimeout = 120000; //2 minutes
    private long regressionTimeout = 600000; //10 minutes
    private int maxLevel = 3;

    public FeedbackManager(Activity act)
    {
        activity = act;
        options = new Options();
        comm = new ArrayList<>();
    }

    public void registerEventChannel(hcm.ssj.core.EventChannel ch)
    {
        comm.add(ch);
    }

    public void close()
    {
        terminate = true;
        while(!safeToKill)
            try { Thread.sleep(200); }
            catch (Exception e) {}
    }

    public void initClasses()
    {
        Event ev = classes.get(0).getEvents().get(0);
        if(ev != null)
            classes.get(0).execute(ev);
    }

    public ArrayList<Feedback> getClasses()
    {
        return classes;
    }

    @Override
    public void run()
    {
        terminate = false;
        lastDesireableState = lastUndesireableState = System.currentTimeMillis();

        while(!terminate)
        {
            try {
                update();
                Thread.sleep(MANAGER_TICK_LENGTH);
            }
            catch (Exception e)
            {
                Log.e(tag, "exception in update loop", e);
            }
        }

        //shut down
        for(Feedback f : classes)
        {
            f.release();
        }

        safeToKill = true;
    }

    public void update() {

        if(comm == null)
            return;

        hcm.ssj.core.event.Event behavEvent = null;

        for (hcm.ssj.core.EventChannel ch : comm)
        {
            do
            {
                behavEvent = ch.getEvent(lastBehavEventID + 1, false);
                if (behavEvent != null)
                {
                    lastBehavEventID = behavEvent.id;
                    process(behavEvent);
                }
            } while (behavEvent != null && !terminate);
        }

        for(Feedback i : classes)
        {
            i.update();
        }
    }

    public void process(hcm.ssj.core.event.Event behavEvent) {

        if(classes.size() == 0)
            return;

        //validate feedback
        for(Feedback i : classes)
        {
            if(i.getLevel() == level)
            {
                if(i.getValence() == Feedback.Valence.Desirable)
                {
                    lastDesireableState = System.currentTimeMillis();
                }
                else if(i.getValence() == Feedback.Valence.Undesirable)
                {
                    lastUndesireableState = System.currentTimeMillis();
                }
            }
        }

        //if all current feedback classes are in a non desirable state, check if we should progress to next level
        if (System.currentTimeMillis() - progressionTimeout > lastDesireableState && level < maxLevel) {
            level++;
            lastDesireableState = System.currentTimeMillis();
        }
        //if all current feedback classes are in a desirable state, check if we can go back to the previous level
        else if (System.currentTimeMillis() - regressionTimeout > lastUndesireableState && level > 0) {
            level--;
            lastUndesireableState = System.currentTimeMillis();
        }

        //execute feedback
        for(Feedback i : classes)
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
                Feedback c = Feedback.create(parser, activity);
                classes.add(c);
            }
            else if(parser.getEventType() == XmlPullParser.END_TAG && parser.getName().equalsIgnoreCase("feedback"))
                break; //jump out once we reach end tag for classes
        }

        parser.require(XmlPullParser.END_TAG, null, "feedback");

        Console.print("loaded " + classes.size() + " classes");
    }

    public void load(String filename) throws IOException, XmlPullParserException
    {
        InputStream in = activity.getAssets().open(filename);

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);

        parser.setInput(in, null);

        while(parser.next() != XmlPullParser.END_DOCUMENT)
        {
            switch(parser.getEventType())
            {
                case XmlPullParser.START_TAG:
                    if(parser.getName().equalsIgnoreCase("options"))
                    {
                        options.load(parser);
                    }
                    if(parser.getName().equalsIgnoreCase("feedback"))
                    {
                        load(parser);
                    }
                    break;
            }
        }

        if(options.getOption("progressionTimeout") != null)
            progressionTimeout = (int)(options.getOptionF("progressionTimeout") * 1000);
        if(options.getOption("regressionTimeout") != null)
            regressionTimeout = (int)(options.getOptionF("regressionTimeout") * 1000);

        //find max progression level
        maxLevel = 0;
        for(Feedback i : classes) {
            if(i.getLevel() > maxLevel)
                maxLevel = i.getLevel();
        }

        in.close();
    }

    public void addFeedbackListener(FeedbackListener listener)
    {
        for(Feedback i : classes)
        {
            i.addFeedbackListener(listener);
        }
    }
}
