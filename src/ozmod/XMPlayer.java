package ozmod;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;

public class XMPlayer extends Thread implements ChipPlayer {

	private AudioDevice gdxAudio;

	static final int MAXNBVOICES = 64;
	static final int MAXNBSONGPAT = 256;
	static final int MAXNBPATTERNS = 256;

	static final int COLUMN_EFFECT_NONE = 0x0fff;
	static final int COLUMN_EFFECT_VOLUMESLIDE = 0x01;
	static final int COLUMN_EFFECT_PANSLIDE = 0x02;
	static final int COLUMN_EFFECT_PORTAMENTO_TO = 0x03;

	static final int MIN_PERIOD = 40;
	static final int MAX_PERIOD = 11520;

	int tick_;
	int patternDelay_ = -1;

	boolean isRunningPlay;
	boolean isDone;

	int posChanson_ = 0;
	int posInPattern_;

	Timer timer_;
	int frequency = 44100;
	byte[] pcm_;
	// SourceDataLine line_;
	ChannelsList chans_ = new ChannelsList();

	boolean loopable_ = false;

	int version_;
	int sizHeaderInfo_;
	int listLen_;
	int posRestart_;
	int nbVoices_;
	byte songName_[] = new byte[20];
	byte trackerName_[] = new byte[20];

	int nbPatterns_;
	int nbInstrus_;
	int freqFlag_;
	int speed_ = 6;
	int BPM = 125;
	int listPatterns_[] = new int[256];

	Voice voices_[];
	Pattern patterns_[];
	Instru instrus_[];

	int globalVolume = 64;
	float mainVolume = 1.0f;

	boolean bGotPatternLoop_;
	int patternPosLoop_;
	int patternLoopLeft_;

	class NoteInfo {
		int note;
		int iInstru = -1;
		int effect;
		int effectOperand;
		int colum;
	}

	class Column {
		NoteInfo notesInfo[];
	}

	class Pattern {
		int nbLines;
		Column columns[];
	}

	class Sample {

		byte name[] = new byte[22];
		int len;
		int fineTune;
		int volume;
		int panning;
		byte relativeNote;
		int startLoop;
		int lengthLoop;
		int offStop;
		int type;

		AudioData audioData = new AudioData();
	};

	class Instru {

		byte name[] = new byte[22];
		int type;
		int nbSamples;
		int sampleTable[] = new int[96];
		int envVol[] = new int[24];
		int envPan[] = new int[24];
		int nbPointsVol;
		int nbPointsPan;
		int sustainPointVol;
		int startVolLoop;
		int endVolLoop;
		int sustainPointPan;
		int startPanLoop;
		int endPanLoop;
		int volType;
		int panType;
		int vibratoType;
		int vibratoSweep;
		int vibratoProf;
		int vibratoSpeed;
		int fadeOut;
		byte reserved[] = new byte[22];

		Sample samples[];
	};

	class Voice {

		int iVoice_;

		int period_, dstPeriod_, periodBAK_;
		int arpeggioCount_, arp1_, arp2_;
		Sample listSamples_;
		int note_, effect_, effectOperand_, effectOperand2_, oldEffect_ = -1;
		int column_Effect_, column_EffectOperand_;
		Sample samplePlaying_, sampleToPlay_;
		Instru actuInstru_;
		int actuNumInstru_ = -1;
		boolean bNeedToBePlayed_;

		int volumeSlidingSpeed_;
		int column_VolumeSlidingSpeed_;

		int portamentoSpeed_;
		int column_PortamentoSpeed_;
		boolean bGotArpeggio_;

		boolean bGotVibrato_;
		int vibratoCounter_, vibratoCounter_s_;
		int vibratoForm_;
		int vibratoSpeed_, vibratoSpeed_s_, vibratoProf_, vibratoProf_s_;

		boolean bGotTremolo_;
		int tremoloCounter_;
		int tremoloForm_;
		int tremoloSpeed_, tremoloProf_;

		int portaSpeed_;

		int fineSlideSpeed_;

		int column_PanSlidingSpeed_;

		boolean bNoteCutted_;
		int noteCut_;

		int fineTune_;
		int volume_, volumeBAK_;
		int envelopeVolume_;
		int fadeOutVolume_;
		int panning_;
		int envelopePanning_;

		int tickBeforeSample_;
		int samplePosJump_;

		boolean bGotRetrigNote_;
		int tickForRetrigNote_ = -1;

		boolean bKeyOFF_;
		float volEnvActuPos_;
		int volEnvActuPoint_;
		float panEnvActuPos_;
		int panEnvActuPoint_;

		int fineVolumeUp_, fineVolumeDown_;
		int column_FineVolumeUp_, column_FineVolumeDown_;
		int globalVolSlide_;

		Channel sndchan_;

		Voice() {
			sndchan_ = new Channel();
		}

