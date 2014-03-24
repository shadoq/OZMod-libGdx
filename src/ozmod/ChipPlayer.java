package ozmod;

public interface ChipPlayer {

	final static boolean OZ_DEBUG=false;
	public OZMod.ERR load(LoaderFromMemory _input);
	public void play();
	public void done();
	public void run();
	public void setLoopable(boolean _b);
	public void setVolume(float volume);
	public void setFrequency(int frequency);
	public void setDaemon(boolean on);
	public boolean isLoopable();
	public byte[] getMixBuffer();
}
