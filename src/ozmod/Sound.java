/*
OZMod - Java Sound Library
Copyright (C) 2011  Igor Kravtchenko

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

Contact the author: igor@tsarevitch.org
*/

package ozmod;

import java.io.*;
//import javax.sound.sampled.*;

/**
 *
 * @author tsar
 */
public class Sound {

    public enum PULSE_TYPE
    {
        PCM,
        UPCM,
        ULAW,
        ALAW,
    };

    Sound()
    {
    }

//    OZMod.ERR load(File _file)
//    {
//        AudioInputStream stream;
//        try {
//            stream = AudioSystem.getAudioInputStream(_file);
//        }
//        catch(IOException e) {
//            return OZMod.proceedError(OZMod.ERR.FILENOTFOUND);
//        }
//        catch(UnsupportedAudioFileException e) {
//            return OZMod.proceedError(OZMod.ERR.INVALIDFORMAT);
//        }
//
//        file_ = _file;
//
//        try {
//            clip_ = AudioSystem.getClip(OZMod.usedMixer_.getMixerInfo());
//            clip_.open(stream);
//        }
//        catch(LineUnavailableException e) {
//            return OZMod.proceedError(OZMod.ERR.DEVICESATURATE);
//        }
//        catch(IOException e) {
//            return OZMod.proceedError(OZMod.ERR.DEVICESATURATE);
//        }
//
//        AudioFormat format = stream.getFormat();
//        nbChannels_ = format.getChannels();
//        frequency_ = (int) format.getSampleRate();
//        nbBits_ = format.getSampleSizeInBits();
//        formatDesc_ = format.toString();
//
//        return OZMod.proceedError(OZMod.ERR.NOERR);
//    }

    public int getNbChannels()
    {
        return nbChannels_;
    }

    public int getNbBitsPerSample()
    {
        return nbBits_;
    }

    public int getFrequency()
    {
        return frequency_;
    }

    public String getFormatDescription()
    {
        return formatDesc_;
    }

//    public void play(boolean loop)
//    {
//        if (loop == false)
//            clip_.start();
//        else
//            clip_.loop(Clip.LOOP_CONTINUOUSLY);
//    }

//    public void setLoop(int _start, int _end)
//    {
//        clip_.setLoopPoints(_start, _end);
//    }

//    public Sound duplicate()
//    {
//        Sound sound = new Sound();
//        sound.load(file_);
//        return sound;
//    }

//    public Clip getClip()
//    {
//        return clip_;
//    }
//
//    Clip clip_;
    int nbChannels_;
    int frequency_;
    int nbBits_;
    PULSE_TYPE pulseType_;
    String formatDesc_;
    File file_;
}
