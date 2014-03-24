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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;

public class MODPlayer extends Thread implements ChipPlayer {

	protected long maxPlayTime = 1000 * 60 * 10;

	final static int MOD_MIN_PERIOD = 40;
	final static int MOD_MAX_PERIOD = 11520;

	int period_[] = { 1712, 1616, 1524, 1440, 1356, 1280, 1208, 1140, 1076, 1016, 960, 907 };
	private AudioDevice gdxAudio;

	int nbVoices_;
	byte songName_[] = new byte[20];

	int nbInstrus_;
	Instru instrus_[] = new Instru[31];

	int listLen_;
	int nbPatterns_;
	int listPatterns_[] = new int[128];
	Pattern patterns_[];

	Voice voices_[];

	int speed_, BPM_;
	float MODvolume_;
	float panPower_;

	boolean running_;
	boolean done_ = false;

	Timer timer_;

	int tick_;
	int patternDelay_;

	int posChanson_, posInPattern_;

	boolean bGotPatternLoop_ = false;
	int patternPosLoop_ = 0;
	int patternLoopLeft_ = 0;

	int freq_ = 44100;
	byte[] pcm_;
	short[] pcms_;
	ChannelsList chans_ = new ChannelsList();

	boolean loopable_ = false;
	private boolean paused = false;

	class Instru {

		Instru() {
		}

		byte name[] = new byte[22];
		int finetune;
		int vol;
		int startLoop;
		int lengthLoop;
		int len;
		AudioData audio = new AudioData();
	};

	class Note {

		Note() {
		}

		int note;
		int effect;
		int effectOperand;
		int numInstru;
	};

	class Row {
		Row() {
		}

		Note notes[];
	};

	class Pattern {

		Pattern() {
		}

		Row rows[] = new Row[64];
	}

	class Voice {

		void vibrato() {
			int vibSeek;
			int periodOffset;

			vibSeek = (vibratoCounter_ >> 2) & 0x3f;

			switch (vibratoForm_) {
				default:
					periodOffset = TrackerConstant.vibratoTable[vibSeek];
				break;
			}

			periodOffset *= vibratoProf_;
			periodOffset >>= 7;
			periodOffset <<= 2;
			period_ = periodBAK_ + periodOffset;
			vibratoCounter_ += vibratoSpeed_;
		}

		void tremolo() {
			int tremoloSeek;
			int volumeOffset;

			tremoloSeek = (tremoloCounter_ >> 2) & 0x3f;

			switch (tremoloForm_) {
				default:
					volumeOffset = TrackerConstant.vibratoTable[tremoloSeek];
				break;
			}

			volumeOffset *= tremoloProf_;
			volumeOffset >>= 6;
			volume_ = volumeBAK_ + volumeOffset;

			tremoloCounter_ += tremoloSpeed_;
		}

		void portamentoToNote(int _speed) {
			if (period_ < dstPeriod_) {
				period_ += _speed;
				if (period_ > dstPeriod_)
					period_ = dstPeriod_;
			} else if (period_ > dstPeriod_) {
				period_ -= _speed;
				if (period_ < dstPeriod_)
					period_ = dstPeriod_;
			}
		}

