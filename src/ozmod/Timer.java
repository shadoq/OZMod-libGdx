/*
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

/**
 * A simple timer Class to count time in milliseconds.
 * @author tsar
 */
public class Timer {
    
    protected long startTime;
    protected long currentTime;
    protected long lastTime;

    public Timer() {
        reset();
    };

    /**
     * Gets the time in ms elapsed between the last call to this method.
     * @return The elapsed time.
     */
   public int getDelta() {
       long current = System.currentTimeMillis();
       long delta = current - lastTime;
       lastTime = current;

       return (int) delta;
    }

   /**
    * Gets the time in ms elapsed since the instanciation of this class or the last call to reset() method.
    * @return The elapsed time.
    */
   public long getElapsed() {
       long current = System.currentTimeMillis();
       long delta = current - startTime;
       return delta;
    }

   /**
    * Resets the timer to zero.
    */
   public void reset() {
        startTime = System.currentTimeMillis();
        currentTime = startTime;
        lastTime = startTime;
   }
}
