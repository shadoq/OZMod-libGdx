OZMod-libGdx
============

OZMod port to use in libGdx based on http://www.tsarevitch.org/ozmod/.
First modification for libGdx by magali.

My modification :
- fix some bugs i play XM Mod
- add interface to universal access
- reformat some fragment code

Example of usage:

```java
package net.shad.s3rend.test;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import ozmod.ChipPlayer;
import ozmod.OZMod;
import ozmod.XMPlayer;
import net.shad.s3rend.main.S3App;
import net.shad.s3rend.main.S3File;
import net.shad.s3rend.main.S3Gfx;
import net.shad.s3rend.main.S3Log;

public class OzPlayTest extends S3App {

	OZMod ozm;
	ChipPlayer player;
	int frequency;

	@Override
	public void initalize() {
		ozm = new OZMod();
		ozm.initOutput();
		 play("sound/fish_and_chips.xm", 1);
//		play("sound/radix-imaginary_friend.xm", 1);
	}

	@Override
	public void update() {
	}

	@Override
	public void render(S3Gfx gfx) {
		gfx.clear(0.2f, 0.0f, 0.0f);
	}

	public void play(String file, float volume) {

		FileHandle module = S3File.getFileHandle(file);
		S3Log.info("OzMod", "Play: " + module.path());
		player = ozm.getPlayer(module);
		// frequency = 44100;
		// frequency = 48000;
		frequency = 96000;
		player.setFrequency(frequency);
		player.setVolume(volume);
		player.setDaemon(true);
		player.setLoopable(false);
		player.play();
	}
}
```