		void soundUpdate() {
			int freq;
			float vol, pan;

			if (instruPlaying_ == null)
				return;

			freq = (3579546 << 2) / period_;

			// Clamp the Volume voice
			if (volume_ < 0)
				volume_ = 0;
			else if (volume_ > 64)
				volume_ = 64;

			// Clamp the Period voice
			if (period_ < MOD_MIN_PERIOD)
				period_ = MOD_MIN_PERIOD;
			else if (period_ > MOD_MAX_PERIOD)
				period_ = MOD_MAX_PERIOD;

			vol = volume_ / 64.0f;
			if (vol < 0)
				vol = 0;
			else if (vol > 1)
				vol = 1;

			if (bNoteCutted_ == true)
				vol = 0;

			if ((iVoice_ & 1) == 1)
				pan = panPower_;
			else
				pan = -panPower_;

			if (bGotRetrigNote_ == true) {
				if ((tick_ - 1) % tickForRetrigNote_ == 0)
					bNeedToBePlayed_ = true;
			}

			if (bNeedToBePlayed_ == false) {
				channel_.frequency = freq;
				channel_.step = freq / (float) (freq_);
				channel_.vol = vol;
				channel_.setPan(pan);
				return;
			}

			int startPos;
			if (effect_ == 0x9) {
				effect_ = 0xfff;
				startPos = samplePosJump_;
			} else
				startPos = 0;

			chans_.removeChannel(channel_);

			if (tick_ >= tickBeforeSample_) {
				channel_.frequency = freq;
				channel_.step = freq / (float) (freq_);
				channel_.pos = startPos;
				channel_.vol = vol;
				channel_.setPan(pan);
				channel_.audio = instruPlaying_.audio;
				chans_.addChannel(channel_);

				bNeedToBePlayed_ = false;
				tickBeforeSample_ = 0;
			}
		}

		void updateSoundWithEffect() {
			switch (effect_) {
				case 0x0fff:
				// NOTHING
				break;

				case 0x0:
					// ARPEGGIO
					switch (arpeggioCount_ % 3) {
						case 0:
							period_ = getPeriod(note_ + arp2_, fineTune_);
						break;
						case 1:
							period_ = getPeriod(note_ + arp1_, fineTune_);
						break;
						case 2:
							period_ = getPeriod(note_, fineTune_);
						break;
					}
					arpeggioCount_++;
				break;

				case 0x1:
					// PORTAMENTO UP
					period_ -= portaUp_;
				break;

				case 0x2:
					// PORTAMENTO DOWN
					period_ += portaDown_;
				break;

				case 0x3:
					// PORTAMENTO TO
					portamentoToNote(portamentoSpeed_);
				break;

				case 0x4:
					// VIBRATO
					vibrato();
				break;

				case 0x5:
					// PORTAMENTO TO + VOLUME SLIDING
					portamentoToNote(portamentoSpeed_);
					volume_ += volumeSlidingSpeed_;
				break;

				case 0x6:
					// VIBRATO + VOLUME SLIDING
					vibrato();
					volume_ += volumeSlidingSpeed_;
				break;

				case 0x7:
					// TREMOLO
					tremolo();
				break;

				case 0xa:
					// VOLUME SLIDING
					volume_ += volumeSlidingSpeed_;
				break;

				case 0xec:
					if (tick_ - 1 == noteCutDelay_)
						bNoteCutted_ = true;
				break;

				default:
				// STILL NOTHING ;)
				break;
			}
		}

		Instru instruPlaying_ = null;
		Instru instruToPlay_ = null;

		Channel channel_ = new Channel();

		int iVoice_;
		int period_ = 0, dstPeriod_ = 0, periodBAK_ = 0;
		int arpeggioCount_ = 0, arp1_ = 0, arp2_ = 0;
		int note_ = 0, effect_ = 0xfff, effectOperand_ = 0;
		boolean bNeedToStopSamplePlaying_ = false;
		boolean bNeedToBePlayed_ = false;

		int lastDCommandParam_ = 0;
		int lastECommandParam_ = 0;
		int lastFCommandParam_ = 0;

		int volumeSlidingSpeed_ = 0;

		int portamentoSpeed_ = 0;

		boolean bGotArpeggio_ = false;

		int portaUp_ = 0, portaDown_ = 0;

		int tremorValue_;
		int tremorCounter_;
		boolean bGotTremor_;

		boolean bGotVibrato_ = false;
		int vibratoCounter_ = 0;
		int vibratoForm_ = 0;
		int vibratoSpeed_ = 0, vibratoProf_ = 0;
		boolean bFineVibrato_ = false;

