package ozmod;

public class Channel {

    static final int RAMP_NONE = 0;
    static final int RAMP_IN = 1;

    public Channel()
    {
        audio = new AudioData();
    }
    
    void setPan(float _p)
    {
        if (_p < -1.0f)
            _p = -1.0f;
        else if (_p > 1.0f)
            _p = 1.0f;

        if (_p < 0) {
            leftFactor = 1.0f;
            rightFactor = 1.0f + _p;
        }
        else {
            leftFactor = 1.0f - _p;
            rightFactor = 1.0f;
        }
    }
    
    AudioData audio;

    int frequency;
    float pos;
    float step;
    float leftFactor = 1.0f;
    float rightFactor = 1.0f;
    float vol;
    int ramp = RAMP_NONE;
    float rampVolume;
    float rampStep;
}
