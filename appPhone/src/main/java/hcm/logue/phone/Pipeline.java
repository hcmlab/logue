/*
 * Pipeline.java
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
import android.preference.PreferenceManager;
import android.util.Log;

import com.jjoe64.graphview.GraphView;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import hcm.logue.feedback.FeedbackManager;
import hcm.ssj.audio.AudioConvert;
import hcm.ssj.audio.AudioProvider;
import hcm.ssj.audio.Microphone;
import hcm.ssj.audio.Pitch;
import hcm.ssj.audio.SpeechRate;
import hcm.ssj.body.OverallActivation;
import hcm.ssj.core.Cons;
import hcm.ssj.core.EventChannel;
import hcm.ssj.core.Provider;
import hcm.ssj.core.TheFramework;
import hcm.ssj.event.FloatSegmentEventSender;
import hcm.ssj.event.FloatsEventSender;
import hcm.ssj.event.ThresholdEventSender;
import hcm.ssj.graphic.SignalPainter;
import hcm.ssj.ioput.BluetoothConnection;
import hcm.ssj.ioput.BluetoothEventWriter;
import hcm.ssj.ioput.BluetoothProvider;
import hcm.ssj.ioput.BluetoothReader;
import hcm.ssj.myo.DynAccelerationProvider;
import hcm.ssj.myo.Myo;
import hcm.ssj.praat.Intensity;
import hcm.ssj.signal.Envelope;
import hcm.ssj.signal.MvgAvgVar;
import hcm.ssj.test.EventLogger;

public class Pipeline implements Runnable, SharedPreferences.OnSharedPreferenceChangeListener
{
    String _name = "Logue_Worker_Pipeline";

    enum FeedbackType
    {
        None,
        Visual,
        Haptic,
        VisualAndHaptic
    }

    enum AudioSource
    {
        None,
        Local,
        Bluetooth
    }

    enum Status
    {
        Idle,
        Initialized,
        Running
    }

    private boolean _useMyo = true;
    private AudioSource _audioSource = AudioSource.None;
    private FeedbackType _feedback = FeedbackType.Haptic;

    private boolean _terminate = false;
    private TheFramework _ssj;
    double _frameSize = 0.1;
    ThresholdEventSender _vad;
    MvgAvgVar _activityf;

    private MainActivity _act = null;
    private GraphView _graphs[] = null;
    SharedPreferences _pref;

    FeedbackManager _feedbackManager = null;
    protected Status _status = Status.Idle;

    public Pipeline(MainActivity a, GraphView[] graphs)
    {
        _act = a;
        _graphs = graphs;

        _pref = PreferenceManager.getDefaultSharedPreferences(a);
        _pref.registerOnSharedPreferenceChangeListener(this);

        _pref.edit().putBoolean("MYO", _useMyo).commit();
        _pref.edit().putString("AUDIO", _audioSource.toString()).commit();
        _pref.edit().putString("FEEDBACK", _feedback.toString()).commit();
    }

    public void release()
    {
        Log.i(_name, "stopping feedback manager");
        _feedbackManager.close();
    }

    public void create()
    {
        Log.i(_name, "creating SSJ pipeline ...");

        try
        {
            //setup an SSJ pipeline to send sensor data to SSI
            _ssj = TheFramework.getFramework();
            _ssj.options.bufferSize.set(10.0f);
            _ssj.options.countdown.set(7);
            _pref.edit().putInt("COUNTDOWN", _ssj.options.countdown.get()).commit(); //initialize value in the GUI

            BluetoothEventWriter blw = null;
            SignalPainter paint = null;

            if (_feedback == FeedbackType.Visual)
            {
                //visual feedback requires an HMD to be connected via bluetooth
                blw = new BluetoothEventWriter();
                blw.options.connectionName.set("logue");
                blw.options.connectionType.set(BluetoothConnection.Type.SERVER);
                _ssj.addComponent(blw);
            }
            if (_feedback == FeedbackType.Haptic)
            {
                _feedbackManager = new FeedbackManager(_act);

                try
                {
                    _feedbackManager.load("config.xml");
                }
                catch (IOException | XmlPullParserException e)
                {
                    throw new RuntimeException("failed reading feedback config file", e);
                }
            }

            if (_audioSource != AudioSource.None)
            {
                Provider audio;
                if (_audioSource == AudioSource.Bluetooth)
                {
                    BluetoothReader bl = new BluetoothReader();
                    bl.options.connectionName.set("audio");
                    bl.options.connectionType.set(BluetoothConnection.Type.SERVER);
                    _ssj.addSensor(bl);

                    BluetoothProvider audio_raw = new BluetoothProvider();
                    audio_raw.options.outputClass.set(new String[]{"Audio"});
                    audio_raw.options.bytes.set(2);
                    audio_raw.options.dim.set(1);
                    audio_raw.options.type.set(Cons.Type.SHORT);
                    audio_raw.options.sr.set(16000.);
                    bl.addProvider(audio_raw);

                    audio = new AudioConvert();
                    _ssj.addTransformer((AudioConvert) audio, audio_raw, _frameSize, 0);
                } else
                {
                    Microphone mic = new Microphone();
                    _ssj.addSensor(mic);

                    audio = new AudioProvider();
                    ((AudioProvider) audio).options.sampleRate.set(16000);
                    ((AudioProvider) audio).options.scale.set(true);
                    mic.addProvider((AudioProvider) audio);
                }

                /*
                 * Processing
                 */
                Pitch pitch = new Pitch();
                pitch.options.detector.set(Pitch.YIN);
                pitch.options.computePitch.set(false);
                pitch.options.computePitchedState.set(false);
                _ssj.addTransformer(pitch, audio, _frameSize, 0);

                Envelope pitch_env = new Envelope();
                pitch_env.options.attackSlope.set(0.3f); //in units, 1.0 is max
                pitch_env.options.releaseSlope.set(0.05f);
                _ssj.addTransformer(pitch_env, pitch, _frameSize * 10, 0);

                Intensity intensity = new Intensity();
                intensity.options.subtractMeanPressure.set(false);
                _ssj.addTransformer(intensity, audio, _frameSize * 10, 0);

                _vad = new ThresholdEventSender();
                _vad.options.sender.set("SSJ");
                _vad.options.event.set("VoiceActivity");
                _vad.options.thresin.set(new float[]{40.0f}); //praat intensity
                _pref.edit().putInt("VAD_THRESHOLD", (int)_vad.options.thresin.get()[0]).commit(); //initialize value in the GUI
                _vad.options.mindur.set(1.0);
                _vad.options.maxdur.set(9.0);
                _vad.options.hangin.set((int) (intensity.getOutputStream().sr * 0.2)); //0.2s
                _vad.options.hangout.set((int) (intensity.getOutputStream().sr * 0.5)); //0.5s
                Provider[] vad_in = {intensity};
                _ssj.addConsumer(_vad, vad_in, _frameSize * 10, 0);
                EventChannel vad_channel = _ssj.registerEventProvider(_vad);

                SpeechRate sr = new SpeechRate();
                sr.options.sender.set("SSJ");
                sr.options.event.set("SpeechRate");
                sr.options.thresholdVoicedProb.set(0.3f);
                sr.options.width.set(3);
                Provider[] sr_in = {intensity, pitch_env};
                _ssj.addConsumer(sr, sr_in, vad_channel);
                EventChannel sr_channel = _ssj.registerEventProvider(sr);

                /*
                 * Output
                 */
                FloatSegmentEventSender evintensity = new FloatSegmentEventSender();
                evintensity.options.sender.set("SSJ");
                evintensity.options.event.set("Intensity");
                _ssj.addConsumer(evintensity, intensity, vad_channel);
                EventChannel intensity_channel = _ssj.registerEventProvider(evintensity);

                if (blw != null)
                {
                    _ssj.registerEventListener(blw, sr_channel);
                    _ssj.registerEventListener(blw, intensity_channel);
                }
                if (_feedbackManager != null)
                {
                    _feedbackManager.registerEventChannel(sr_channel);
                    _feedbackManager.registerEventChannel(intensity_channel);
                }

                /*
                 * Visualizer
                 */
                paint = new SignalPainter();
                paint.options.manualBounds.set(true);
                paint.options.min.set(0.);
                paint.options.max.set(1.);
                paint.options.renderMax.set(true);
                paint.options.graphView.set(_graphs[0]);
                _ssj.addConsumer(paint, audio, 0.1, 0);

                paint = new SignalPainter();
                paint.options.graphView.set(_graphs[0]);
                paint.options.renderMax.set(false);
                paint.options.colors.set(new int[]{0xffff9900, 0xff009999, 0xff990000, 0xffff00ff, 0xff000000, 0xff339900});
                paint.options.manualBounds.set(true);
                paint.options.min.set(0.);
                paint.options.max.set(1.);
                paint.options.secondScaleDim.set(0);
                paint.options.secondScaleMin.set(0.);
                paint.options.secondScaleMax.set(100.);
                _ssj.addConsumer(paint, intensity, 1.0, 0);
            }

            // Movement
            if (_useMyo)
            {
                Myo myo = new Myo();
                _ssj.addSensor(myo);

                DynAccelerationProvider acc = new DynAccelerationProvider();
                myo.addProvider(acc);

                OverallActivation activity = new OverallActivation();
                _ssj.addTransformer(activity, acc, _frameSize, 5.0);

                _activityf = new MvgAvgVar();
                _activityf.options.window.set(10.);
                _pref.edit().putInt("MVGAVG_WINDOW", _activityf.options.window.get().intValue()).commit(); //initialize value in the GUI
                _ssj.addTransformer(_activityf, activity, _frameSize, 0);

                FloatsEventSender evactivity = new FloatsEventSender();
                evactivity.options.sender.set("SSJ");
                evactivity.options.event.set("OverallActivation");
                _ssj.addConsumer(evactivity, _activityf, _frameSize * 5, 0);
                EventChannel activity_channel = _ssj.registerEventProvider(evactivity);

                EventLogger log = new EventLogger();
                _ssj.registerEventListener(log, activity_channel);
                _ssj.addComponent(log);

                if (blw != null)
                {
                    _ssj.registerEventListener(blw, activity_channel);
                }
                if (_feedbackManager != null)
                {
                    _feedbackManager.registerEventChannel(activity_channel);
                }

                paint = new SignalPainter();
                paint.options.manualBounds.set(true);
                paint.options.min.set(-3.);
                paint.options.max.set(3.);
                paint.options.numVLabels.set(4);
                paint.options.graphView.set(_graphs[1]);
                _ssj.addConsumer(paint, acc, 0.1, 0);

                paint = new SignalPainter();
                paint.options.graphView.set(_graphs[1]);
                paint.options.colors.set(new int[]{0xff990000, 0xffff00ff, 0xff000000, 0xff339900});
                paint.options.secondScaleDim.set(0);
                paint.options.secondScaleMin.set(0.);
                paint.options.secondScaleMax.set(3.);
                paint.options.manualBounds.set(true);
                paint.options.min.set(-3.);
                paint.options.max.set(3.);
                paint.options.numVLabels.set(4);
                _ssj.addConsumer(paint, _activityf, 0.1, 0);
            }
        }
        catch(Exception e)
        {
            Log.e(_name, "error in creating pipeline", e);
            throw new RuntimeException(e);
        }

        _status = Status.Initialized;
        Log.i(_name, "pipeline ready");

        if(_feedbackManager != null)
            new Thread(_feedbackManager).start();
    }

    public void run()
    {
        _terminate = false;

        try
        {
            _ssj.Start();
        }
        catch(Exception e)
        {
            Log.e(_name, "error starting pipeline", e);
            throw new RuntimeException(e);
        }

        _status = Status.Running;
        _act.notifyPipeState(true);

        while(!_terminate)
        {
            try
            {
                synchronized(this)
                {
                    this.wait();
                }
            }
            catch (InterruptedException e) {}
        }

        Log.i(_name, "stopping SSJ");
        try
        {
            _ssj.Stop();
        }
        catch(Exception e)
        {
            Log.e(_name, "error stopping pipeline", e);
            throw new RuntimeException(e);
        }

        _status = Status.Initialized;
        _act.notifyPipeState(false);
    }

    public void terminate()
    {
        _terminate = true;

        synchronized(this)
        {
            this.notify();
        }
    }

    public boolean isRunning()
    {
        if(_ssj == null)
            return false;

        return _ssj.isRunning();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
    {
        if(key.equalsIgnoreCase("VAD_THRESHOLD"))
            _vad.options.thresin.get()[0] = sharedPreferences.getInt(key, (int)_vad.options.thresin.get()[0]);
        else if(key.equalsIgnoreCase("COUNTDOWN"))
            _ssj.options.countdown.set(sharedPreferences.getInt(key, _ssj.options.countdown.get()));
        else if(key.equalsIgnoreCase("MVGAVG_WINDOW"))
            _activityf.options.window.set((double)sharedPreferences.getInt(key, _activityf.options.window.get().intValue()));
        else if(key.equalsIgnoreCase("MYO"))
            _useMyo = sharedPreferences.getBoolean(key, _useMyo);
        else if(key.equalsIgnoreCase("AUDIO"))
            _audioSource = AudioSource.valueOf(sharedPreferences.getString(key, _audioSource.toString()));
        else if(key.equalsIgnoreCase("FEEDBACK"))
        {
            _feedback = FeedbackType.valueOf(sharedPreferences.getString(key, _feedback.toString()));
            if(_feedback == FeedbackType.Haptic || _feedback == FeedbackType.VisualAndHaptic)
            {
                _useMyo = true;
                _pref.edit().putBoolean("MYO", _useMyo).commit();
            }
        }
    }

    public Status getStatus()
    {
        return _status;
    }
}