		boolean bGotTremolo_ = false;
		int tremoloCounter_ = 0;
		int tremoloForm_ = 0;
		int tremoloSpeed_ = 0, tremoloProf_ = 0;

		boolean bGotNoteCut_ = false;
		int noteCutDelay_;
		boolean bNoteCutted_ = false;

		int fineTune_ = 0;
		int volume_ = 0, volumeBAK_ = 0;

		int tickBeforeSample_;
		int samplePosJump_;

		boolean bGotRetrigNote_ = false;
		int tickForRetrigNote_;
		int volFadeOutForRetrig_;
	}

	MODPlayer() {
	}

	protected void finalize() {
		running_ = false;
	}

	public OZMod.ERR load(LoaderFromMemory _input) {
		byte tmp[] = new byte[20];

		// Normal MOD?
		_input.seek(1080);
		_input.read(tmp, 0, 4);
		String format = new String(tmp).substring(0, 4);
		if (format.compareTo("M.K.") == 0)
			nbVoices_ = 4;
		else if (format.compareTo("FLT4") == 0)
			nbVoices_ = 4;
		else if (format.compareTo("6CHN") == 0)
			nbVoices_ = 6;
		else if (format.compareTo("8CHN") == 0)
			nbVoices_ = 6;
		else if (format.compareTo("16CH") == 0)
			nbVoices_ = 6;
		else
			return OZMod.proceedError(OZMod.ERR.BADFORMAT);

		// Song name
		_input.seek(0);
		_input.read(songName_, 0, 20);

		// 31 samples
		nbInstrus_ = 31;
		for (int i = 0; i < nbInstrus_; i++) {
			Instru instru = new Instru();
			instrus_[i] = instru;
			_input.read(instru.name, 0, 22);

			instru.len = _input.readUShort() * 2;
			instru.finetune = ozmod.TrackerConstant.finetune[_input.readByte() & 0xf];
			instru.vol = _input.readByte();
			instru.startLoop = _input.readUShort() * 2;
			instru.lengthLoop = _input.readUShort() * 2;
			if (instru.lengthLoop < 4)
				instru.lengthLoop = 0;
		}
		// end of header

		_input.seek(950);
		listLen_ = _input.readUByte();

		_input.seek(952);
		for (int i = 0; i < 128; i++) {
			int pattern = _input.readUByte();
			listPatterns_[i] = pattern;
		}

		// Calculate the number of patterns
		nbPatterns_ = 0;
		for (int i = 0; i < 128; i++) {
			if (listPatterns_[i] > nbPatterns_)
				nbPatterns_ = listPatterns_[i];
		}
		nbPatterns_++;
		_input.seek(1084);

		// Read patterns
		patterns_ = new Pattern[nbPatterns_];
		for (int i = 0; i < nbPatterns_; i++) {
			Pattern pat = new Pattern();
			patterns_[i] = pat;

			for (int j = 0; j < 64; j++) {
				pat.rows[j] = new Row();
				pat.rows[j].notes = new Note[nbVoices_];
				for (int k = 0; k < nbVoices_; k++) {
					pat.rows[j].notes[k] = new Note();

					int byte1, byte2, byte3, byte4;
					byte1 = _input.readByte() & 0xff;
					byte2 = _input.readByte() & 0xff;
					byte3 = _input.readByte() & 0xff;
					byte4 = _input.readByte() & 0xff;

					int period = ((byte1 & 0xf) << 8) + byte2;
					int l = 0;
					for (l = 0; l < 60; l++) {
						if (period >= ozmod.TrackerConstant.defaultPeriod[l])
							break;
					}

					l++;
					if (l == 61)
						l = 0;

					Note note;
					note = pat.rows[j].notes[k];
					note.note = l;
					note.effect = byte3 & 0xf;
					note.effectOperand = byte4;
					note.numInstru = (((byte1 & 0xf0) + (byte3 >> 4)) & 0xff) - 1;
				}
			}
		}

		// Sample read
		_input.seek(1084 + nbPatterns_ * 256 * nbVoices_);

		for (int i = 0; i < 31; i++) {
			Instru instru = instrus_[i];
			int len = instru.len;
			if (len == 0)
				continue;
			byte pcm[] = new byte[len];
			_input.readFully(pcm);

			if (instru.lengthLoop == 0)
				instru.audio.make(pcm, 8, 1);
			else
				instru.audio
						.make(pcm, 8, 1, instru.startLoop, instru.startLoop + instru.lengthLoop, AudioData.LOOP_FORWARD);
		}

		voices_ = new Voice[nbVoices_];
		for (int i = 0; i < nbVoices_; i++) {
			Voice voice = new Voice();
			voices_[i] = voice;
			voice.iVoice_ = i;
		}

		return OZMod.proceedError(OZMod.ERR.NOERR);
	}

