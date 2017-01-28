/*
 * VisualInstance.java
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

package hcm.logue.feedback.events;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

import hcm.logue.feedback.classes.Feedback;

/**
 * Created by Johnny on 01.12.2014.
 */
public class VisualEvent extends Event
{
    public Drawable icons[];

    public int dur = 0;
    public int lock = 0;
    public float brightness = 1;

    public VisualEvent()
    {
        type = Feedback.Type.Visual;
    }

    protected void load(XmlPullParser xml, Context context)
    {
        super.load(xml, context);

        try
        {
            xml.require(XmlPullParser.START_TAG, null, "event");

            int num = (xml.getAttributeValue(null, "icon2") != null) ? 2 : 1;
            icons = new Drawable[num];

            InputStream icon_is = context.getAssets().open(xml.getAttributeValue(null, "icon1"));
            icons[0] = Drawable.createFromStream(icon_is, null);

            String icon2_str = xml.getAttributeValue(null, "icon2");
            if(icon2_str != null)
            {
                InputStream icon_is2 = context.getAssets().open(icon2_str);
                icons[1] = Drawable.createFromStream(icon_is2, null);
            }

            String bright_str = xml.getAttributeValue(null, "brightness");
            if (bright_str != null)
                brightness = Float.valueOf(bright_str);

            String dur_str = xml.getAttributeValue(null, "dur");
            if (dur_str != null)
                dur = Integer.valueOf(dur_str);

            String lock_str = xml.getAttributeValue(null, "lock");
            if (lock_str != null)
                lock = Integer.valueOf(lock_str);
        }
        catch(IOException | XmlPullParserException e)
        {
            Log.e(tag, "error parsing config file", e);
        }
    }
}
