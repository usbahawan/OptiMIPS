package mars.tools;

import java.util.List;

public class CriticalPathResult {
    public final int[] pathLength;
    public final List<Integer> criticalPath;
    public final int minPossibleCycles;
    public final double theoreticalMinCPI;

    public CriticalPathResult(int[] pathLength,
                              List<Integer> criticalPath,
                              int minPossibleCycles,
                              double theoreticalMinCPI) {
        this.pathLength = pathLength;
        this.criticalPath = criticalPath;
        this.minPossibleCycles = minPossibleCycles;
        this.theoreticalMinCPI = theoreticalMinCPI;
    }
}
