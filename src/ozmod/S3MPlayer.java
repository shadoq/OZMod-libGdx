package ozmod;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.AudioDevice;

public class S3MPlayer extends Thread implements ChipPlayer {

	private AudioDevice gdxAudio;
	static final int MAX_NB_CHANNELS = 32;

	static final int COMMAND_NONE = 0;
	static final int COMMAND_VOLSLIDEDOWN = 1;
	static final int COMMAND_VOLSLIDEUP = 2;
	static final int COMMAND_SLIDEDOWN = 3;
	static final int COMMAND_SLIDEUP = 4;
	static final int COMMAND_TONEPORTAMENTO = 5;
	static final int COMMAND_VIBRATO = 6;
	static final int COMMAND_TREMOR = 7;
	static final int COMMAND_TREMOLO = 8;
	static final int COMMAND_ARPEGGIO = 9;
	static final int COMMAND_SAMPLEOFFSET = 10;
	static final int COMMAND_RETRIG = 11;

	static final int EXTRACOMMAND_NONE = 0;
	static final int EXTRACOMMAND_DUALK = 1;
	static final int EXTRACOMMAND_DUALL = 2;

	static final int g_Period[] = { 1712, 1616, 1524, 1440, 1356, 1280, 1208, 1140, 1076, 1016, 960, 907 };
	static final int g_RetrigFadeOutTable[] = { 0, -1, -2, -4, -8, -16, -32, -64, 0, 1, 2, 4, 8, 16, 32, 64 };


	byte songName_[] = new byte[0x1c];
	int ID_;
	int fileType_;
	int listLen_;
	int nbInstrus_;
	int nbPatterns_;
	int fileFlags_;
	int volumeMaster_;
	int startSpeed_;
	int startTempo_;
	int masterMultiplier_;
	int specChannel_[] = new int[32];

	int realNbChannels_;
	int chanRemap_[] = new int[MAX_NB_CHANNELS];
	int numListPattern_[];
	Pattern patterns_[];
	Voice voices_[];
	int panChannel_[] = new int[32];

	Instru instrus_[];

	int freq_=44100;
	// SourceDataLine line_ = null;
	byte[] pcm_;
	ChannelsList chans_ = new ChannelsList();

	float realVolume_;
	float localVolume_;
	float globalVolume_ = 1.0f;

	boolean bGotPatternLoop_;
	int patternPosLoop_;
	int patternLoopLeft_;

	int patternDelay_ = -1;

	int tick_;
	int speed_;
	int tempo_;
	int posChanson_ = 0;
	int posInPattern_;
	boolean loopable_;

	boolean running_;
	boolean done_ = false;

	Timer timer_;

	private short[] pcms_;

	class Instru {

		int type;
		byte DOSname[] = new byte[0xc];
		int sampleDataOffset;
		int sampleDataLen;
		int startLoop;
		int endLoop;
		int defaultVolume;
		int disk;
		int packType;
		int flags;
		int C2Speed;
		byte instruName[] = new byte[0x1c];

		AudioData audio = new AudioData();
	}

	class Note {
		int note;
		int octave;
		int numInstru;
		int vol;
		int command;
		int commandParam;
	};

	class Row {
		Note notes[];
	};

	class Pattern {
		Row rows[] = new Row[64];
	};

	class Voice {

		Voice() {
		}

		void portamentoToNote() {
			if (period_ < dstPeriod_) {
				period_ += portamentoSpeed_;
				if (period_ > dstPeriod_)
					period_ = dstPeriod_;
			} else if (period_ > dstPeriod_) {
				period_ -= portamentoSpeed_;
				if (period_ < dstPeriod_)
					period_ = dstPeriod_;
			}
		}

