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

import java.io.*;
import java.net.*;

import com.badlogic.gdx.files.FileHandle;

public class LoaderFromMemory {

    static final int BIGENDIAN = 0;
    static final int LITTLEENDIAN = 1;

    FileHandle file_ = null;
    URL url_ = null;
    byte[] content_;
    int pos_ = 0;
    int endianness_;

    public LoaderFromMemory(FileHandle _file, int _endianness)
    {
        file_ = _file;
        endianness_ = _endianness;
    }

    public LoaderFromMemory(URL _url, int _endianness)
    {
        url_ = _url;
        endianness_ = _endianness;
    }

    public OZMod.ERR readContent()
    {
        if (file_ != null)
            return readContentFromFile();
        if (url_ != null)
            return readContentFromURL(url_);

        return OZMod.proceedError(OZMod.ERR.NEEDINIT);
    }

    OZMod.ERR readContentFromFile()
    {

        content_ = file_.readBytes();
        return OZMod.proceedError(OZMod.ERR.NOERR);

//        RandomAccessFile input;
//
//        try {
//            input = new RandomAccessFile(file_, "r");
//        }
//        catch(FileNotFoundException e)
//        {
//            return OZMod.proceedError(OZMod.ERR.FILENOTFOUND);
//        }
//
//        try {
//            int fileSize = (int) input.length();
//            content_ = new byte[fileSize];
//            input.readFully(content_);
//            input.close();
//        }
//        catch(IOException e) {
//            return OZMod.proceedError(OZMod.ERR.READERROR);
//        }
//
//        return OZMod.proceedError(OZMod.ERR.NOERR);
    }

    OZMod.ERR readContentFromURL(URL _url)
    {
        // first calculate the size of the file
        // very lazy evaluation to calculate the length of the stream, it sucks as hell but didn't find better for now
        // in fact I could use a vector but it would suck also anyway

        InputStream stream;
        int total = 0;
        byte b[] = new byte[1];
        try
        {
            stream = _url.openStream();
            while(true)
            {
                int nb = stream.read(b);
                if (nb == -1)
                    break;
                total++;
            }
            stream.close();
        }
        catch(IOException e) {
            return OZMod.proceedError(OZMod.ERR.READERROR);
        }

        content_ = new byte[total];

        int off = 0;
        try
        {
            stream = _url.openStream();
            while(true)
            {
                int nb = stream.read(b);
                if (nb == -1)
                    break;

                content_[off] = b[0];
                off ++;
            }
            stream.close();
        }
        catch(IOException e) {
            return OZMod.proceedError(OZMod.ERR.READERROR);
        }

        return OZMod.proceedError(OZMod.ERR.NOERR);
    }

    public void read(byte[] _buf, int off, int len)
    {
        for (int i = off; i < off+len; i++)
            _buf[i] = content_[pos_++];
    }

    public byte readByte()
    {
        return content_[pos_++];
    }

    public int readUByte()
    {
        return content_[pos_++] & 0xff;
    }

    public short readShort()
    {
        int b1 = content_[pos_++];
        int b2 = content_[pos_++];

        if (endianness_ == BIGENDIAN) {
            b2 &= 0xff;
            return (short) ((b1 << 8) | b2);
        }
        b1 &= 0xff;
        return (short) ((b2 << 8) | b1);
    }

    public short readUShort()
    {
        int b1 = content_[pos_++];
        int b2 = content_[pos_++];
        b1 &= 0xff;
        b2 &= 0xff;
        if (endianness_ == BIGENDIAN)
            return (short) ((b1 << 8) | b2);
        else
            return (short) ((b2 << 8) | b1);
    }

    public int readInt()
    {
        int b1 = content_[pos_++];
        int b2 = content_[pos_++];
        int b3 = content_[pos_++];
        int b4 = content_[pos_++];
        b1 &= 0xff;
        b2 &= 0xff;
        b3 &= 0xff;
        b4 &= 0xff;
        if (endianness_ == BIGENDIAN)
            return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
        else
            return (b4 << 24) | (b3 << 16) | (b2 << 8) | b1;
    }

    public void readFully(byte[] _buf)
    {
        int len = _buf.length;
        for (int i = 0; i < len; i++)
            _buf[i] = readByte();
    }

    public void seek(int _pos)
    {
        pos_ = _pos;
    }

    public int tell()
    {
        return pos_;
    }

    public void forward(int _fw)
    {
        pos_ += _fw;
    }

}
