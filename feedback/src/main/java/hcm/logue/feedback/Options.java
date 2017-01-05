/*
 * Config.java
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

import android.support.v4.util.SimpleArrayMap;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Created by Johnny on 02.12.2014.
 */
public class Options {

    protected SimpleArrayMap<String,String> _options = new SimpleArrayMap<String, String>();

    public void load(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, null, "options");

        //iterate through classes
        while (parser.next() != XmlPullParser.END_DOCUMENT)
        {
            if(parser.getEventType() == XmlPullParser.START_TAG && parser.getName().equalsIgnoreCase("option"))
            {
                String name = parser.getAttributeValue(null, "name");
                String value = "";
                value = parser.nextText();

                _options.put(name, value);
            }
            else if(parser.getEventType() == XmlPullParser.END_TAG && parser.getName().equalsIgnoreCase("options"))
                break; //jump out once we reach end tag
        }

        parser.require(XmlPullParser.END_TAG, null, "options");
    }

    public String getOption(String name) {
        return _options.get(name);
    }

    public float getOptionF(String name) {
        return Float.parseFloat(_options.get(name));
    }

    public int getOptionI(String name) {
        return Integer.parseInt(_options.get(name));
    }
}
