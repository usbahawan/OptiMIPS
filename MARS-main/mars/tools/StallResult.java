package mars.tools;

import java.util.List;

public class StallResult {
    public final int totalStalls;
    public final List<StallEvent> events;

    public StallResult(int totalStalls, List<StallEvent> events) {
        this.totalStalls = totalStalls;
        this.events = events;
    }
}