		void soundUpdate() {
			// int i;
			// byte data[];
			// Sample sample;
			int freq;
			float vol, pan;

			if (samplePlaying_ == null)
				return;

			if (tick_ < tickBeforeSample_)
				return;

			// Clamp the Period voice
			if (period_ < MIN_PERIOD)
				period_ = MIN_PERIOD;
			else if (period_ > MAX_PERIOD)
				period_ = MAX_PERIOD;

			if (freqFlag_ != 0)
				freq = getFreq(period_);
			else
				freq = (int) (((long) (3579546 << 2)) / period_);

			// Sleep(100);

			// Clamp the volume_ voice
			if (volume_ < 0)
				volume_ = 0;
			else if (volume_ > 64)
				volume_ = 64;

			// Clamp the panning_ voice
			if (panning_ < 0)
				panning_ = 0;
			else if (panning_ > 255)
				panning_ = 255;

			if (bNoteCutted_ == false) {
				vol = (fadeOutVolume_ / 65536.f) * (volume_ / 64.f) * (envelopeVolume_ / 64.f) * (globalVolume / 64.f)
						* mainVolume;
				if (vol < 0)
					vol = 0;
				else if (vol > 1)
					vol = 1;
			} else
				vol = 0;

			pan = (float) (panning_ + (envelopePanning_ - 127) * (128 - Math.abs(panning_ - 128)) / 128);
			pan = (pan - 128) / 128.f;
			if (pan < -1)
				pan = -1;
			else if (pan > 1)
				pan = 1;

			if (bGotRetrigNote_ == true) {
				if (((tick_ - 1) % tickForRetrigNote_) == 0)
					bNeedToBePlayed_ = true;
			}

			if (bNeedToBePlayed_ == false) {
				sndchan_.setPan(pan);

				sndchan_.frequency = freq;
				sndchan_.step = freq / (float) frequency;
				sndchan_.vol = vol;

				return;
			}

			int startPos;
			if (effect_ == 0x9) {
				effect_ = 0xfff;
				startPos = samplePosJump_;
			} else
				startPos = 0;

			chans_.removeChannel(sndchan_);

			if (tick_ >= tickBeforeSample_) {

				sndchan_.audio = samplePlaying_.audioData;
				sndchan_.frequency = freq;
				sndchan_.step = freq / (float) frequency;
				sndchan_.vol = vol;
				sndchan_.setPan(pan);
				sndchan_.pos = startPos;
				chans_.addChannel(sndchan_);

				bNeedToBePlayed_ = false;
				tickBeforeSample_ = 0;
			}

		}