		void vibrato(boolean _bFine) {
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
			if (_bFine == false)
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

		void updateSoundWithEffect() {
			int on, off, pos;

			switch (command_) {
				case COMMAND_VOLSLIDEDOWN:
					volume_ -= lastDCommandParam_ & 0x0f;
				break;

				case COMMAND_VOLSLIDEUP:
					volume_ += lastDCommandParam_ >> 4;
				break;

				case COMMAND_SLIDEDOWN:
					period_ += portaSpeed_ * 4;
				break;

				case COMMAND_SLIDEUP:
					period_ -= portaSpeed_ * 4;
				break;

				case COMMAND_TONEPORTAMENTO:
					portamentoToNote();
				break;

				case COMMAND_VIBRATO:
					vibrato(bFineVibrato_);
				break;

				case COMMAND_TREMOR:
					on = tremorValue_ >> 4;
					on++;
					off = tremorValue_ & 0xf;
					off++;
					pos = tremorCounter_ % (on + off);
					if (pos < on)
						volume_ = volumeBAK_;
					else
						volume_ = 0;
					tremorCounter_++;
				break;

				case COMMAND_ARPEGGIO:
					switch (arpeggioCount_ % 3) {
						case 0:
							period_ = getST3period(note_ + arp2_, instruPlaying_.C2Speed);
						break;
						case 1:
							period_ = getST3period(note_ + arp1_, instruPlaying_.C2Speed);
						break;
						case 2:
							period_ = getST3period(note_, instruPlaying_.C2Speed);
						break;
					}
					arpeggioCount_++;
				break;

				case COMMAND_TREMOLO:
					tremolo();
				break;
			}

			switch (extraCommand_) {
				case EXTRACOMMAND_DUALK:
					vibrato(false);
				break;

				case EXTRACOMMAND_DUALL:
					portamentoToNote();
				break;
			}
		}

		void soundUpdate() {
			// int i;
			// Instru instru;
			int freq;
			float Vol, Pan;

			if (instruPlaying_ == null)
				return;

			freq = 14317056 / period_;

			if (volume_ < 0)
				volume_ = 0;
			if (volume_ > 64)
				volume_ = 64;

			if (bNoteIsCutted_ == false)
				Vol = (volume_ / 64.0f) * globalVolume_;
			else
				Vol = 0;

			if (Vol < 0.0f)
				Vol = 0.0f;
			else if (Vol > 1.0f)
				Vol = 1.0f;

			Pan = (panning_ - 128) / 128.0f;

			if (bGotRetrigNote_ == true) {
				if ((tick_ - 1) % tickForRetrigNote_ == tickForRetrigNote_ - 1) {
					bNeedToBePlayed_ = true;
					volume_ += g_RetrigFadeOutTable[volFadeOutForRetrig_];
				}
			}
			if (bGotNoteCut_ == true) {
				if ((tick_ - 2) == noteCutDelay_)
					bNoteIsCutted_ = true;
			}

			if (bNeedToBePlayed_ == false) {
				sndchan_.frequency = freq;
				sndchan_.step = freq / (float) (freq_);
				sndchan_.setPan(Pan);
				sndchan_.vol = Vol;
				return;
			}

			int StartPos;
			if (command_ == COMMAND_SAMPLEOFFSET) {
				command_ = COMMAND_NONE;
				StartPos = samplePosJump_;
			} else
				StartPos = 0;

			chans_.removeChannel(sndchan_);
			// sndchan_.stop();

			if (tick_ >= tickBeforeSample_) {
				sndchan_.frequency = freq;
				sndchan_.step = freq / (float) (freq_);
				sndchan_.vol = Vol;
				sndchan_.setPan(Pan);
				sndchan_.pos = (float) StartPos;
				sndchan_.audio = instruPlaying_.audio;
				chans_.addChannel(sndchan_);

				bNeedToBePlayed_ = false;
				tickBeforeSample_ = 0;
			}
		}

		void evalue_DCommand(int commandParam) {
			if (commandParam != 0)
				lastDCommandParam_ = commandParam;

			int actuCommandParam = lastDCommandParam_;

			int x = actuCommandParam >> 4;
			int y = actuCommandParam & 0xf;

			if (y == 0 || (y == 0x0f && x != 0)) {
				// VOL SLIDE UP
				if (y == 0) {
					// normal
					command_ = COMMAND_VOLSLIDEUP;
				} else {
					// fine vslide
					volume_ += x;
				}
			} else if (x == 0 || (x == 0x0f && y != 0)) {
				// VOL SLIDE DOWN
				if (x == 0) {
					// normal
					command_ = COMMAND_VOLSLIDEDOWN;
				} else {
					// fine vslide
					volume_ -= y;
				}
			}
		}

		Instru instruPlaying_;
		Instru instruToPlay_;

		Channel sndchan_ = new Channel();

		int numVoice_;
		int period_, dstPeriod_, periodBAK_;
		int arpeggioCount_, arp1_, arp2_;
		int note_, command_, commandParam_, extraCommand_;
		boolean bNeedToStopSamplePlaying_;
		boolean bNeedToBePlayed_;

		int lastDCommandParam_;
		int portaSpeed_;

		int portamentoSpeed_;

		boolean bGotArpeggio_;

		int tremorValue_;
		int tremorCounter_;
		boolean bGotTremor_;

		boolean bGotVibrato_;
		int vibratoCounter_;
		int vibratoForm_;
		int vibratoSpeed_, vibratoProf_;
		boolean bFineVibrato_;

		boolean bGotTremolo_;
		int tremoloCounter_;
		int tremoloForm_;
		int tremoloSpeed_, tremoloProf_;

		boolean bGotNoteCut_;
		int noteCutDelay_;
		boolean bNoteIsCutted_;

		int volume_, volumeBAK_;
		int panning_;

		int tickBeforeSample_;
		int samplePosJump_;

		boolean bGotRetrigNote_;
		int tickForRetrigNote_ = -1;
		int volFadeOutForRetrig_;
	}