	public void setVolume(float _vol) {
		MODvolume_ = _vol;
	}

	public void setPanPower(float _p) {
		panPower_ = _p;
	}

	void dispatchNotes() {
		int iInstru;
		int note, effect, effectOperand, effectOperand2;

		int actuPattern = listPatterns_[posChanson_];
		if (actuPattern >= nbPatterns_)
			actuPattern = 0;
		int actuPos = posInPattern_;

		int newSpeed = 0, newBPM = 0;

		boolean bGotPatternJump = false;
		boolean bGotPatternBreak = false;
		int whereToJump = 0;
		int whereToBreak = 0;

		for (int iVoice = 0; iVoice < nbVoices_; iVoice++) {

			Voice voice = voices_[iVoice];
			Row actuRow = patterns_[actuPattern].rows[actuPos];
			Note actuNote = actuRow.notes[iVoice];

			note = actuNote.note;
			effect = actuNote.effect;
			effectOperand = actuNote.effectOperand;
			iInstru = actuNote.numInstru;

			// Reset GotVibrato if Vibrato no more used
			if ((effect != 0x4 && effect != 0x6) && voice.bGotVibrato_ == true) {
				voice.period_ = voice.periodBAK_;
				voice.bGotVibrato_ = false;
			}

			// Reset GotTremolo if Tremolo no more used
			if (effect != 0x7 && voice.bGotTremolo_ == true) {
				voice.volume_ = voice.volumeBAK_;
				voice.bGotTremolo_ = false;
			}

			// For safety, restore Period after Arpeggio
			if (voice.bGotArpeggio_ == true)
				voice.period_ = voice.periodBAK_;

			boolean bAllowToUpdateNote = true;
			voice.bNeedToStopSamplePlaying_ = false;

			if (voice.instruPlaying_ != null) {
				if (effect == 0x03 || effect == 0x05)
					bAllowToUpdateNote = false;
			}

			if (iInstru >= 0) {
				if (iInstru <= nbInstrus_)
					voice.instruToPlay_ = instrus_[iInstru];
				else {
					chans_.removeChannel(voice.channel_);
					voice.instruPlaying_ = null;
					voice.instruToPlay_ = null;
				}

				if (voice.instruToPlay_ != null) {
					voice.volume_ = voice.instruToPlay_.vol;
					voice.fineTune_ = voice.instruToPlay_.finetune;
				}

				voice.vibratoCounter_ = 0;
				voice.tremoloCounter_ = 0;
			}

			if (note > 0 && voice.instruToPlay_ != null) {
				int period;
				period = getPeriod(note, voice.fineTune_);

				voice.note_ = note;
				voice.periodBAK_ = period;

				if (bAllowToUpdateNote == true) {
					voice.instruPlaying_ = voice.instruToPlay_;

					if (iInstru >= 0)
						voice.dstPeriod_ = period;

					voice.bNeedToBePlayed_ = true;
					voice.bNoteCutted_ = false;

					voice.period_ = period;
					voice.bNoteCutted_ = false;
				}
			}

			voice.bGotArpeggio_ = false;
			voice.bGotRetrigNote_ = false;
			voice.effect_ = 0x0fff;

			// Standart Effect
			switch (effect) {
				case 0x0:
					// ARPEGGIO
					if (effectOperand != 0) {
						voice.effect_ = effect;
						voice.effectOperand_ = effectOperand;
						voice.arp1_ = effectOperand & 0xf;
						voice.arp2_ = effectOperand >> 4;
						voice.bGotArpeggio_ = true;
						voice.arpeggioCount_ = 0;
					}
					;
				break;

				case 0x1:
					// PORTAMENTO UP
					voice.effect_ = effect;
					voice.portaUp_ = effectOperand * 4;
				break;

				case 0x2:
					// PORTAMENTO DOWN
					voice.effect_ = effect;
					voice.portaDown_ = effectOperand * 4;
				break;

				case 0x3:
					// PORTAMENTO TO
					if (note > 0 && voice.instruPlaying_ != null) {
						voice.dstPeriod_ = getPeriod(note, voice.fineTune_);
					}
					voice.effect_ = effect;
					if (effectOperand != 0)
						voice.portamentoSpeed_ = effectOperand * 4;
				break;

				case 0x4:
					// VIBRATO
					voice.effect_ = effect;
					if ((effectOperand & 0xf0) != 0)
						voice.vibratoSpeed_ = (effectOperand >> 4) * 4;

					if ((effectOperand & 0x0f) != 0)
						voice.vibratoProf_ = effectOperand & 0x0f;
				break;

				case 0x5:
					// PORTAMENTO TO + VOLUME SLIDING
					if (note != 0 && voice.instruPlaying_ != null)
						voice.dstPeriod_ = getPeriod(note, voice.fineTune_);
					voice.effect_ = effect;
					if ((effectOperand & 0xf0) != 0)
						voice.volumeSlidingSpeed_ = (byte) (effectOperand >> 4);
					else if ((effectOperand & 0x0f) != 0)
						voice.volumeSlidingSpeed_ = (byte) (-(effectOperand & 0x0f));
					else
						voice.volumeSlidingSpeed_ = 0;
				break;

				case 0x6:
					// VIBRATO + VOLUME SLIDING
					voice.effect_ = effect;
					if ((effectOperand & 0xf0) != 0)
						voice.volumeSlidingSpeed_ = (byte) (effectOperand >> 4);
					else if ((effectOperand & 0x0f) != 0)
						voice.volumeSlidingSpeed_ = (byte) (-(effectOperand & 0x0f));
				break;

				case 0x7:
					// TREMOLO
					voice.effect_ = effect;
					if ((effectOperand & 0xf0) != 0)
						voice.tremoloSpeed_ = (byte) ((effectOperand >> 4) * 4);
					if ((effectOperand & 0x0f) != 0)
						voice.tremoloProf_ = (byte) (effectOperand & 0x0f);
				break;

				case 0x9:
					// SAMPLE JUMP
					voice.effect_ = effect;
					if (effectOperand != 0)
						voice.samplePosJump_ = effectOperand << 8;
				break;

				case 0xa:
					// VOLUME SLIDING
					voice.effect_ = effect;
					if ((effectOperand & 0xf0) != 0)
						voice.volumeSlidingSpeed_ = effectOperand >> 4;
					else if ((effectOperand & 0x0f) != 0)
						voice.volumeSlidingSpeed_ = -(effectOperand & 0xf);
					else
						voice.volumeSlidingSpeed_ = 0;
				break;

				case 0xb:
					// POSITION JUMP
					bGotPatternJump = true;
					whereToJump = effectOperand;
				break;

				case 0xc:
					// SET VOLUME
					voice.volume_ = effectOperand;
				break;

				case 0xd:
					// PATTERN BREAK
					bGotPatternBreak = true;
					whereToBreak = ((effectOperand & 0xf0) >> 4) * 10 + (effectOperand & 0x0f);
				// Yes, posJump is given in BCD format. What is the interest?
				// Absolutely none, thanks MOD..
				break;

				case 0xe:
					// MISCELLANEOUS
					effectOperand2 = effectOperand & 0xf;
					effectOperand >>= 4;

					switch (effectOperand) {
						case 0x1:
							// FineSlideUp
							voice.period_ -= effectOperand2 * 4;
						break;
						case 0x2:
							// FineSlideDown
							voice.period_ += effectOperand2 * 4;
						break;
						case 0x4:
							// Set Vibrato Form
							voice.vibratoForm_ = effectOperand2;
						break;
						case 0x5:
							// Set FineTune
							// if (!Note) break;
							voice.fineTune_ = effectOperand2 << 4;
						break;
						case 0x6:
							// Pattern Loop
							if (effectOperand2 != 0) {
								if (bGotPatternLoop_ == false) {
									bGotPatternLoop_ = true;
									patternLoopLeft_ = effectOperand2;
								}
								patternLoopLeft_--;
								if (patternLoopLeft_ < 0)
									bGotPatternLoop_ = false;
								else
									posInPattern_ = patternPosLoop_ - 1;
							} else
								patternPosLoop_ = actuPos;
						break;
						case 0x7:
							// Set Tremolo Form
							voice.tremoloForm_ = effectOperand2;
						break;
						case 0x9:
							// Retrigger Note
							if (effectOperand2 == 0)
								break;
							voice.bGotRetrigNote_ = true;
							voice.tickForRetrigNote_ = effectOperand2;
						break;
						case 0xa:
							// Fine Volumesliding Up
							voice.volume_ += effectOperand2;
						break;
						case 0xb:
							// Fine Volumesliding Down
							voice.volume_ -= effectOperand2;
						break;
						case 0xc:
							// Note Cut
							voice.effect_ = 0xec;
							if (effectOperand2 != 0)
								voice.noteCutDelay_ = effectOperand2;
							else
								voice.bNoteCutted_ = true;
						break;
						case 0xd:
							// Note Delay
							voice.tickBeforeSample_ = effectOperand2 + 1;
						break;
						case 0xe:
							// Pattern Delay
							if (patternDelay_ < 0)
								patternDelay_ = effectOperand2;
						break;
					}
				break;

				case 0xf:
					// SET SPEED or BPM
					if (effectOperand < 32)
						newSpeed = effectOperand;
					else
						newBPM = effectOperand;
				break;

				default:
				// UNKNOWN EFFECT
				break;
			}

			if ((voice.effect_ == 0x4 || voice.effect_ == 0x6) && voice.bGotVibrato_ == false) {
				voice.periodBAK_ = voice.period_;
				voice.bGotVibrato_ = true;
			}
			if (voice.effect_ == 0x7 && voice.bGotTremolo_ == false) {
				voice.volumeBAK_ = voice.volume_;
				voice.bGotTremolo_ = true;
			}
		}

		if (newSpeed != 0)
			speed_ = newSpeed;
		if (newBPM != 0)
			BPM_ = newBPM;

		posInPattern_++;

		if (posInPattern_ == 64 || bGotPatternJump == true || bGotPatternBreak == true) {

			posInPattern_ = whereToBreak;
			if (bGotPatternJump == true)
				posChanson_ = whereToJump;
			else {
				posChanson_++;
				if (posChanson_ == listLen_) {
					if (loopable_ == false)
						running_ = false;
					else
						posChanson_ = 0;
				}
			}

			posInPattern_ = 0;
		}
	}