		void vibrato(boolean bSample, int form) {
			int vibSeek;
			int periodOffset;

			if (bSample == false)
				vibSeek = (vibratoCounter_ >> 2) & 0x3f;
			else
				vibSeek = (vibratoCounter_s_ >> 2) & 0x3f;

			switch (form) {
				case 0:
				default:
					periodOffset = TrackerConstant.vibratoTable[vibSeek];
				break;
				case 1:
					periodOffset = TrackerConstant.squareTable[vibSeek];
				break;
				case 2:
					periodOffset = (((vibSeek + 32) % 63) - 32) << 3;
				break;
				case 3:
					periodOffset = (-((vibSeek + 32) % 63) + 32) << 3;
				break;
			}

			if (bSample == false) {
				periodOffset *= vibratoProf_;
				periodOffset >>= 7;
				periodOffset <<= 2;
			} else {
				periodOffset *= vibratoProf_s_ * actuInstru_.vibratoProf;
				periodOffset >>= (14 + 8);
			}

			if (bSample == true && (bGotVibrato_ == true || bGotArpeggio_ == true))
				period_ += periodOffset;
			else
				period_ = periodBAK_ + periodOffset;

			if (bSample == false)
				vibratoCounter_ += vibratoSpeed_;
			else
				vibratoCounter_s_ += vibratoSpeed_s_;
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

		void portamentoTo(int speed) {
			if (period_ < dstPeriod_) {
				period_ += speed;
				if (period_ > dstPeriod_)
					period_ = dstPeriod_;
			} else if (period_ > dstPeriod_) {
				period_ -= speed;
				if (period_ < dstPeriod_)
					period_ = dstPeriod_;
			}

			periodBAK_ = period_;
		}

		void volumeSliding(int vol) {
			volume_ += vol;
			volumeBAK_ = volume_;
		}

		void panSliding(int pan) {
			panning_ += pan;
		}

		void seekEnvelope(int pos) {
			int i;

			if (actuInstru_ == null)
				return;

			// Reseek volume
			if ((actuInstru_.volType & 1) != 0) {
				int nbPoints = actuInstru_.nbPointsVol;
				if (pos >= actuInstru_.envVol[(nbPoints - 1) * 2]) {
					volEnvActuPoint_ = nbPoints - 2;
					volEnvActuPos_ = (float) actuInstru_.envVol[(nbPoints - 1) * 2]
							- actuInstru_.envVol[(nbPoints - 2) * 2];
					return;
				}

				i = 0;
				while (actuInstru_.envVol[i * 2] < pos)
					i++;
				volEnvActuPoint_ = i - 1;
				volEnvActuPos_ = (float) (pos - actuInstru_.envVol[(i - 1) * 2]);
			}

			// then Reseek panning
			if ((actuInstru_.panType & 1) != 0) {
				int nbPoints = actuInstru_.nbPointsPan;
				if (pos >= actuInstru_.envPan[(nbPoints - 1) * 2]) {
					panEnvActuPoint_ = nbPoints - 2;
					panEnvActuPos_ = (float) actuInstru_.envPan[(nbPoints - 1) * 2]
							- actuInstru_.envPan[(nbPoints - 2) * 2];
					return;
				}

				i = 0;
				while (actuInstru_.envPan[(++i) * 2] < pos)
					;
				panEnvActuPoint_ = i - 1;
				panEnvActuPos_ = (float) (pos - actuInstru_.envPan[(i - 1) * 2]);
			}
		}

		void volEnvelope() {
			// int x0;
			int y0, x1, y1, actuY;
			int envVol[];

			if (actuInstru_ == null)
				return;

			if ((actuInstru_.volType & 1) == 0) {
				if (bKeyOFF_ == true)
					fadeOutVolume_ = 0;
				return;
			}

			if (tick_ < tickBeforeSample_)
				return;

			boolean bAuthorize = true;

			envVol = actuInstru_.envVol; // [volEnvActuPoint_];

			if (volEnvActuPoint_ == actuInstru_.nbPointsVol - 1) {
				x1 = 1;
				y0 = y1 = envVol[1];
				bAuthorize = false;
			} else {
				x1 = envVol[volEnvActuPoint_ * 2 + 2] - envVol[volEnvActuPoint_ * 2];
				y0 = envVol[volEnvActuPoint_ * 2 + 1];
				y1 = envVol[volEnvActuPoint_ * 2 + 3];
			}

			if (x1 == 0) {
				x1 = 1;
				y1 = y0;
			}

			actuY = (int) ((float) ((y1 - y0) * volEnvActuPos_) / x1 + y0);

			if ((actuInstru_.volType & 2) != 0) {
				// Sustain point
				if (volEnvActuPoint_ == actuInstru_.sustainPointVol && bKeyOFF_ == false)
					bAuthorize = false;
			}
			if (bAuthorize == true)
				volEnvActuPos_++;

			if (volEnvActuPos_ >= x1) {
				if ((actuInstru_.volType & 4) != 0) {
					// Envelope Loop
					if (volEnvActuPoint_ + 1 == actuInstru_.endVolLoop)
						volEnvActuPoint_ = actuInstru_.startVolLoop;
					else
						volEnvActuPoint_++;
					volEnvActuPos_ = 0;
				} else {
					if (volEnvActuPoint_ == actuInstru_.nbPointsVol - 2)
						volEnvActuPos_ = x1;
					else {
						volEnvActuPoint_++;
						volEnvActuPos_ = 0;
					}
				}
			}
			envelopeVolume_ = actuY;

			if (bKeyOFF_ == true) {
				fadeOutVolume_ -= actuInstru_.fadeOut * 2;
				if (fadeOutVolume_ < 0)
					fadeOutVolume_ = 0;
			}
		}

		void panEnvelope() {
			// int x0;
			int y0, x1, y1, actuY;
			int envPan[];

			if (actuInstru_ == null)
				return;

			if ((actuInstru_.panType & 1) == 0)
				return;

			if (tick_ < tickBeforeSample_)
				return;

			envPan = actuInstru_.envPan; // [panEnvActuPoint_];

			boolean bAuthorize = true;

			if (panEnvActuPoint_ == actuInstru_.nbPointsPan - 1) {
				x1 = 1;
				y0 = y1 = envPan[panEnvActuPoint_ * 2 + 1];
			} else {
				x1 = envPan[panEnvActuPoint_ * 2 + 2] - envPan[panEnvActuPoint_ * 2];
				y0 = envPan[panEnvActuPoint_ * 2 + 1];
				y1 = envPan[panEnvActuPoint_ * 2 + 3];
			}

			if (x1 == 0) {
				x1 = 1;
				y1 = y0;
			}

			actuY = (int) ((float) ((y1 - y0) * panEnvActuPos_) / x1 + y0);

			if ((actuInstru_.panType & 2) != 0) {
				// Sustain point
				if (panEnvActuPoint_ == actuInstru_.sustainPointPan && bKeyOFF_ == false)
					bAuthorize = false;
			}
			if (bAuthorize == true)
				panEnvActuPos_++;

			if (panEnvActuPos_ >= x1) {
				if ((actuInstru_.panType & 4) != 0) {
					// Envelope Loop
					if (panEnvActuPoint_ + 1 == actuInstru_.endPanLoop) {
						panEnvActuPoint_ = actuInstru_.startPanLoop;
					} else
						panEnvActuPoint_++;
					panEnvActuPos_ = 0;
				} else {
					if (panEnvActuPoint_ == actuInstru_.nbPointsPan - 2)
						panEnvActuPos_ = x1;
					else {
						panEnvActuPoint_++;
						panEnvActuPos_ = 0;
					}
				}
			}
			envelopePanning_ = actuY * 4;
		}

		void updateSoundWithEnvelope() {
			if (actuInstru_ == null)
				return;

			if (actuInstru_.vibratoProf != 0 && actuInstru_.vibratoSpeed != 0) {
				if (actuInstru_.vibratoSweep != 0)
					vibratoProf_s_ += (64 << 8) / actuInstru_.vibratoSweep;
				else
					vibratoProf_s_ += 64 << 8;
				if (vibratoProf_s_ > 256 * 64)
					vibratoProf_s_ = 256 * 64;
				vibratoSpeed_s_ = actuInstru_.vibratoSpeed;
				vibrato(true, actuInstru_.vibratoType);
			}

			volEnvelope();
			panEnvelope();
		}

		void updateSoundWithEffect() {
			switch (column_Effect_) {

				case COLUMN_EFFECT_VOLUMESLIDE:
					volumeSliding(column_VolumeSlidingSpeed_);
				break;

				case COLUMN_EFFECT_PANSLIDE:
					panSliding(column_PanSlidingSpeed_);
				break;

				case COLUMN_EFFECT_PORTAMENTO_TO:
					portamentoTo(column_PortamentoSpeed_);
				break;
			}

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
					period_ -= portaSpeed_;
					periodBAK_ = period_;
				break;

				case 0x2:
					// PORTAMENTO DOWN
					period_ += portaSpeed_;
					periodBAK_ = period_;
				break;

				case 0x3:
					// PORTAMENTO TO
					portamentoTo(portamentoSpeed_);
				break;

				case 0x4:
					// VIBRATO
					vibrato(false, vibratoForm_);
				break;

				case 0x5:
					// PORTAMENTO TO + VOLUME SLIDING
					portamentoTo(portamentoSpeed_);
					volumeSliding(volumeSlidingSpeed_);
				break;

				case 0x6:
					// VIBRATO + VOLUME SLIDING
					vibrato(false, vibratoForm_);
					volumeSliding(volumeSlidingSpeed_);
				break;

				case 0x7:
					// TREMOLO
					tremolo();
				break;

				case 0xa:
					// VOLUME SLIDING
					volumeSliding(volumeSlidingSpeed_);
				break;

				case 0xec:
					if (tick_ - 1 == noteCut_)
						bNoteCutted_ = true;
				break;

				default:
				// STILL NOTHING ;)
				break;
			}
		}

	}

	// private AudioDevice audio;
	private short[] pcms_;

	XMPlayer() {
	}

	protected void finalize() {
		isRunningPlay = false;
	}

