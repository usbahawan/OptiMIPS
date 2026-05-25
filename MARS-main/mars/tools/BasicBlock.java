package mars.tools;

import java.util.List;

public class BasicBlock {
    public final int startIndex;
    public final int endIndex;
    public final List<MipsInstruction> instructions;

    public BasicBlock(int startIndex, int endIndex,
                      List<MipsInstruction> instructions) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.instructions = instructions;
    }
}
