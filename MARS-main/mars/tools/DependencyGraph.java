package mars.tools;

import java.util.*;

public class DependencyGraph {

    private List<Set<Integer>> edges;
    private int n;
    private List<MipsInstruction> instrs;

    public DependencyGraph(List<MipsInstruction> instrs) {
        this.instrs = instrs;
        n = instrs.size();
        edges = new ArrayList<>();
        for (int i = 0; i < n; i++) edges.add(new HashSet<>());

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (hasEdge(instrs.get(i), instrs.get(j), i, j)) {
                    edges.get(i).add(j);
                }
            }
        }
    }

    private boolean hasEdge(MipsInstruction a, MipsInstruction b,
                             int ai, int bi) {
        // Rule 1: nothing crosses a branch
        for (int k = ai; k < bi; k++) {
            if (instrs.get(k).isBranch) return true;
        }
        // Rule 2: memory instructions never reorder with each other
        if ((a.isMemLoad || a.isMemStore) &&
            (b.isMemLoad || b.isMemStore)) return true;

        // Rule 3a: RAW
        if (a.dest != -1) {
            for (int src : b.sources)
                if (src == a.dest) return true;
        }
        // Rule 3b: WAR
        if (b.dest != -1) {
            for (int src : a.sources)
                if (src == b.dest) return true;
        }
        // Rule 3c: WAW
        if (a.dest != -1 && b.dest != -1 && a.dest == b.dest)
            return true;

        return false;
    }

    public boolean mustFollow(int i, int j) {
        if (i < 0 || i >= n || j < 0 || j >= n) return false;
        return edges.get(i).contains(j);
    }

    public String getHazardType(MipsInstruction a, MipsInstruction b) {
        if (a.dest != -1)
            for (int src : b.sources)
                if (src == a.dest) return "RAW";
        if (b.dest != -1)
            for (int src : a.sources)
                if (src == b.dest) return "WAR";
        if (a.dest != -1 && b.dest != -1 && a.dest == b.dest)
            return "WAW";
        if (a.isBranch || b.isBranch) return "CTRL";
        if ((a.isMemLoad||a.isMemStore) &&
            (b.isMemLoad||b.isMemStore)) return "MEM";
        return "NONE";
    }

    public CriticalPathResult computeCriticalPath() {
        int[] pathLength = new int[n];
        List<Integer> topologicalOrder = topologicalSort();

        for (int t = topologicalOrder.size() - 1; t >= 0; t--) {
            int node = topologicalOrder.get(t);
            int bestSuccessorLength = 0;
            for (int successor : edges.get(node)) {
                bestSuccessorLength = Math.max(bestSuccessorLength,
                                               pathLength[successor]);
            }
            pathLength[node] = Scheduler.latencyOf(instrs.get(node)) +
                               bestSuccessorLength;
        }

        int start = -1;
        int minPossibleCycles = 0;
        for (int i = 0; i < n; i++) {
            if (pathLength[i] > minPossibleCycles) {
                minPossibleCycles = pathLength[i];
                start = i;
            }
        }

        List<Integer> criticalPath = new ArrayList<>();
        int current = start;
        while (current >= 0) {
            criticalPath.add(current);
            int next = -1;
            int best = -1;
            for (int successor : edges.get(current)) {
                if (pathLength[successor] > best) {
                    best = pathLength[successor];
                    next = successor;
                }
            }
            current = next;
        }

        double theoreticalMinCPI = n == 0
            ? 0.0
            : (double) minPossibleCycles / n;
        return new CriticalPathResult(pathLength, criticalPath,
                                      minPossibleCycles,
                                      theoreticalMinCPI);
    }

    private List<Integer> topologicalSort() {
        int[] indegree = new int[n];
        for (int i = 0; i < n; i++) {
            for (int successor : edges.get(i)) {
                indegree[successor]++;
            }
        }

        Queue<Integer> ready = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) ready.add(i);
        }

        List<Integer> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            int node = ready.remove();
            order.add(node);
            for (int successor : edges.get(node)) {
                indegree[successor]--;
                if (indegree[successor] == 0) ready.add(successor);
            }
        }

        if (order.size() != n) {
            order.clear();
            for (int i = 0; i < n; i++) order.add(i);
        }
        return order;
    }

    public int size() { return n; }
}