	public OZMod.ERR load(LoaderFromMemory _input) {
		byte tmp[] = new byte[20];
		int lseek;

		_input.read(tmp, 0, 17);
		String ID = new String(tmp).substring(0, 17);
		if (ID.compareTo("Extended Module: ") != 0)
			return OZMod.proceedError(OZMod.ERR.BADFORMAT);

		_input.readFully(songName_);
		_input.seek(38);
		_input.read(trackerName_, 0, 20);
		version_ = _input.readUShort();
		lseek = _input.tell();

		sizHeaderInfo_ = _input.readInt();
		listLen_ = _input.readUShort();
		posRestart_ = _input.readUShort();
		nbVoices_ = _input.readUShort();
		if (nbVoices_ > 64)
			return OZMod.proceedError(OZMod.ERR.BADFORMAT);

		nbPatterns_ = _input.readUShort();
		nbInstrus_ = _input.readUShort();
		freqFlag_ = _input.readUShort();
		speed_ = _input.readUShort();
		BPM = _input.readUShort();

		if (OZ_DEBUG) {
			System.out.println("version: " + version_ + " sizHeaderInfo: " + sizHeaderInfo_ + " listLen: " + listLen_
					+ " nbVoices: " + nbVoices_ + " nbPatterns: " + nbPatterns_ + " nbInstrus: " + nbInstrus_
					+ " freqFlag: " + freqFlag_ + " speed: " + speed_ + " BPM: " + BPM);
		}

		// don't trust on nbPatterns_, always read 256 bytes!
		// for (int i = 0; i < 256; i++)
		for (int i = 0; i < nbPatterns_; i++)
			listPatterns_[i] = _input.readUByte();

		voices_ = new Voice[nbVoices_];
		for (int i = 0; i < nbVoices_; i++) {
			Voice voice = new Voice();
			voices_[i] = voice;
			voice.iVoice_ = i;
		}

		_input.seek(lseek + sizHeaderInfo_);

		// Patterns
		patterns_ = new Pattern[nbPatterns_];
		for (int i = 0; i < nbPatterns_; i++) {
			Pattern pat = new Pattern();
			patterns_[i] = pat;

			int headerSize;
			int patternCompression;
			int comp;

			headerSize = _input.readInt();
			patternCompression = _input.readUByte();
			pat.nbLines = _input.readUShort();
			comp = _input.readUShort();

			pat.columns = new Column[nbVoices_];
			for (int j = 0; j < nbVoices_; j++) {
				Column column = new Column();
				pat.columns[j] = column;
				column.notesInfo = new NoteInfo[pat.nbLines];
				for (int k = 0; k < pat.nbLines; k++)
					column.notesInfo[k] = new NoteInfo();
			}

			if (comp == 0)
				continue;

			for (int j = 0; j < pat.nbLines; j++) {
				for (int k = 0; k < nbVoices_; k++) {
					int dat;
					dat = _input.readUByte();

					NoteInfo noteInfo = pat.columns[k].notesInfo[j];

					if ((dat & 0x80) != 0) {
						// packed info
						if ((dat & 1) != 0)
							noteInfo.note = _input.readUByte();

						if ((dat & 2) != 0) {
							noteInfo.iInstru = _input.readUByte();
							noteInfo.iInstru--;
						}

						if ((dat & 4) != 0)
							noteInfo.colum = _input.readUByte();

						if ((dat & 8) != 0)
							noteInfo.effect = _input.readUByte();

						if ((dat & 16) != 0)
							noteInfo.effectOperand = _input.readUByte();
					} else {
						noteInfo.note = dat;
						noteInfo.iInstru = _input.readUByte();
						noteInfo.iInstru--;
						noteInfo.colum = _input.readUByte();
						noteInfo.effect = _input.readUByte();
						noteInfo.effectOperand = _input.readUByte();
					}
				}
			}
		}

		// Check for unexisting pattern
		for (int i = 0; i < listLen_; i++) {
			int iPat = listPatterns_[i];
			if (patterns_[iPat] == null) {
				Pattern pat = new Pattern();
				patterns_[i] = pat;
				pat.nbLines = 64;
				pat.columns = new Column[nbVoices_];
				for (int j = 0; j < nbVoices_; j++) {
					Column column = new Column();
					pat.columns[j] = column;
					column.notesInfo = new NoteInfo[pat.nbLines];
					for (int k = 0; k < pat.nbLines; k++)
						column.notesInfo[k] = new NoteInfo();
				}
			}
		}

		// Instruments
		instrus_ = new Instru[nbInstrus_];
		for (int i = 0; i < nbInstrus_; i++) {
			Instru instru = new Instru();
			instrus_[i] = instru;

			int headerSize;
			int extra_size;
			headerSize = _input.readInt();
			lseek = _input.tell();
			_input.read(instru.name, 0, 22);
			instru.type = _input.readUByte();
			instru.nbSamples = _input.readUShort();

			if ((instru.nbSamples == 0) || (instru.nbSamples > 255)) {
				_input.forward(headerSize - 29);
				continue;
			}

			extra_size = _input.readInt();
			for (int j = 0; j < 96; j++)
				instru.sampleTable[j] = _input.readUByte();

			for (int j = 0; j < 24; j++)
				instru.envVol[j] = _input.readUShort();

			for (int j = 0; j < 24; j++)
				instru.envPan[j] = _input.readUShort();

			instru.nbPointsVol = _input.readUByte();
			instru.nbPointsPan = _input.readUByte();
			instru.sustainPointVol = _input.readUByte();
			instru.startVolLoop = _input.readUByte();
			instru.endVolLoop = _input.readUByte();
			instru.sustainPointPan = _input.readUByte();
			instru.startPanLoop = _input.readUByte();
			instru.endPanLoop = _input.readUByte();
			instru.volType = _input.readUByte();
			instru.panType = _input.readUByte();
			instru.vibratoType = _input.readUByte();
			instru.vibratoSweep = _input.readUByte();
			instru.vibratoProf = _input.readUByte();
			instru.vibratoSpeed = _input.readUByte();
			instru.fadeOut = _input.readUShort();
			_input.read(instru.reserved, 0, 11 * 2);

			// Inside the instruments, dispatch samples info
			instru.samples = new Sample[instru.nbSamples];
			for (int j = 0; j < instru.nbSamples; j++) {
				Sample sample = new Sample();
				instru.samples[j] = sample;

				sample.len = _input.readInt();
				sample.startLoop = _input.readInt();
				sample.lengthLoop = _input.readInt();
				sample.volume = _input.readUByte();
				sample.fineTune = _input.readByte();
				sample.type = _input.readUByte();
				sample.panning = _input.readUByte();
				sample.relativeNote = _input.readByte();
				_input.forward(1);
				_input.read(sample.name, 0, 22);
				_input.forward(extra_size - 40);
			}

			// and in a second pass _input.read the audio data (XM format really
			// sucks!!)
			for (int j = 0; j < instru.nbSamples; j++) {
				Sample sample = instru.samples[j];
				if (sample.len == 0)
					continue;

				byte dat[] = new byte[sample.len];
				_input.read(dat, 0, sample.len);

				// samples are stored as delta value, recompose them as absolute
				// integer
				int nbBits = 0;
				if ((sample.type & 16) != 0) {
					// 16 bits
					nbBits = 16;
					int old = 0;
					for (int ii = 2; ii < dat.length; ii += 2) {
						int b1 = (int) (dat[ii] & 0xff);
						int b2 = (int) (dat[ii + 1]);
						int n = b1 | (b2 << 8);

						n += old;
						dat[ii] = (byte) (n & 0xff);
						dat[ii + 1] = (byte) ((n >> 8) & 0xff);

						old = n;
					}
				} else {
					// 8 bits
					nbBits = 8;
					int old = 0;
					for (int ii = 0; ii < dat.length; ii++) {
						int n = dat[ii] + old;
						dat[ii] = (byte) n;
						old = n;
					}
				}

				if ((sample.type & 3) == 1) {
					// forward loop
					sample.audioData
							.make(dat, nbBits, 1, (sample.startLoop) >> (nbBits / 16), (sample.startLoop + sample.lengthLoop) >> (nbBits / 16), AudioData.LOOP_FORWARD);
				} else if (((sample.type & 3) == 2) || ((sample.type & 3) == 3)) {
					// pingpong loop
					sample.audioData
							.make(dat, nbBits, 1, (sample.startLoop) >> (nbBits / 16), (sample.startLoop + sample.lengthLoop) >> (nbBits / 16), AudioData.LOOP_PINGPONG);
				} else
					sample.audioData.make(dat, nbBits, 1);
			}
		} // Next instrument

		return OZMod.proceedError(OZMod.ERR.NOERR);
	}

