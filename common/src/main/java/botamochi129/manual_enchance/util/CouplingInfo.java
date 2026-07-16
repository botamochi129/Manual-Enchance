package botamochi129.manual_enchance.util;

public class CouplingInfo {
    public final long masterId;
    public final double offset;
    public final CouplingManager.ConnectionType type;

    public CouplingInfo(long masterId, double offset, CouplingManager.ConnectionType type) {
        this.masterId = masterId;
        this.offset = offset;
        this.type = type;
    }
}