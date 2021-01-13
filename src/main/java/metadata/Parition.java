package metadata;

public class Parition {

    public Parition(String paritionid) {
        _State = State.HOT;
        LastModiftime = Timer.GetOldDayTime();
        _ParitionID = paritionid;
    }

    State _State;
    String LastModiftime;
    String _ParitionID;

    public String getLastModiftime() {
        return LastModiftime;
    }

    public void setLastModiftime(String lastModiftime) {
        LastModiftime = lastModiftime;
    }

    public State get_State() {
        return _State;
    }

    public String get_ParitionID() {
        return _ParitionID;
    }
}

