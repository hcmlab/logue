/*
 * SettingsActivity.java
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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Call super :
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Set the activity's fragment :
        SettingsFragment frag = new SettingsFragment();
        frag.status = getIntent().getStringExtra("status");

        getFragmentManager().beginTransaction().replace(android.R.id.content, frag).commit();
    }


    public static class SettingsFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {

        public String status = "Idle";

        @Override
        public void onCreate(Bundle savedInstanceState) {

            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            // Set listener :
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
            pref.registerOnSharedPreferenceChangeListener(this);

            // Set seekbar summary :
            this.findPreference("VAD_THRESHOLD").setSummary(String.valueOf(pref.getInt("VAD_THRESHOLD", 50)));
            this.findPreference("COUNTDOWN").setSummary(String.valueOf(pref.getInt("COUNTDOWN", 10)));
            this.findPreference("MVGAVG_WINDOW").setSummary(String.valueOf(pref.getInt("MVGAVG_WINDOW", 50)));

            if(status.equalsIgnoreCase("Idle"))
            {
                this.findPreference("CAT_OPTIONS").setEnabled(false);
            }
            else if(status.equalsIgnoreCase("Initialized"))
            {
                this.findPreference("CAT_PIPELINE").setEnabled(false);
            }
            else if(status.equalsIgnoreCase("Running"))
            {
                this.findPreference("COUNTDOWN").setEnabled(false);
                this.findPreference("MVGAVG_WINDOW").setEnabled(false);
                this.findPreference("CAT_PIPELINE").setEnabled(false);
            }
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

            if(key.equals("VAD_THRESHOLD") || key.equals("COUNTDOWN") || key.equals("MVGAVG_WINDOW"))
            {
                // Set seekbar summary :
                int value = sharedPreferences.getInt(key, 0);
                findPreference(key).setSummary(String.valueOf(value));
            }
        }
    }
}
