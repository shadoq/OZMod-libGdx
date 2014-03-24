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

import java.io.File;
import java.net.URL;

import com.badlogic.gdx.files.FileHandle;

public class OZMod {

	boolean initialised_ = false;
	static ERR lastError_;

	static public enum ERR {
		NOERR, ALREADYINIT, NEEDINIT, FILENOTFOUND, INVALIDFORMAT, DEVICESATURATE, READERROR, BADFORMAT, UNKNOWN
	}

	public OZMod() {
		lastError_ = ERR.NOERR;
	}

	public void finalize() {
	}

	public ERR initOutput() {
		return initOutput(0);
	}

	public ERR initOutput(int _deviceIndex) {
		if (initialised_ == true)
			return proceedError(ERR.ALREADYINIT);

		initialised_ = true;

		return proceedError(ERR.NOERR);
	}

	public ERR startSoftwareMixer() {
		if (initialised_ == false)
			return proceedError(ERR.NEEDINIT);
		return ERR.NOERR;
	}

	public ChipPlayer getPlayer(FileHandle gdxFile) {
		if (gdxFile.extension().equals("mod")) {
			return getMOD(gdxFile);
		} else if (gdxFile.extension().equals("xm")) {
			return getXM(gdxFile);
		} else if (gdxFile.extension().equals("s3m")) {
			return getS3M(gdxFile);
		} else {
			return null;
		}
	}

	public MODPlayer getMOD(FileHandle gdx_file) {
		if (initialised_ == false) {
			OZMod.proceedError(OZMod.ERR.NEEDINIT);
			return null;
		}

		MODPlayer player = new MODPlayer();
		LoaderFromMemory loader = new LoaderFromMemory(gdx_file, LoaderFromMemory.BIGENDIAN);
		ERR err = loader.readContent();
		if (err != ERR.NOERR)
			return null;

		player.load(loader);

		return player;
	}

	public S3MPlayer getS3M(FileHandle gdx_file) {
		if (initialised_ == false) {
			OZMod.proceedError(OZMod.ERR.NEEDINIT);
			return null;
		}

		S3MPlayer player = new S3MPlayer();
		LoaderFromMemory loader = new LoaderFromMemory(gdx_file, LoaderFromMemory.LITTLEENDIAN);
		ERR err = loader.readContent();
		if (err != ERR.NOERR)
			return null;

		player.load(loader);

		return player;
	}

	public XMPlayer getXM(FileHandle gdx_file) {
		if (initialised_ == false) {
			OZMod.proceedError(OZMod.ERR.NEEDINIT);
			return null;
		}

		XMPlayer player = new XMPlayer();
		LoaderFromMemory loader = new LoaderFromMemory(gdx_file, LoaderFromMemory.LITTLEENDIAN);
		ERR err = loader.readContent();
		if (err != ERR.NOERR)
			return null;

		player.load(loader);

		return player;
	}

	public static short wordSwap(short _value) {
		short block1 = (short) (_value << 8);
		short block2 = (short) ((_value >> 8) & 0xff);
		return (short) (block1 | block2);
	}

	static final public ERR getLastError() {
		return lastError_;
	}

	static final ERR proceedError(ERR _err) {
		lastError_ = _err;
		return _err;
	}

}