	S3MPlayer() {
	}

	protected void finalize() {
		running_ = false;
	}

	public OZMod.ERR load(LoaderFromMemory _input) {
		byte tmp[] = new byte[4];

		_input.readFully(songName_);
		ID_ = _input.readByte();
		fileType_ = _input.readByte();
		_input.forward(2);

		listLen_ = _input.readUShort();
		nbInstrus_ = _input.readUShort();
		nbPatterns_ = _input.readUShort();
		fileFlags_ = _input.readUShort();
		_input.forward(2); // version info
		_input.forward(2); // format version
		_input.read(tmp, 0, 4);
		String format = new String(tmp).substring(0, 4);
		if (format.compareTo("SCRM") != 0)
			return OZMod.proceedError(OZMod.ERR.BADFORMAT);

		int initialPanID;

		volumeMaster_ = _input.readUByte();
		startSpeed_ = _input.readUByte();
		startTempo_ = _input.readUByte();
		masterMultiplier_ = _input.readUByte();
		_input.forward(1);
		initialPanID = _input.readUByte();
		_input.forward(10);
		for (int i = 0; i < 32; i++)
			specChannel_[i] = _input.readUByte();

		for (int i = 0; i < MAX_NB_CHANNELS; i++)
			chanRemap_[i] = 255;

		int nbChannels = 0;
		for (int i = 0; i < 32; i++) {
			int specChannel = specChannel_[i];
			if (specChannel < 16) {
				chanRemap_[i] = nbChannels;
				nbChannels++;
			}
		}
		realNbChannels_ = nbChannels;

		numListPattern_ = new int[listLen_];
		for (int i = 0; i < listLen_; i++) {
			int num = _input.readUByte();
			numListPattern_[i] = num;
		}

		// Read instru and related info
		instrus_ = new Instru[nbInstrus_];
		int actuFilePos = _input.tell();
		for (int numInstru = 0; numInstru < nbInstrus_; numInstru++) {
			Instru instru = new Instru();
			instrus_[numInstru] = instru;

			int offsetInstru;
			_input.seek(actuFilePos);
			actuFilePos += 2;
			offsetInstru = _input.readUShort();
			offsetInstru <<= 4;
			_input.seek(offsetInstru);

			instru.type = _input.readUByte();
			_input.readFully(instru.DOSname);
			byte doff[] = new byte[3];
			_input.read(doff, 0, 3);
			int b1 = doff[0];
			int b2 = (doff[1] & 0xff) << 8;
			int b3 = (doff[2] & 0xff) << 16;
			instru.sampleDataOffset = b1 | b2 | b3;
			instru.sampleDataLen = _input.readInt();
			instru.startLoop = _input.readInt();
			instru.endLoop = _input.readInt();
			instru.defaultVolume = _input.readUByte();
			instru.disk = _input.readUByte();
			instru.packType = _input.readUByte();
			instru.flags = _input.readUByte();
			instru.C2Speed = _input.readInt();
			_input.forward(4); // non-used
			_input.forward(2); // gravis memory position
			_input.forward(6); // used for ?
			_input.readFully(instru.instruName);

			// int strID;
			// strID = _input.readInt();

			int sizeForSample = instru.sampleDataLen;
			if (sizeForSample == 0)
				continue;

			int nbC = 1;
			int nbBits = 8;

			if ((instru.flags & 2) != 0) {
				nbC = 2;
				sizeForSample *= 2;
			}
			if ((instru.flags & 4) != 0) {
				nbBits = 16;
				sizeForSample *= 2;
			}

			int startLoop = instru.startLoop;
			int endLoop = instru.endLoop;

			int off = instru.sampleDataOffset >> 4;
			_input.seek(off);

			byte pcm[] = new byte[sizeForSample];
			_input.readFully(pcm);

			if (nbBits == 8) {
				for (int i = 0; i < sizeForSample; i++)
					pcm[i] -= 128;

				if (nbC == 2) {
					// left and right are entrelaced
					byte tmpb[] = new byte[pcm.length];
					int j = 0;
					for (int i = 0; i < instru.sampleDataLen; i++) {
						byte left = pcm[i];
						byte right = pcm[i + instru.sampleDataLen];
						tmpb[j++] = left;
						tmpb[j++] = right;
					}
					pcm = tmpb;
				}
			} else {
				int nbsamp = instru.sampleDataLen * nbC;
				for (int i = 0; i < nbsamp; i++) {
					short b = (short) (pcm[i * 2 + 0] & 0xff);
					b |= (short) (pcm[i * 2 + 1] << 8);
					b -= 32768;
					pcm[i * 2 + 0] = (byte) (b & 0xff);
					pcm[i * 2 + 1] = (byte) (b >> 8);
				}

				if (nbC == 2) {
					// left and right are entrelaced
					byte tmpb[] = new byte[pcm.length];
					int j = 0;
					for (int i = 0; i < instru.sampleDataLen; i++) {
						byte left0 = pcm[i * 2];
						byte left1 = pcm[i * 2 + 1];

						byte right0 = pcm[i * 2 + instru.sampleDataLen];
						byte right1 = pcm[i * 2 + 1 + instru.sampleDataLen * 2];

						tmpb[j++] = left0;
						tmpb[j++] = left1;
						tmpb[j++] = right0;
						tmpb[j++] = right1;
					}
					pcm = tmpb;
				}
			}

			if ((instru.flags & 1) == 0)
				instru.audio.make(pcm, nbBits, nbC);
			else
				instru.audio.make(pcm, nbBits, nbC, startLoop, endLoop, AudioData.LOOP_FORWARD);
		}

		// Read pattern
		patterns_ = new Pattern[nbPatterns_];
		for (int numPattern = 0; numPattern < nbPatterns_; numPattern++) {

			Pattern pattern = new Pattern();
			patterns_[numPattern] = pattern;

			int offsetPattern = 0;
			_input.seek(actuFilePos);
			actuFilePos += 2;

			offsetPattern = _input.readUShort();
			offsetPattern <<= 4;
			_input.seek(offsetPattern + 2);

			Note bidonNote = new Note();
			Note actuNote;

			int numRow = 0;
			while (numRow < 64) {
				Row row = new Row();
				pattern.rows[numRow] = row;
				row.notes = new Note[nbChannels];
				for (int i = 0; i < nbChannels; i++)
					row.notes[i] = new Note();

				while (true) {
					int byt = 0;
					byt = _input.readUByte();
					if (byt == 0)
						break;

					int numChan = byt & 31;
					int realNumChan = chanRemap_[numChan];

					if (realNumChan < 16)
						actuNote = row.notes[realNumChan];
					else
						actuNote = bidonNote;

					if ((byt & 32) != 0) {
						int note;
						note = _input.readUByte();
						actuNote.numInstru = _input.readUByte();
						actuNote.note = ((note >> 4) * 12) + (note & 0xf);
					}

					if ((byt & 64) != 0) {
						actuNote.vol = _input.readUByte();
						actuNote.vol++;
					}

					if ((byt & 128) != 0) {
						actuNote.command = _input.readUByte();
						actuNote.commandParam = _input.readUByte();
					}
				}
				numRow++;
			}
		}

		voices_ = new Voice[realNbChannels_];
		for (int i = 0; i < realNbChannels_; i++) {
			Voice voice = new Voice();
			voices_[i] = voice;

			voice.numVoice_ = i;
			if ((i & 1) != 0)
				voice.panning_ = 192;
			else
				voice.panning_ = 64;
		}

		if (initialPanID == 252) {
			_input.seek(actuFilePos);
			for (int i = 0; i < 32; i++)
				panChannel_[i] = _input.readUByte();

			int j = 0;
			for (int i = 0; i < 32; i++) {
				if (specChannel_[i] < 16 && (panChannel_[i] & 0x20) != 0) {
					int pan = panChannel_[i] & 0xf;
					voices_[j++].panning_ = (short) (pan << 4);
				}
			}
		}

		speed_ = startSpeed_;
		tempo_ = startTempo_;

		return OZMod.proceedError(OZMod.ERR.NOERR);
	}