	public void play() {
		if (isAlive() == true || isDone == true)
			return;

		timer_ = new Timer();
		tick_ = 0;
		patternDelay_ = -1;

		isRunningPlay = true;

		start();
	}

	public void done() {
		isRunningPlay = false;
		try {
			join();
		} catch (InterruptedException e) {
		}
		gdxAudio.dispose();
		gdxAudio = null;
	}

	public void run() {

		gdxAudio = Gdx.audio.newAudioDevice(frequency, false);
		gdxAudio.setVolume(1f);

		int soundBufferLen = frequency * 4;
		pcm_ = new byte[soundBufferLen];
		pcms_ = new short[pcm_.length / 2];

		if (OZ_DEBUG) {
			System.out.println("Run frequency: " + frequency + " soundBufferLen: " + soundBufferLen);
		}

		long cumulTime = 0;
		timer_.reset();

		while (isRunningPlay) {
			float timerRate = 1000.0f / (BPM * 0.4f);
			int intTimerRate = (int) Math.floor(timerRate);

			long since = timer_.getDelta();
			cumulTime += since;

			if (cumulTime >= intTimerRate) {
				cumulTime -= intTimerRate;
				oneShot(intTimerRate);
			}
			Thread.yield();
		}
		isDone = true;
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

		for (int i = 0; i < nbVoices_; i++) {
			voices_[i].updateSoundWithEnvelope();
		}

		for (int i = 0; i < nbVoices_; i++) {
			voices_[i].soundUpdate();
		}

		mixSample(_timer);
	}

	void mixSample(int _time) {
		int nbsamp = frequency / (1000 / _time);
		Arrays.fill(pcm_, (byte) 0);
		chans_.mix(nbsamp, pcm_);
		ByteBuffer.wrap(pcm_).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(pcms_, 0, nbsamp * 2);
		gdxAudio.writeSamples(pcms_, 0, nbsamp * 2);
	}

	int getFreq(int period) {
		int okt;
		int frequency;

		period = 11520 - period;
		okt = period / 768 - 3;
		frequency = TrackerConstant.lintab[period % 768];

		if (okt < 8)
			frequency = frequency >> (7 - okt);
		else {
			frequency = frequency << (okt - 7);
		}

		return frequency;
	}

	int interpolate(int p, int p1, int p2, int v1, int v2) {
		int dp, dv, di;

		if (p1 == p2)
			return v1;

		dv = v2 - v1;
		dp = p2 - p1;
		di = p - p1;

		return v1 + ((int) (di * dv) / dp);
	}

	int getLinearPeriod(int note, int fine) {
		return ((10 * 12 * 16 * 4) - ((int) note * 16 * 4) - (fine / 2) + 64);
	}

	int getLogPeriod(int note, int fine) {
		int n, o;
		int p1, p2, i;

		// fine+=128;
		n = note % 12;
		o = note / 12;
		i = (n << 3) + (fine >> 4); // n*8 + fine/16
		if (i < 0)
			i = 0;

		p1 = TrackerConstant.logtab[i];
		p2 = TrackerConstant.logtab[i + 1];

		return (interpolate(fine / 16, 0, 15, p1, p2) >> o);
	}

	int getPeriod(int note, int fine) {
		int period;
		if (freqFlag_ != 0)
			period = getLinearPeriod(note, fine);
		else
			period = getLogPeriod(note, fine);
		return period;
	}

