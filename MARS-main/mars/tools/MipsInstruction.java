package mars.tools;

public class MipsInstruction {
    public int     index;
    public String  mnemonic;
    public int     dest;
    public int[]   sources;
    public boolean isMemLoad;
    public boolean isMemStore;
    public boolean isBranch;
    public String  originalText;
    public int     sourceLine;
    public int     address;
    public int     branchTargetAddress;

    public MipsInstruction(int index, String mnemonic,
                           int dest, int[] sources,
                           boolean isLoad, boolean isStore,
                           boolean isBranch, String text) {
        this.index        = index;
        this.mnemonic     = mnemonic;
        this.dest         = dest;
        this.sources      = sources;
        this.isMemLoad    = isLoad;
        this.isMemStore   = isStore;
        this.isBranch     = isBranch;
        this.originalText = text;
        this.sourceLine    = -1;
        this.address       = -1;
        this.branchTargetAddress = -1;
    }
}
