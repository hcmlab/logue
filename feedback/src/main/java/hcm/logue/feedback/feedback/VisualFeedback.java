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
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.ViewSwitcher;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.security.InvalidParameterException;

import hcm.logue.feedback.feedback.events.Event;
import hcm.logue.feedback.feedback.events.VisualEvent;


/**
 * Created by Johnny on 01.12.2014.
 */
public class VisualFeedback extends Feedback
{
    Activity _activity;

    protected static int s_id = 0;
    protected ImageSwitcher _img[];

    protected Drawable _defIcon[] = null;
    protected float _defBrightness = 0;

    protected long _timeout = 0;
    protected long _lock = 0;

    public VisualFeedback(Activity activity)
    {
        _activity = activity;
        _id = s_id++;

        _type = Type.Visual;
    }

    @Override
    public void execute(Event event)
    {
        VisualEvent ve = (VisualEvent) event;
        setIcon(ve.icons, ve.dur, ve.lock, ve.brightness);
    }

    protected void setIcon(Drawable icons[], int dur, int lock, float brightness)
    {
        //update only if the lock has passed
        if(System.currentTimeMillis() < _lock)
        {
            Log.i(_name, "ignoring event, lock active for another " + (_lock - System.currentTimeMillis()) + "ms");
            return;
        }

        updateIcons(icons, brightness);

        //set lock
        if(dur > 0 && icons != _defIcon)
            //return to default (first) event after dur milliseconds has passed
            _timeout = System.currentTimeMillis() + (long) dur;
        else
            _timeout = 0;

        //set lock
        if(lock > 0)
            _lock = System.currentTimeMillis() + (long) dur + (long) lock;
        else
            _lock = 0;
    }

    public void update()
    {
        if(_timeout == 0 || System.currentTimeMillis() < _timeout)
            return;

        //if a lock is set, return icons to default configuration
        Log.i(_name, "restoring default icons");
        updateIcons(_defIcon, _defBrightness);
        _timeout = 0;
    }

    protected void updateIcons(Drawable icons[], float brightness)
    {
        //set feedback icon
        updateImageSwitcher(_activity, _img[0], icons[0]);

        //set quality icon
        if(icons.length == 2 && _img[1] != null)
        {
            updateImageSwitcher(_activity, _img[1], icons[1]);
        }

        //set brightness
        WindowManager.LayoutParams lp = _activity.getWindow().getAttributes();
        lp.screenBrightness = brightness;
        _activity.getWindow().setAttributes(lp);
    }

    protected void updateImageSwitcher(final Activity act, final ImageSwitcher view, final Drawable img)
    {
        act.runOnUiThread(new Runnable()
        {
            public void run()
            {
                view.setImageDrawable(img);
            }
        });
    }

    protected void load(XmlPullParser xml, final Context context)
    {
        String layout_name = null;
        int fade = 0;
        try
        {
            xml.require(XmlPullParser.START_TAG, null, "class");

            layout_name = xml.getAttributeValue(null, "layout_id");
            if(layout_name == null)
                throw new InvalidParameterException("layout not set");

            String fade_str = xml.getAttributeValue(null, "fade");
            if (fade_str != null)
                fade = Integer.valueOf(fade_str);
        }
        catch(IOException | XmlPullParserException | InvalidParameterException e)
        {
            Log.e(_tag, "error parsing config file", e);
        }

        super.load(xml, context);

        //default icons
        _defIcon = ((VisualEvent) _events.get(0)).icons;
        _defBrightness = ((VisualEvent) _events.get(0)).brightness;

        //build layout
        int layout_id = _activity.getResources().getIdentifier(layout_name, "id", context.getPackageName());
        TableLayout table = (TableLayout) _activity.findViewById(layout_id);
        table.setStretchAllColumns(true);

        int rows = ((VisualEvent)_events.get(0)).icons.length;
        for(Event e : _events)
            if(((VisualEvent)e).icons.length > rows)
                rows = ((VisualEvent)e).icons.length;

        _img = new ImageSwitcher[rows];

        for(int i = 0; i < rows; ++i)
        {
            if (table.getChildCount() == 0) //if this is the first visual class, init rows
                table.addView(new TableRow(context), i);

            TableRow tr = (TableRow) table.getChildAt(i);
            tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT, 1f));

            _img[i] = new ImageSwitcher(context);
            _img[i].setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.MATCH_PARENT, 1f));

            Animation in = AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
            in.setDuration(fade);
            Animation out = AnimationUtils.loadAnimation(context, android.R.anim.fade_out);
            out.setDuration(fade);

            _img[i].setInAnimation(in);
            _img[i].setOutAnimation(out);

            _img[i].setFactory(new ViewSwitcher.ViewFactory() {
                @Override
                public View makeView() {
                    ImageView imageView = new ImageView(context);
//                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageView.setLayoutParams(new ImageSwitcher.LayoutParams(ImageSwitcher.LayoutParams.MATCH_PARENT, ImageSwitcher.LayoutParams.MATCH_PARENT));
                    return imageView;
                }
            });

            tr.addView(_img[i]);
        }
    }
}
