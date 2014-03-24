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

//import javax.sound.sampled.*;

public class JMixer extends Thread {

    static final int TIMER_RATE = 20;
    
//    public JMixer(Mixer _mixer)
//    {
//        mixer_ = _mixer;
//    }
    
    OZMod.ERR install(int frequency_)
    {
        frequency_ = 44100;
        nbSampAtEachPoll_ = frequency_ / TIMER_RATE; // must be an integer value: by limiting possible frequency and with a good timer rate, there is no problem

//        AudioFormat format = new AudioFormat(frequency_, 16, 2, true, true);
//
//        try {
//            line_ = AudioSystem.getSourceDataLine(format, mixer_.getMixerInfo());
//            line_.open(format, frequency_);
//        }
//        catch(LineUnavailableException e) {
//        }
        
        soundBufferLen_ = frequency_ * 4;
        pcm_ = new byte[soundBufferLen_];
        
        return OZMod.ERR.NOERR;
    }

    void launch()
    {
        bRunning_ = true;
        start();
    }
    
    public void run()
    {
        long cumulTime = 0;
        
        while(bRunning_)
        {
            long elapsed = timer_.getDelta();
            cumulTime += elapsed;
                
            if (cumulTime >= TIMER_RATE)
            {
                cumulTime  -= TIMER_RATE;
            }
        }
    }
 
    boolean bRunning_;

    int frequency_;
    int nbSampAtEachPoll_;
    
    Timer timer_;
    
//    Mixer mixer_;
//    SourceDataLine line_;
    int soundBufferLen_;
    byte pcm_[];
}