	int getST3period(int note, int c2speed) {
		int n = note % 12;
		int o = note / 12;

		int h = (8363 * 16 * g_Period[n]) >> o;

		if (c2speed == 0)
			return 1;
		else
			return h / c2speed;
	}

	void dispatchNotes() {
		int note, numInstru, volume, command, commandParam;
		int actuCommandParam;
		int com, par;
		int numVoice;

		int newSpeed = 0;
		int newTempo = 0;

		// again:;
		int actuPattern = numListPattern_[posChanson_];
		/*
		 * if (actuPattern >= nbPatterns_) { posChanson_++; if (posChanson_ ==
		 * listLen_ || numListPattern_[ posChanson_ ] == 0xff) {
		 * sendEndNotify(notifiesList_, nbNotifies_); if (flags_ == FLAG_LOOP) {
		 * posChanson_ = 0; for (int i = 0; i < realNbChannels_; i++) {
		 * voices_[i].sndchan_.stop(); } } else { stop(true, false); } return; }
		 * else goto again; }
		 */

		boolean bGotPatternJump = false;
		boolean bGotPatternBreak = false;
		int whereToJump = 0;
		int whereToBreak = 0;

		Row actuRow = patterns_[actuPattern].rows[posInPattern_];

		for (numVoice = 0; numVoice < realNbChannels_; numVoice++) {
			Voice voice = voices_[numVoice];

			// if (numVoice != 2)
			// continue;

			note = actuRow.notes[numVoice].note;
			volume = actuRow.notes[numVoice].vol;
			numInstru = actuRow.notes[numVoice].numInstru;
			command = actuRow.notes[numVoice].command;
			commandParam = actuRow.notes[numVoice].commandParam;

			boolean AllowToUpdateNote = true;

			if (voice.command_ != COMMAND_VIBRATO && voice.extraCommand_ != EXTRACOMMAND_DUALK
					&& voice.bGotVibrato_ == true) {
				voice.period_ = voice.periodBAK_;
				voice.bGotVibrato_ = false;
			} else if (voice.command_ != COMMAND_TREMOR && voice.bGotTremor_ == true) {
				voice.volume_ = voice.volumeBAK_;
				voice.bGotTremor_ = false;
			} else if (voice.command_ != COMMAND_TREMOLO && voice.bGotTremolo_ == true) {
				voice.volume_ = voice.volumeBAK_;
				voice.bGotTremolo_ = false;
			} else if (voice.command_ != COMMAND_ARPEGGIO && voice.bGotArpeggio_ == true) {
				voice.period_ = voice.periodBAK_;
				voice.bGotArpeggio_ = false;
			}

			if (voice.instruPlaying_ != null) {
				if (command == ('G' - 0x40))
					AllowToUpdateNote = false;
			}

			if (numInstru > 0) {
				if (numInstru <= nbInstrus_)
					voice.instruToPlay_ = instrus_[numInstru - 1];
				else {
					chans_.removeChannel(voice.sndchan_);
					voice.instruPlaying_ = null;
					voice.instruToPlay_ = null;
				}

				if (voice.instruToPlay_ != null) {
					voice.volume_ = voice.instruToPlay_.defaultVolume;
					voice.volumeBAK_ = voice.volumeBAK_;
				}
				voice.vibratoCounter_ = 0;
				voice.tremorCounter_ = 0;
			}

			if (note > 0 && note < 97 && voice.instruToPlay_ != null) {
				int Period;

				Period = getST3period(note, voice.instruToPlay_.C2Speed);
				voice.periodBAK_ = Period;
				voice.note_ = note;

				if (AllowToUpdateNote == true) {
					voice.instruPlaying_ = voice.instruToPlay_;
					voice.period_ = voice.dstPeriod_ = Period;
					voice.bNeedToBePlayed_ = true;
					voice.bNoteIsCutted_ = false;
				}
			} else if (note == 194) {
				voice.bNoteIsCutted_ = true;
			}

			if (volume != 0) {
				voice.volume_ = volume - 1;
				voice.volumeBAK_ = voice.volume_;
			}

			voice.command_ = COMMAND_NONE;
			voice.extraCommand_ = EXTRACOMMAND_NONE;
			voice.bGotRetrigNote_ = false;
			voice.bGotNoteCut_ = false;
			voice.bFineVibrato_ = false;

			switch (command) {
				case 'A' - 0x40:
					// ===========
					// SET SPEED
					// ===========

					newSpeed = commandParam;
				break;

				case 'B' - 0x40:
					// ===============
					// JUMP TO ORDER
					// ===============

					bGotPatternJump = true;
					whereToJump = commandParam;
				break;

				case 'C' - 0x40:
					// ===============
					// PATTERN BREAK
					// ===============

					bGotPatternBreak = true;
					whereToBreak = (commandParam >> 4) * 10 + (commandParam & 0x0f);
				break;

				case 'D' - 0x40:
					// ==========================
					// NORMAL/FINE VOLUME SLIDE
					// ==========================

					voice.evalue_DCommand(commandParam);
				break;

				case 'E' - 0x40:
					// ============
					// SLIDE DOWN
					// ============

					if (commandParam != 0)
						voice.portaSpeed_ = commandParam;

					actuCommandParam = voice.portaSpeed_;
					if ((actuCommandParam >> 4) == 0x0e) {
						// extra fine slide down
						voice.period_ += actuCommandParam & 0xf;
					} else if ((actuCommandParam >> 4) == 0x0f) {
						// fine slide down
						voice.period_ += (actuCommandParam & 0xf) * 4;
					} else {
						// slide down
						voice.command_ = COMMAND_SLIDEDOWN;
					}
				break;

				case 'F' - 0x40:
					// ==========
					// SLIDE UP
					// ==========

					if (commandParam != 0)
						voice.portaSpeed_ = commandParam;

					actuCommandParam = voice.portaSpeed_;
					if ((actuCommandParam >> 4) == 0x0e) {
						// extra fine slide up
						voice.period_ -= actuCommandParam & 0xf;
					} else if ((actuCommandParam >> 4) == 0x0f) {
						// fine slide up
						voice.period_ -= (actuCommandParam & 0xf);
					} else {
						// slide up
						voice.command_ = COMMAND_SLIDEUP;
					}
				break;

				case 'G' - 0x40:
					// ====================
					// PORTAMENTO TO NOTE
					// ====================

					if (note > 0 && note < 97 && voice.instruPlaying_ != null) {
						voice.dstPeriod_ = getST3period(note, voice.instruPlaying_.C2Speed);
					}

					if (commandParam != 0)
						voice.portamentoSpeed_ = commandParam * 4;

					voice.command_ = COMMAND_TONEPORTAMENTO;

				break;

				case 'U' - 0x40:
					voice.bFineVibrato_ = true;
					// no break, continuy ..

				case 'H' - 0x40:
					// =========
					// VIBRATO
					// =========

					voice.command_ = COMMAND_VIBRATO;
					if ((commandParam >> 4) != 0)
						voice.vibratoSpeed_ = (commandParam >> 4) * 4;
					if ((commandParam & 0x0f) != 0)
						voice.vibratoProf_ = commandParam & 0x0f;
				break;

				case 'I' - 0x40:
					// ========
					// TREMOR
					// ========

					voice.command_ = COMMAND_TREMOR;

					if (commandParam != 0)
						voice.tremorValue_ = commandParam;
				break;

				case 'J' - 0x40:
					// ==========
					// ARPEGGIO
					// ==========

					voice.command_ = COMMAND_ARPEGGIO;

					if ((commandParam & 0xf) != 0)
						voice.arp1_ = commandParam & 0xf;
					if ((commandParam >> 4) != 0)
						voice.arp2_ = commandParam >> 4;

					voice.arpeggioCount_ = 0;
				break;

				case 'K' - 0x40:
					// ===========================
					// DUAL COMMAND: H00 and Dxy
					// ===========================

					voice.extraCommand_ = EXTRACOMMAND_DUALK;
					voice.evalue_DCommand(commandParam);
				break;

				case 'L' - 0x40:
					// ===========================
					// DUAL COMMAND: G00 and Dxy
					// ===========================

					voice.extraCommand_ = EXTRACOMMAND_DUALL;
					voice.evalue_DCommand(commandParam);
				break;

				case 'O' - 0x40:
					voice.command_ = COMMAND_SAMPLEOFFSET;
					if (commandParam != 0)
						voice.samplePosJump_ = commandParam << 8;
				break;

				case 'Q' - 0x40:
					voice.command_ = COMMAND_RETRIG;
					if (commandParam != 0) {
						voice.tickForRetrigNote_ = commandParam & 0xf;
						voice.volFadeOutForRetrig_ = commandParam >> 4;
					}

					voice.bGotRetrigNote_ = true;
				break;

				case 'R' - 0x40:
					// =========
					// TREMOLO
					// =========

					voice.command_ = COMMAND_TREMOLO;

					if ((commandParam >> 4) != 0)
						voice.tremoloSpeed_ = (commandParam >> 4) * 4;
					if ((commandParam & 0x0f) != 0)
						voice.tremoloProf_ = commandParam & 0x0f;
				break;

				case 'S' - 0x40:
					// ===============
					// MISCELLANEOUS
					// ===============

					com = commandParam >> 4;
					par = commandParam & 0xf;

					switch (com) {
						case 0x8:
							// channel pan posistion
							voice.panning_ = par << 4;
						break;

						case 0xb:
							// pattern loop
							if (par == 0)
								patternPosLoop_ = posInPattern_;
							else {
								if (bGotPatternLoop_ == false) {
									bGotPatternLoop_ = true;
									patternLoopLeft_ = par;
								}
								patternLoopLeft_--;
								if (patternLoopLeft_ >= 0) {
									posInPattern_ = patternPosLoop_ - 1;
								} else
									bGotPatternLoop_ = false;
							}
						break;

						case 0xc:
							// note cut
							voice.noteCutDelay_ = par;
							voice.bGotNoteCut_ = true;
						break;

						case 0xd:
							// note delay
							voice.tickBeforeSample_ = par + 1;
						break;

						case 0xe:
							// pattern delay
							if (patternDelay_ < 0)
								patternDelay_ = par;
						break;
					}
				break;

				case 'T' - 0x40:
					// ===========
					// SET TEMPO
					// ===========

					newTempo = commandParam;
				break;

				case 'V' - 0x40:
					// ===================
					// SET GLOBAL VOLUME
					// ===================

					globalVolume_ = commandParam / 64.f;
				break;

				case 'X' - 0x40:
					// =============
					// SET PANNING
					// =============

					voice.panning_ = (commandParam & 0x7f) << 1;
				break;

			}

			if ((voice.command_ == COMMAND_VIBRATO || voice.extraCommand_ == EXTRACOMMAND_DUALK)
					&& voice.bGotVibrato_ == false) {
				voice.periodBAK_ = voice.period_;
				voice.bGotVibrato_ = true;
			} else if (voice.command_ == COMMAND_TREMOR && voice.bGotTremor_ == false) {
				voice.volumeBAK_ = voice.volume_;
				voice.bGotTremor_ = true;
			} else if (voice.command_ == COMMAND_TREMOLO && voice.bGotTremolo_ == false) {
				voice.volumeBAK_ = voice.volume_;
				voice.bGotTremolo_ = true;
			} else if (voice.command_ == COMMAND_ARPEGGIO && voice.bGotArpeggio_ == false) {
				voice.periodBAK_ = voice.period_;
				voice.bGotArpeggio_ = true;
			}
		}

		if (newSpeed != 0)
			speed_ = newSpeed;

		if (newTempo != 0)
			tempo_ = newTempo;

		posInPattern_++;

		if (posInPattern_ == 64 || bGotPatternJump == true || bGotPatternBreak == true) {
			posInPattern_ = whereToBreak;
			if (bGotPatternJump == true)
				posChanson_ = whereToJump;
			else
				posChanson_++;

			if (posChanson_ == listLen_ || numListPattern_[posChanson_] == 0xff) {

				if (loopable_ == true)
					posChanson_ = 0;
				else
					running_ = false;
			}
		}
	}

