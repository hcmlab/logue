/*
 * TactileInstance.java
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
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import hcm.logue.feedback.classes.Feedback;

/**
 * Created by Johnny on 01.12.2014.
 */
public class TactileEvent extends Event
{
    public int[] duration;
    public byte[] intensity;

    public int lock = 0;
    public int lockSelf = 0;

    public float multiplier = 1f; //in case of lock, intensity is multiplied

    public TactileEvent()
    {
        type = Feedback.Type.Tactile;
    }

    protected void load(XmlPullParser xml, Context context)
    {
        super.load(xml, context);

        try
        {
            xml.require(XmlPullParser.START_TAG, null, "event");

            intensity = parseByteArray(xml.getAttributeValue(null, "intensity"), ",");
            duration = parseIntArray(xml.getAttributeValue(null, "duration"), ",");

            String lock_str = xml.getAttributeValue(null, "lock");
            if(lock_str != null)
                lock = Integer.valueOf(lock_str);

            lock_str = xml.getAttributeValue(null, "lockSelf");
            if(lock_str != null)
                lockSelf = Integer.valueOf(lock_str);

            String timeoutMult_str = xml.getAttributeValue(null, "multiplier");
            if(timeoutMult_str != null)
                multiplier = Float.valueOf(timeoutMult_str);
        }
        catch(IOException | XmlPullParserException e)
        {
            Log.e(tag, "error parsing config file", e);
        }
    }
}
