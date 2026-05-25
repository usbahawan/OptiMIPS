package mars.tools;

import java.util.*;

public class Scheduler {

    private static final Map<String,Integer> LATENCY = new HashMap<>();
    static {
        LATENCY.put("lw",2); LATENCY.put("lb",2); LATENCY.put("lh",2);
        LATENCY.put("lbu",2);LATENCY.put("lhu",2);LATENCY.put("ll",2);
        LATENCY.put("add",2);LATENCY.put("sub",2);LATENCY.put("and",2);
        LATENCY.put("or",2); LATENCY.put("xor",2);LATENCY.put("nor",2);
        LATENCY.put("mul",2);LATENCY.put("div",2);LATENCY.put("slt",2);
        LATENCY.put("addi",2);LATENCY.put("addiu",2);LATENCY.put("andi",2);
        LATENCY.put("ori",2);LATENCY.put("xori",2);LATENCY.put("slti",2);
        LATENCY.put("addu",2);LATENCY.put("subu",2);LATENCY.put("sw",1);
    }

    private List<MipsInstruction> original;
    private DependencyGraph       graph;

    public Scheduler(List<MipsInstruction> instrs, DependencyGraph graph) {
        this.original = instrs;
        this.graph    = graph;
    }

    public List<MipsInstruction> schedule() {
        int n = original.size();
        boolean[] scheduled = new boolean[n];
        List<MipsInstruction> result = new ArrayList<>();
        int[] ready = new int[32];
        Arrays.fill(ready, 0);
        int cycle = 0;

        while (result.size() < n) {
            int best = -1;
            int bestStall = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (scheduled[i]) continue;
                if (allPredecessorsScheduled(i, scheduled, n)) {
                    int stall = addedStall(original.get(i), cycle, ready);
                    if (best == -1 ||
                        stall < bestStall ||
                        (stall == bestStall &&
                         successorCount(i) > successorCount(best))) {
                        best = i;
                        bestStall = stall;
                    }
                }
            }
            if (best == -1) {
                // safety: pick first unscheduled
                for (int i = 0; i < n; i++) {
                    if (!scheduled[i]) { best = i; break; }
                }
            }
            scheduled[best] = true;
            MipsInstruction chosen = original.get(best);
            cycle++;
            int waitUntil = cycle;
            for (int src : chosen.sources) {
                if (src >= 0 && src < 32)
                    waitUntil = Math.max(waitUntil, ready[src]);
            }
            cycle = waitUntil;
            if (chosen.dest >= 0 && chosen.dest < 32) {
                int lat = LATENCY.getOrDefault(chosen.mnemonic, 1);
                ready[chosen.dest] = cycle + lat;
            }
            result.add(chosen);
        }
        return result;
    }

    private int addedStall(MipsInstruction instr, int cycle, int[] ready) {
        int issueCycle = cycle + 1;
        int waitUntil = issueCycle;
        for (int src : instr.sources) {
            if (src >= 0 && src < 32)
                waitUntil = Math.max(waitUntil, ready[src]);
        }
        return waitUntil - issueCycle;
    }

    private boolean allPredecessorsScheduled(int j, boolean[] scheduled, int n) {
        for (int i = 0; i < n; i++) {
            if (i == j) continue;
            if (graph.mustFollow(i, j) && !scheduled[i]) return false;
        }
        return true;
    }

    private int successorCount(int i) {
        int count = 0;
        for (int j = 0; j < original.size(); j++)
            if (graph.mustFollow(i, j)) count++;
        return count;
    }

    public static StallResult countStalls(List<MipsInstruction> instrs) {
        return countStalls(instrs, true);
    }

    public static StallResult countStalls(List<MipsInstruction> instrs,
                                          boolean forwardingEnabled) {
        if (forwardingEnabled) {
            return countForwardingStalls(instrs);
        }

        int stalls = 0;
        List<StallEvent> events = new ArrayList<>();
        for (int i = 0; i < instrs.size(); i++) {
            MipsInstruction producer = instrs.get(i);
            if (producer.dest < 0) continue;
            int lat = LATENCY.getOrDefault(producer.mnemonic, 1);
            int rawCost = Math.max(0, lat - 1);
            for (int j = i + 1; j < instrs.size(); j++) {
                MipsInstruction consumer = instrs.get(j);
                if (rawCost > 0 && usesRegister(consumer, producer.dest)) {
                    stalls += rawCost;
                    events.add(new StallEvent(
                        producer.index, consumer.index, rawCost, "RAW",
                        instructionText(producer), instructionText(consumer)));
                }
            }
        }
        return new StallResult(stalls, events);
    }

    private static StallResult countForwardingStalls(
            List<MipsInstruction> instrs) {
        int stalls = 0;
        List<StallEvent> events = new ArrayList<>();
        for (int i = 0; i < instrs.size() - 1; i++) {
            MipsInstruction producer = instrs.get(i);
            MipsInstruction consumer = instrs.get(i + 1);
            if (producer.dest >= 0 &&
                isStrictLoad(producer) &&
                usesRegister(consumer, producer.dest)) {
                stalls++;
                events.add(new StallEvent(
                    producer.index, consumer.index, 1, "load-use",
                    instructionText(producer), instructionText(consumer)));
            }
        }
        return new StallResult(stalls, events);
    }

    private static boolean usesRegister(MipsInstruction instr, int register) {
        for (int src : instr.sources) {
            if (src == register) return true;
        }
        return false;
    }

    private static boolean isStrictLoad(MipsInstruction instr) {
        return "lw".equals(instr.mnemonic) ||
               "lb".equals(instr.mnemonic) ||
               "lh".equals(instr.mnemonic);
    }

    public static int latencyOf(MipsInstruction instr) {
        return LATENCY.getOrDefault(instr.mnemonic, 1);
    }

    private static String instructionText(MipsInstruction instr) {
        return instr.originalText != null
            ? instr.originalText.trim()
            : instr.mnemonic;
    }

    public static double calcCPI(int instrCount, int stalls) {
        if (instrCount == 0) return 0;
        return (double)(instrCount + stalls) / instrCount;
    }
}