	void dispatchNotes() {
		int iInstru;
		// int iSample;
		// int frequency;
		int note, effect, effectOperand_, colum, effectOperand2_;
		// int posJump;
		int iVoice;

		int actuPattern = listPatterns_[posChanson_];
		if (actuPattern >= nbPatterns_)
			actuPattern = 0;
		int actuPos = posInPattern_;

		int newSpeed = 0, newBPM = 0;
		// int nbLines = patterns_[actuPattern].nbLines;

		boolean bGotPatternJump = false;
		boolean bGotPatternBreak = false;
		int whereToJump = 0;
		int whereToBreak = 0;

		for (iVoice = 0; iVoice < nbVoices_; iVoice++) {

			// if (iVoice != 4)
			// continue;

			Voice voice = voices_[iVoice];
			Column actuColumn = patterns_[actuPattern].columns[iVoice];

			note = actuColumn.notesInfo[actuPos].note;
			effect = actuColumn.notesInfo[actuPos].effect;
			effectOperand_ = actuColumn.notesInfo[actuPos].effectOperand;
			colum = actuColumn.notesInfo[actuPos].colum;
			iInstru = actuColumn.notesInfo[actuPos].iInstru;

			// Reset GotVibrato if Vibrato no more used
			if (((effect != 0x4) && (effect != 0x6)) && (voice.bGotVibrato_ == true)) {
				voice.period_ = voice.periodBAK_;
				voice.bGotVibrato_ = false;
			}
			// Reset GotTremolo if Tremolo no more used
			if ((effect != 0x7) && (voice.bGotTremolo_ == true)) {
				voice.volume_ = voice.volumeBAK_;
				voice.bGotTremolo_ = false;
			}
			// For safety, restore Period after Arpeggio
			if (voice.bGotArpeggio_ == true)
				voice.period_ = voice.periodBAK_;

			boolean bAllowToUpdateNote = true;

			if (voice.samplePlaying_ != null) {
				if ((effect == 0x03) || (effect == 0x05) || ((colum >= 0xf0) && (colum <= 0xff)))
					bAllowToUpdateNote = false;
			}

			if (iInstru >= 0) {
				// if (iInstru > nbInstrus_)
				// voice.actuInstru_ = NULLInstru_;
				// else
				voice.actuInstru_ = instrus_[iInstru];

				if (voice.samplePlaying_ != null) {
					voice.volume_ = voice.samplePlaying_.volume;
					voice.volumeBAK_ = voice.volume_;
					voice.panning_ = voice.samplePlaying_.panning;
					voice.fineTune_ = voice.samplePlaying_.fineTune;
					voice.vibratoProf_s_ = 0;
				}

				voice.envelopeVolume_ = 64;
				voice.fadeOutVolume_ = 65536;
				voice.envelopePanning_ = 127;
				voice.volEnvActuPos_ = 0;
				voice.volEnvActuPoint_ = 0;
				voice.panEnvActuPos_ = 0;
				voice.panEnvActuPoint_ = 0;
				voice.bKeyOFF_ = false;
			}

			if (note > 0 && note < 97 && voice.actuInstru_ != null) {
				Sample sample;
				int sampleToPick;
				sampleToPick = voice.actuInstru_.sampleTable[note - 1];

				// if (sampleToPick >= voice.actuInstru_.nbSamples)
				// sample = NULLSample_;
				// else
				sample = voice.actuInstru_.samples[sampleToPick];

				voice.note_ = note + sample.relativeNote;
				voice.tremoloCounter_ = 0;
				voice.vibratoCounter_ = 0;
				voice.vibratoCounter_s_ = 0;

				if (iInstru >= 0) {
					voice.volume_ = sample.volume;
					voice.volumeBAK_ = voice.volume_;
					voice.panning_ = sample.panning;
					voice.fineTune_ = sample.fineTune;
					voice.vibratoProf_s_ = 0;
				}

				if (bAllowToUpdateNote == true) {
					int period;
					int rel = note + sample.relativeNote;
					if (rel < 0)
						rel = 0;
					period = getPeriod(rel, voice.fineTune_);

					voice.samplePlaying_ = sample;
					voice.period_ = period;
					voice.periodBAK_ = voice.period_;
					voice.bNoteCutted_ = false;

					voice.dstPeriod_ = period;
					voice.bNeedToBePlayed_ = true;
				}
			} else if (note == 97) {
				voice.bKeyOFF_ = true;
			}

			voice.bGotArpeggio_ = false;
			voice.bGotRetrigNote_ = false;
			voice.effect_ = 0x0fff;
			voice.column_Effect_ = COLUMN_EFFECT_NONE;

			// volume_ Column
			if (colum >= 0x10 && colum < 0x60) {
				voice.volume_ = colum - 0x10;
				voice.volumeBAK_ = voice.volume_;
			} else if (colum >= 0x60 && colum < 0x70) {
				// volume_ Sliding down
				colum -= 0x60;
				voice.column_Effect_ = COLUMN_EFFECT_VOLUMESLIDE;
				if (colum != 0)
					voice.column_VolumeSlidingSpeed_ = -colum;
			} else if (colum >= 0x70 && colum < 0x80) {
				// volume_ Sliding up
				colum -= 0x70;
				voice.column_Effect_ = COLUMN_EFFECT_VOLUMESLIDE;
				voice.effect_ = 0xa;
				if (colum != 0)
					voice.column_VolumeSlidingSpeed_ = colum;
			} else if (colum >= 0x80 && colum < 0x90) {
				// Fine volume_ down
				colum -= 0x80;
				if (colum != 0)
					voice.column_FineVolumeDown_ = colum;
				voice.volume_ -= voice.column_FineVolumeDown_;
				voice.volumeBAK_ = voice.volume_;
			} else if (colum >= 0x90 && colum < 0xa0) {
				// Fine volume_ up
				colum -= 0x90;
				if (colum != 0)
					voice.column_FineVolumeUp_ = colum;
				voice.volume_ += voice.column_FineVolumeUp_;
				voice.volumeBAK_ = voice.volume_;
			} else if (colum >= 0xa0 && colum < 0xb0) {
				voice.vibratoSpeed_ = colum - 0xa0;
			} else if (colum >= 0xb0 && colum < 0xc0) {
				voice.vibratoProf_ = colum - 0xb0;
			} else if (colum >= 0xc0 && colum < 0xd0) {
				voice.panning_ = (((colum - 0xc0) + 1) * 16) - 1;
			} else if (colum >= 0xd0 && colum < 0xe0) {
				colum -= 0xd0;
				voice.column_Effect_ = COLUMN_EFFECT_PANSLIDE;
				if (colum != 0)
					voice.column_PanSlidingSpeed_ = -colum;
			} else if (colum >= 0xe0 && colum < 0xf0) {
				colum -= 0xd0;
				voice.column_Effect_ = COLUMN_EFFECT_PANSLIDE;
				if (colum != 0)
					voice.column_PanSlidingSpeed_ = colum;
			} else if (colum >= 0xf0 && colum <= 0xff) {
				// PORTAMENTO TO
				colum -= 0xf0;
				if (note != 0 && note != 97 && voice.samplePlaying_ != null)
					voice.dstPeriod_ = getPeriod(note + voice.samplePlaying_.relativeNote, voice.fineTune_);
				voice.column_Effect_ = COLUMN_EFFECT_PORTAMENTO_TO;
				if (colum != 0)
					voice.column_PortamentoSpeed_ = colum << 6;
			}

			// Standart effect
			switch (effect) {
				case 0x0:
					// ARPEGGIO
					if (effectOperand_ != 0) {
						voice.effect_ = effect;
						voice.effectOperand_ = effectOperand_;
						voice.arp1_ = effectOperand_ & 0xf;
						voice.arp2_ = effectOperand_ >> 4;
						voice.bGotArpeggio_ = true;
						voice.arpeggioCount_ = 0;
					}
					;
				break;

				case 0x1:
					// PORTAMENTO UP
					voice.effect_ = effect;
					if (effectOperand_ != 0)
						voice.portaSpeed_ = effectOperand_ * 4;
				break;

				case 0x2:
					// PORTAMENTO DOWN
					voice.effect_ = effect;
					if (effectOperand_ != 0)
						voice.portaSpeed_ = effectOperand_ * 4;
				break;
				case 0x3:
					// PORTAMENTO TO
					if (note > 0 && note < 97 && voice.samplePlaying_ != null)
						voice.dstPeriod_ = getPeriod(note + voice.samplePlaying_.relativeNote, voice.fineTune_);
					voice.effect_ = effect;
					if (effectOperand_ != 0)
						voice.portamentoSpeed_ = effectOperand_ * 4;
				break;

				case 0x4:
					// VIBRATO
					voice.effect_ = effect;
					if ((effectOperand_ & 0xf0) != 0)
						voice.vibratoSpeed_ = (effectOperand_ >> 4) * 4;
					if ((effectOperand_ & 0x0f) != 0)
						voice.vibratoProf_ = effectOperand_ & 0x0f;
				break;

				case 0x5:
					// PORTAMENTO TO + VOLUME SLIDING
					if (note != 0 && voice.samplePlaying_ != null)
						voice.dstPeriod_ = getPeriod(note + voice.samplePlaying_.relativeNote, voice.fineTune_);
					voice.effect_ = effect;
					if ((effectOperand_ & 0xf0) != 0)
						voice.volumeSlidingSpeed_ = effectOperand_ >> 4;
					else if ((effectOperand_ & 0x0f) != 0)
						voice.volumeSlidingSpeed_ = -(effectOperand_ & 0x0f);
				break;

				case 0x6:
					// VIBRATO + VOLUME SLIDING
					voice.effect_ = effect;
					if ((effectOperand_ & 0xf0) != 0)
						voice.volumeSlidingSpeed_ = effectOperand_ >> 4;
					else if ((effectOperand_ & 0x0f) != 0)
						voice.volumeSlidingSpeed_ = -(effectOperand_ & 0x0f);
				break;

				case 0x7:
					// TREMOLO
					voice.effect_ = effect;
					if ((effectOperand_ & 0xf0) != 0)
						voice.tremoloSpeed_ = (effectOperand_ >> 4) * 4;
					if ((effectOperand_ & 0x0f) != 0)
						voice.tremoloProf_ = effectOperand_ & 0x0f;
				break;

				case 0x8:
					// PANNING
					voice.panning_ = effectOperand_;
				break;

				case 0x9:
					// SAMPLE JUMP
					voice.effect_ = effect;
					if (effectOperand_ != 0)
						voice.samplePosJump_ = effectOperand_ << 8;
				break;

				case 0xa:
					// VOLUME SLIDING
					voice.effect_ = effect;
					if ((effectOperand_ & 0xf0) != 0)
						voice.volumeSlidingSpeed_ = effectOperand_ >> 4;
					else if ((effectOperand_ & 0x0f) != 0)
						voice.volumeSlidingSpeed_ = -(effectOperand_ & 0xf);
				break;

				case 0xb:
					// POSITION JUMP
					bGotPatternJump = true;
					whereToJump = effectOperand_;
				break;

				case 0xc:
					// SET VOLUME
					voice.effect_ = effect;
					voice.volume_ = effectOperand_;
					voice.volumeBAK_ = voice.volume_;
				break;

				case 0xd:
					// PATTERN BREAK
					bGotPatternBreak = true;
					whereToBreak = ((effectOperand_ & 0xf0) >> 4) * 10 + (effectOperand_ & 0x0f);
				// Yes, posJump is given in BCD format. What is the interest ?
				// Absolutely none, thanks XM..
				break;

				case 0xe:
					// MISCELLANEOUS
					effectOperand2_ = effectOperand_ & 0xf;
					effectOperand_ >>= 4;

					switch (effectOperand_) {
						case 0x1:
							// FineSlideUp
							if (effectOperand2_ != 0)
								voice.fineSlideSpeed_ = effectOperand2_ * 4;
							voice.period_ -= voice.fineSlideSpeed_;
							voice.periodBAK_ = voice.period_;
						break;
						case 0x2:
							// FineSlideDown
							if (effectOperand2_ != 0)
								voice.fineSlideSpeed_ = effectOperand2_ * 4;
							voice.period_ += voice.fineSlideSpeed_;
							voice.periodBAK_ = voice.period_;
						break;
						case 0x4:
							// Set Vibrato Form
							voice.vibratoForm_ = effectOperand2_;
						break;
						case 0x5:
							// Set fineTune
							if (note == 0)
								break;
							voice.fineTune_ = effectOperand2_ << 4;
						break;
						case 0x6:
							// Pattern Loop
							if (effectOperand2_ != 0) {
								if (bGotPatternLoop_ == false) {
									bGotPatternLoop_ = true;
									patternLoopLeft_ = effectOperand2_;
								}
								patternLoopLeft_--;
								if (patternLoopLeft_ >= 0)
									posInPattern_ = patternPosLoop_ - 1;
								else
									bGotPatternLoop_ = false;
							} else
								patternPosLoop_ = posInPattern_;
						break;
						case 0x7:
							// Set Tremolo Form
							voice.tremoloForm_ = effectOperand2_;
						break;
						case 0x9:
							// Retrigger note
							if (effectOperand2_ == 0)
								break;
							voice.bGotRetrigNote_ = true;
							voice.tickForRetrigNote_ = effectOperand2_;
						break;
						case 0xa:
							// Fine Volumesliding Up
							if (effectOperand2_ != 0)
								voice.fineVolumeUp_ = effectOperand2_;
							voice.volume_ += voice.fineVolumeUp_;
							voice.volumeBAK_ = voice.volume_;
						break;
						case 0xb:
							// Fine Volumesliding Down
							if (effectOperand2_ != 0)
								voice.fineVolumeDown_ = effectOperand2_;
							voice.volume_ -= voice.fineVolumeDown_;
							voice.volumeBAK_ = voice.volume_;
						break;
						case 0xc:
							// note Cut
							voice.effect_ = 0xec;
							if (effectOperand2_ != 0)
								voice.noteCut_ = effectOperand2_;
							else
								voice.bNoteCutted_ = true;
						break;
						case 0xd:
							// note Delay
							voice.tickBeforeSample_ = effectOperand2_ + 1;
						break;
						case 0xe:
							// Pattern Delay
							if (patternDelay_ < 0)
								patternDelay_ = effectOperand2_;
						break;
					}
				break;

				case 0xf:
					// SET SPEED or BPM_
					if (effectOperand_ < 32)
						newSpeed = effectOperand_;
					else
						newBPM = effectOperand_;
				break;

				case 0xf + 'g' - 'f':
					// SET GLOBAL VOLUME
					globalVolume = effectOperand_;
					if (globalVolume > 64)
						globalVolume = 64;
				break;

				case 0xf + 'h' - 'f':
					// GLOBAL VOLUME SLIDING
					if (effectOperand_ != 0)
						voice.globalVolSlide_ = effectOperand_;
					if ((voice.globalVolSlide_ & 0xf0) != 0)
						globalVolume += (voice.globalVolSlide_ >> 4) * 4;
					else if ((voice.globalVolSlide_ & 0x0f) != 0)
						globalVolume -= (voice.globalVolSlide_ & 0xf) * 4;
					if (globalVolume > 64)
						globalVolume = 64;
					else if (globalVolume < 0)
						globalVolume = 0;
				break;

				case 0xf + 'r' - 'f':
					// MULTI RETRIG NOTE
					voice.bGotRetrigNote_ = true;
					if ((effectOperand_ & 0x0f) != 0)
						voice.tickForRetrigNote_ = effectOperand_ & 0x0f;
				break;

				case 0xf + 'l' - 'f':
					// SET ENVELOPE POSITION
					voice.seekEnvelope(effectOperand_);
				break;

				default:
				// UNKNOWN EFFECT
				break;
			}

			if ((voice.column_Effect_ == COLUMN_EFFECT_VOLUMESLIDE) && (voice.effect_ == 0xa))
				voice.column_Effect_ = COLUMN_EFFECT_NONE;

			if (((voice.effect_ == 0x4) || (voice.effect_ == 0x6)) && (voice.bGotVibrato_ == false))
				voice.bGotVibrato_ = true;

			if ((voice.effect_ == 0x7) && (voice.bGotTremolo_ == false))
				voice.bGotTremolo_ = true;
		}

		if (newSpeed != 0)
			speed_ = newSpeed;
		if (newBPM != 0)
			BPM = newBPM;

		posInPattern_++;

		if ((posInPattern_ == patterns_[actuPattern].nbLines) || (bGotPatternJump == true)
				|| (bGotPatternBreak == true)) {
			posInPattern_ = whereToBreak;
			if (bGotPatternJump == true)
				posChanson_ = whereToJump;
			else
				posChanson_++;

			if (posChanson_ >= listLen_) {
				posChanson_ = 0;
				posInPattern_ = 0;

				if (loopable_ == true) {
					posChanson_ = posRestart_;
					if (posRestart_ == 0) {
						globalVolume = 64;
						for (int i = 0; i < nbVoices_; i++) {
							chans_.removeChannel(voices_[i].sndchan_);
							voices_[i].actuInstru_ = null;
							voices_[i].samplePlaying_ = null;
						}
					}
				} else {
					isRunningPlay = false;
				}

			}
		}

	}

	@Override
	public byte[] getMixBuffer() {
		return pcm_;
	}

	@Override
	public boolean isLoopable() {
		return loopable_;
	}

	@Override
	public void setLoopable(boolean _b) {
		loopable_ = _b;
	}

	int getCurrentPos() {
		return posInPattern_;
	}

	@Override
	public void setFrequency(int frequency) {
		this.frequency = frequency;
	}

	@Override
	public void setVolume(float volume) {
		this.mainVolume=volume;
	}

}