	public void run() {
		gdxAudio = Gdx.audio.newAudioDevice(freq_, false);
		gdxAudio.setVolume(1.0f);

		int soundBufferLen = freq_ * 4;
		pcm_ = new byte[soundBufferLen];
		pcms_ = new short[pcm_.length / 2];

		long cumulTime = 0;

		while (running_) {

			float timerRate = 1000.0f / (tempo_ * 0.4f);
			int intTimerRate = (int) Math.floor(timerRate);

			long since = timer_.getDelta();
			cumulTime += since;

			if (cumulTime >= intTimerRate) {

				cumulTime -= intTimerRate;

				oneShot(intTimerRate);

			}

			Thread.yield();
		}
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
			for (int i = 0; i < realNbChannels_; i++)
				voices_[i].updateSoundWithEffect();
		}

		for (int i = 0; i < realNbChannels_; i++)
			voices_[i].soundUpdate();

		mixSample(_timer);
	}

	void mixSample(int _time) {
		int nbsamp = freq_ / (1000 / _time);
		Arrays.fill(pcm_, (byte) 0);
		chans_.mix(nbsamp, pcm_);
		ByteBuffer.wrap(pcm_).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(pcms_, 0, nbsamp * 2);
		gdxAudio.writeSamples(pcms_, 0, nbsamp * 2);
	}

	public void play() {
		if (isAlive() == true || done_ == true)
			return;

		timer_ = new Timer();
		running_ = true;

		start();
	}

	public byte[] getMixBuffer() {
		return pcm_;
	}

	public int getCurrentPos() {
		return posInPattern_;
	}

	public boolean isLoopable() {
		return loopable_;
	}

	public void setLoopable(boolean _b) {
		loopable_ = _b;
	}

	public void done() {
		running_ = false;
		try {
			join();
		} catch (InterruptedException e) {
		}
		gdxAudio.dispose();
	}

	@Override
	public void setVolume(float volume) {
		this.globalVolume_=volume;
	}

	@Override
	public void setFrequency(int frequency) {
		this.freq_=frequency;
	}
}
