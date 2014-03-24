package ozmod;


class AudioData
{
    static final int LOOP_FORWARD = 1;
    static final int LOOP_PINGPONG = 2;
    
    void make(byte _data[], int _nbBits, int _nbChans)
    {
        if (_nbChans == 1) {
            if (_nbBits == 8) {
                pcm_ = new byte[_data.length+1];
                for (int i = 0; i < _data.length; i++)
                    pcm_[i] = _data[i];
                pcm_[_data.length] = pcm_[0];
                endFrame_ = _data.length;
            }
            else if (_nbBits == 16) {
                pcm_ = new byte[_data.length+2];
                for (int i = 0; i < _data.length; i++)
                    pcm_[i] = _data[i];
                pcm_[_data.length] = pcm_[0];
                pcm_[_data.length+1] = pcm_[1];
                endFrame_ = _data.length >> 1;
            }
        }
        else {
            if (_nbBits == 8) {
                pcm_ = new byte[_data.length+2];
                for (int i = 0; i < _data.length; i++)
                    pcm_[i] = _data[i];
                pcm_[_data.length] = pcm_[0];
                pcm_[_data.length+1] = pcm_[1];
                endFrame_ = _data.length >> 1;
            }
            else if (_nbBits == 16) {
                pcm_ = new byte[_data.length+4];
                for (int i = 0; i < _data.length; i++)
                    pcm_[i] = _data[i];
                pcm_[_data.length] = pcm_[0];
                pcm_[_data.length+1] = pcm_[1];
                pcm_[_data.length+2] = pcm_[2];
                pcm_[_data.length+3] = pcm_[3];
                endFrame_ = _data.length >> 2;
            }
        }

        nbBits_ = _nbBits;
        nbChans_ = _nbChans;
    }

    void make(byte _data[], int _nbBits, int _nbChans, int _startLoop, int _endLoop, int _loopType)
    {
        if (_loopType == LOOP_FORWARD) {
            startLoop_ = _startLoop;
            endLoop_ = _endLoop; 

            if (_nbChans == 1) {
                if (_nbBits == 8) {
                    pcm_ = new byte[_endLoop+1];
                    for (int i = 0; i < _endLoop; i++)
                        pcm_[i] = _data[i];
                    pcm_[_endLoop] = pcm_[_startLoop];
                }
                else if (_nbBits == 16) {
                    int len = _endLoop * 2;
                    pcm_ = new byte[len+2];
                    for (int i = 0; i < len; i++)
                        pcm_[i] = _data[i];
                    pcm_[len] = pcm_[_startLoop*2];
                    pcm_[len+1] = pcm_[_startLoop*2+1];
                }
            }
            else if (_nbChans == 2) {
                if (_nbBits == 8) {
                    int len = _endLoop * 2;
                    pcm_ = new byte[len+2];
                    for (int i = 0; i < len; i++)
                        pcm_[i] = _data[i];
                    pcm_[len] = pcm_[_startLoop*2];
                    pcm_[len+1] = pcm_[_startLoop*2+1];
                }
                else if (_nbBits == 16) {
                    int len = _endLoop * 4;
                    pcm_ = new byte[len+4];
                    for (int i = 0; i < len; i++)
                        pcm_[i] = _data[i];
                    pcm_[len] = pcm_[_startLoop*4];
                    pcm_[len+1] = pcm_[_startLoop*4+1];
                    pcm_[len+2] = pcm_[_startLoop*4+2];
                    pcm_[len+3] = pcm_[_startLoop*4+3];
                }
            }        
        }
        else if (_loopType == LOOP_PINGPONG) {

            startLoop_ = _startLoop;
            endLoop_ = _endLoop + (_endLoop - _startLoop);

            if (_nbChans == 1) {
                if (_nbBits == 8) {
                    pcm_ = new byte[_endLoop + (_endLoop - _startLoop) + 1];
                    int off = 0;
                    for (int i = 0; i < _endLoop; i++)
                        pcm_[off++] = _data[i];
                    
                    for (int i = _endLoop-1; i > _startLoop; i--)
                        pcm_[off++] = _data[i];

                    pcm_[off] = pcm_[_startLoop];
                }
                else if (_nbBits == 16) {
                    pcm_ = new byte[ (_endLoop + (_endLoop - _startLoop)) * 2 + 2];
                    int off = 0;
                    for (int i = 0; i < _endLoop*2; i++)
                        pcm_[off++] = _data[i];
                    
                    for (int i = (_endLoop*2)-2; i > _startLoop*2; i--)
                        pcm_[off++] = _data[i];

                    pcm_[off] = pcm_[_startLoop*2];
                    pcm_[off+1] = pcm_[_startLoop*2+1];
                }
            }       
            else if (_nbChans == 2) {
                if (_nbBits == 8) {
                    pcm_ = new byte[ (_endLoop + (_endLoop - _startLoop)) * 2 + 2];
                    int off = 0;
                    for (int i = 0; i < _endLoop*2; i++)
                        pcm_[off++] = _data[i];
                    
                    for (int i = (_endLoop*2)-2; i > _startLoop*2; i--)
                        pcm_[off++] = _data[i];

                    pcm_[off] = pcm_[_startLoop*2];
                    pcm_[off+1] = pcm_[_startLoop*2+1];
                }
                else if (_nbBits == 16) {
                    pcm_ = new byte[ (_endLoop + (_endLoop - _startLoop)) * 4 + 4];
                    int off = 0;
                    for (int i = 0; i < _endLoop*4; i++)
                        pcm_[off++] = _data[i];
                    
                    for (int i = (_endLoop*4)-4; i > _startLoop*4; i--)
                        pcm_[off++] = _data[i];

                    pcm_[off] = pcm_[_startLoop*4];
                    pcm_[off+1] = pcm_[_startLoop*4+1];
                    pcm_[off+2] = pcm_[_startLoop*4+2];
                    pcm_[off+3] = pcm_[_startLoop*4+3];
                }

            }
        }

        nbBits_ = _nbBits;
        nbChans_ = _nbChans;
    }
    
    byte pcm_[];
    int nbBits_;
    int nbChans_;
    int startLoop_;
    int endLoop_;
    int endFrame_;
}