	void mixSample(int _time) {
		final int nbsamp = freq_ / (1000 / _time);
		Arrays.fill(pcm_, (byte) 0);
		chans_.mix(nbsamp, pcm_);
		ByteBuffer.wrap(pcm_).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(pcms_, 0, nbsamp * 2);
		gdxAudio.writeSamples(pcms_, 0, nbsamp * 2);
	}

	private float masterVolume = 1f;
	private float prevMasterVolume = 1f;
	private Runnable callback;

	public Runnable getCallback() {
		return callback;
	}

	public void setCallback(Runnable callback) {
		this.callback = callback;
	}

	public int getMasterVolume() {
		return (int) (masterVolume * 100f);
	}

	public void setMasterVolume(float masterVolume) {
		if (masterVolume < 0) {
			masterVolume = 0;
		}
		if (masterVolume > 1) {
			masterVolume = 1;
		}
		this.masterVolume = masterVolume;
	}

	private long totalTime = 0l;

	public void run() {
		gdxAudio = Gdx.audio.newAudioDevice(freq_, false);
		gdxAudio.setVolume(masterVolume);

		int soundBufferLen = freq_ * 4;
		pcm_ = new byte[soundBufferLen];
		pcms_ = new short[pcm_.length / 2];

		long cumulTime = 0;

		while (running_) {
			if (prevMasterVolume != masterVolume) {
				prevMasterVolume = masterVolume;
				gdxAudio.setVolume(masterVolume);
			}
			long since = timer_.getDelta();
			if (paused) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
				}
				continue;
			}
			totalTime += since;
			// max ten minutes for any one song
			if (totalTime > maxPlayTime && !loopable_) {
				running_ = false;
			}
			float timerRate = 1000.0f / (BPM_ * 0.4f);
			int intTimerRate = (int) Math.floor(timerRate);

