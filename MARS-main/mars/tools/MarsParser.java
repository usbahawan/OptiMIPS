package mars.tools;

import mars.Globals;
import mars.ProgramStatement;
import mars.mips.instructions.Instruction;
import java.util.*;

public class MarsParser {

    private static final Set<String> R_TYPE = new HashSet<>(Arrays.asList(
        "add","sub","and","or","xor","nor","slt","sltu",
        "mul","div","sll","srl","sra","addu","subu","multu","divu"
    ));
    private static final Set<String> I_TYPE = new HashSet<>(Arrays.asList(
        "addi","addiu","andi","ori","xori","slti","sltiu","lui"
    ));
    private static final Set<String> LOADS = new HashSet<>(Arrays.asList(
        "lw","lb","lbu","lh","lhu","ll"
    ));
    private static final Set<String> STORES = new HashSet<>(Arrays.asList(
        "sw","sb","sh","sc"
    ));
    private static final Set<String> BRANCHES = new HashSet<>(Arrays.asList(
        "beq","bne","blez","bgtz","bltz","bgez","bgezal","bltzal",
        "j","jal","jr","jalr"
    ));

    public List<MipsInstruction> parse() {
        List<MipsInstruction> result = new ArrayList<>();
        try {
            ArrayList stmts = Globals.program.getMachineList();
            int idx = 0;
            for (Object obj : stmts) {
                ProgramStatement stmt = (ProgramStatement) obj;
                String mn  = stmt.getInstruction().getName()
                                 .toLowerCase().trim();
                int[]  op  = stmt.getOperands();
                String txt = stmt.getSource();
                if (txt == null) txt = mn;

                int     dest    = -1;
                int[]   sources = new int[0];
                boolean isLoad  = LOADS.contains(mn);
                boolean isStore = STORES.contains(mn);
                boolean isBr    = BRANCHES.contains(mn);

                if (op != null) {
                    if (R_TYPE.contains(mn) && op.length >= 3) {
                        dest    = op[0];
                        sources = new int[]{op[1], op[2]};
                    } else if (I_TYPE.contains(mn) && op.length >= 2) {
                        dest    = op[0];
                        sources = new int[]{op[1]};
                    } else if (isLoad && op.length >= 2) {
                        dest    = op[0];
                        sources = op.length >= 3
                            ? new int[]{op[2]} : new int[]{op[1]};
                    } else if (isStore && op.length >= 2) {
                        dest    = -1;
                        sources = op.length >= 3
                            ? new int[]{op[0], op[2]}
                            : new int[]{op[0]};
                    } else if (isBr && op.length >= 2) {
                        dest    = -1;
                        sources = new int[]{op[0], op[1]};
                    } else if (op.length >= 1) {
                        dest    = op[0];
                        sources = op.length >= 2
                            ? new int[]{op[1]} : new int[0];
                    }
                }

                MipsInstruction parsed = new MipsInstruction(
                    idx++, mn, dest, sources,
                    isLoad, isStore, isBr, txt);
                parsed.sourceLine = stmt.getSourceLine();
                parsed.address = stmt.getAddress();
                parsed.branchTargetAddress = branchTargetAddress(mn, op,
                                                                  parsed.address);
                result.add(parsed);
            }
        } catch (Exception e) {
            System.err.println("MarsParser error: " + e.getMessage());
        }
        return result;
    }

    private int branchTargetAddress(String mn, int[] op, int address) {
        if (op == null || op.length == 0) return -1;
        if ("j".equals(mn) || "jal".equals(mn)) {
            return op[0] << 2;
        }
        if ("jr".equals(mn) || "jalr".equals(mn)) {
            return -1;
        }
        if (BRANCHES.contains(mn)) {
            int offset = op[op.length - 1];
            return address + 4 + (offset << 2);
        }
        return -1;
    }
}
