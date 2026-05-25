package mars.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.HashMap;

public class BasicBlockSplitter {

    public List<BasicBlock> split(List<MipsInstruction> instrs) {
        List<BasicBlock> blocks = new ArrayList<>();
        int n = instrs.size();
        if (n == 0) return blocks;

        Set<Integer> leaders = findLeaders(instrs);
        List<Integer> leaderList = new ArrayList<>(leaders);
        for (int i = 0; i < leaderList.size(); i++) {
            int start = leaderList.get(i);
            int end = i + 1 < leaderList.size()
                ? leaderList.get(i + 1) - 1
                : n - 1;
            blocks.add(new BasicBlock(start, end,
                new ArrayList<>(instrs.subList(start, end + 1))));
        }
        return blocks;
    }

    public List<MipsInstruction> scheduleBlocks(List<BasicBlock> blocks) {
        List<MipsInstruction> optimized = new ArrayList<>();
        for (BasicBlock block : blocks) {
            optimized.addAll(scheduleOneBlock(block.instructions));
        }
        return optimized;
    }

    private List<MipsInstruction> scheduleOneBlock(
            List<MipsInstruction> instructions) {
        DependencyGraph graph = new DependencyGraph(instructions);
        Scheduler scheduler = new Scheduler(instructions, graph);
        return scheduler.schedule();
    }

    public Set<Integer> blockStartPositions(List<BasicBlock> blocks) {
        Set<Integer> starts = new TreeSet<>();
        int pos = 0;
        for (BasicBlock block : blocks) {
            starts.add(pos);
            pos += block.instructions.size();
        }
        return starts;
    }

    private Set<Integer> findLeaders(List<MipsInstruction> instrs) {
        Set<Integer> leaders = new TreeSet<>();
        Map<String,Integer> labels = collectLabels(instrs);
        Map<Integer,Integer> addresses = collectAddresses(instrs);
        leaders.add(0);

        for (int i = 0; i < instrs.size(); i++) {
            MipsInstruction instr = instrs.get(i);
            if (hasLeadingLabel(instr)) leaders.add(i);

            if (instr.isBranch) {
                if (i + 1 < instrs.size()) leaders.add(i + 1);
                String target = branchTargetLabel(instr);
                if (target != null && labels.containsKey(target)) {
                    leaders.add(labels.get(target));
                }
                if (addresses.containsKey(
                        Integer.valueOf(instr.branchTargetAddress))) {
                    leaders.add(addresses.get(
                        Integer.valueOf(instr.branchTargetAddress)));
                }
            }
        }
        return leaders;
    }

    private Map<Integer,Integer> collectAddresses(List<MipsInstruction> instrs) {
        Map<Integer,Integer> addresses = new HashMap<>();
        for (int i = 0; i < instrs.size(); i++) {
            int address = instrs.get(i).address;
            if (address >= 0) addresses.put(Integer.valueOf(address),
                                            Integer.valueOf(i));
        }
        return addresses;
    }

    private Map<String,Integer> collectLabels(List<MipsInstruction> instrs) {
        Map<String,Integer> labels = new HashMap<>();
        for (int i = 0; i < instrs.size(); i++) {
            List<String> names = leadingLabels(instrs.get(i));
            for (String name : names) {
                labels.put(name, Integer.valueOf(i));
            }
        }
        return labels;
    }

    private boolean hasLeadingLabel(MipsInstruction instr) {
        return !leadingLabels(instr).isEmpty();
    }

    private List<String> leadingLabels(MipsInstruction instr) {
        List<String> labels = new ArrayList<>();
        String text = instr.originalText == null ? "" : instr.originalText;
        String code = stripComment(text).trim();

        while (true) {
            int colon = code.indexOf(':');
            if (colon <= 0) break;
            String candidate = code.substring(0, colon).trim();
            if (!isSimpleLabel(candidate)) break;
            labels.add(candidate);
            code = code.substring(colon + 1).trim();
        }
        return labels;
    }

    private String branchTargetLabel(MipsInstruction instr) {
        String text = instr.originalText == null ? "" : instr.originalText;
        String code = stripComment(text).trim();
        while (true) {
            int colon = code.indexOf(':');
            if (colon <= 0) break;
            String candidate = code.substring(0, colon).trim();
            if (!isSimpleLabel(candidate)) break;
            code = code.substring(colon + 1).trim();
        }

        String[] parts = code.split("[,\\s()]+");
        for (int i = parts.length - 1; i >= 1; i--) {
            String token = parts[i].trim();
            if (isSimpleLabel(token) && !isRegisterName(token)) {
                return token;
            }
        }
        return null;
    }

    private String stripComment(String text) {
        int hash = text.indexOf('#');
        return hash >= 0 ? text.substring(0, hash) : text;
    }

    private boolean isSimpleLabel(String s) {
        if (s == null || s.length() == 0) return false;
        char first = s.charAt(0);
        if (!Character.isLetter(first) && first != '_' && first != '.')
            return false;
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '.')
                return false;
        }
        return true;
    }

    private boolean isRegisterName(String token) {
        return token.startsWith("$");
    }
}