			cumulTime += since;

			while (cumulTime >= intTimerRate) {
				cumulTime -= intTimerRate;
				oneShot(intTimerRate);
			}
			try {
				Thread.sleep(intTimerRate - cumulTime);
			} catch (InterruptedException e) {
			}
		}
		done();
		done_ = true;
	}

	void oneShot(int _timer) {
		if (tick_ == speed_)
			tick_ = 0;
		tick_++;

		if (tick_ == 1) {
			patternDelay_--;
			if (patternDelay_ < 0)
				dispatchNotes();
		} else {
			for (int i = 0; i < nbVoices_; i++)
				voices_[i].updateSoundWithEffect();
		}

		for (int i = 0; i < nbVoices_; i++)
			voices_[i].soundUpdate();

		mixSample(_timer);
	}

	Thread player;

	public void play() {
		if (isAlive() == true || done_ == true)
			return;

		timer_ = new Timer();
		tick_ = 0;
		patternDelay_ = -1;

		panPower_ = 0.5f;
		speed_ = 6;
		BPM_ = 125;
		MODvolume_ = 0.1f;
		posChanson_ = 0;
		running_ = true;

		paused = false;

		start();
	}

	public void done() {
		if (callback != null) {
			new Thread(callback).start();
		}
		gdxAudio.dispose();
		die();
	}

	private void die() {
		done_ = true;
		running_ = false;
		this.interrupt();
		try {
			join();
		} catch (InterruptedException e) {
		}
	}

	int getPeriod(int _note, int _fine) {
		_note += 23;
		int n = _note % 12;
		int o = _note / 12;

		int h = (8363 * 16 * period_[n]) >> o;

		if (_fine == 0)
			return 1;
		else
			return h / _fine;
	}

	public int getCurrentPos() {
		return posInPattern_;
	}

	public int getMaxPlayTime() {
		return (int) (maxPlayTime / 1000l);
	}

	public void setMaxPlayTime(int seconds) {
		this.maxPlayTime = seconds * 1000l;
	}

	public byte[] getMixBuffer() {
		return pcm_;
	}

	public boolean isLoopable() {
		return loopable_;
	}

	public void setLoopable(boolean _b) {
		loopable_ = _b;
	}

	public void pause(boolean b) {
		this.paused = b;
	}

	@Override
	public void setFrequency(int frequency) {
		this.freq_ = frequency;
	}
}
