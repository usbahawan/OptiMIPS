package mars.tools;

import java.util.List;

public class RegisterPressureAnalyzer {
    public int[] analyze(List<MipsInstruction> instrs, DependencyGraph graph) {
        int n = instrs.size();
        int[] pressure = new int[n];

        for (int i = 0; i < n; i++) {
            int live = 0;
            for (int r = 0; r < 32; r++) {
                if (hasPriorWrite(instrs, i, r) &&
                    hasFutureRead(instrs, i, r)) {
                    live++;
                }
            }
            pressure[i] = live;
        }

        return pressure;
    }

    private boolean hasPriorWrite(List<MipsInstruction> instrs,
                                  int position, int register) {
        for (int j = 0; j < position; j++) {
            if (instrs.get(j).dest == register) return true;
        }
        return false;
    }

    private boolean hasFutureRead(List<MipsInstruction> instrs,
                                  int position, int register) {
        for (int k = position; k < instrs.size(); k++) {
            for (int src : instrs.get(k).sources) {
                if (src == register) return true;
            }
        }
        return false;
    }
}
