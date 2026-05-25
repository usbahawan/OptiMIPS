package mars.tools;

import mars.mips.hardware.*;
import mars.Globals;
import mars.venus.EditPane;
import mars.venus.FileStatus;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Set;

public class OptiMIPS extends AbstractMarsToolAndApplication {

    private static final long serialVersionUID = 1L;

    private JTable    origTable, optTable;
    private JTable    origPressureTable, optPressureTable;
    private JTable    explanationTable;
    private JTable    stallProfileTable;
    private JLabel    origCpiLabel, optCpiLabel, gainLabel;
    private JLabel    stallSummaryLabel;
    private JTextArea hazardLog;
    private JButton   optimizeBtn;
    private JButton   exportBtn;
    private JButton   applyBtn;
    private JButton   reportBtn;
    private JToggleButton forwardingToggle;
    private JLabel    hardwareModeLabel;
    private JLabel    criticalPathLabel;
    private JLabel    registerPressureLabel;
    private JLabel    blockSummaryLabel;
    private List<MipsInstruction> lastOriginal;
    private List<MipsInstruction> lastOptimized;
    private DependencyGraph lastGraph;
    private CriticalPathResult lastCriticalPath;
    private StallResult lastOriginalStallResult;
    private StallResult lastOptimizedStallResult;
    private int[] lastOriginalPressure;
    private int[] lastOptimizedPressure;
    private List<BasicBlock> lastBlocks;
    private Set<Integer> lastOriginalBlockStarts;
    private Set<Integer> lastOptimizedBlockStarts;
    private double lastBeforeCpi;
    private double lastAfterCpi;
    private int lastBeforeStalls;
    private int lastAfterStalls;

    public OptiMIPS() {
        super("OptiMIPS — Pipeline Optimizer", "OptiMIPS");
    }

    public String getName() {
        return "OptiMIPS — Pipeline Optimizer";
    }

    @Override
    protected JComponent buildMainDisplayArea() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ── toolbar ──────────────────────────────────────────────────
        optimizeBtn = new JButton("▶  Optimize");
        optimizeBtn.setFont(new Font("Arial", Font.BOLD, 13));
        optimizeBtn.setBackground(new Color(46, 95, 163));
        optimizeBtn.setForeground(Color.WHITE);
        optimizeBtn.setOpaque(true);
        optimizeBtn.setBorderPainted(false);
        optimizeBtn.addActionListener(e -> runOptimizer());

        exportBtn = new JButton("Export Optimized");
        exportBtn.setFont(new Font("Arial", Font.BOLD, 12));
        exportBtn.setEnabled(false);
        exportBtn.addActionListener(e -> exportOptimizedProgram());

        applyBtn = new JButton("Apply to Editor");
        applyBtn.setFont(new Font("Arial", Font.BOLD, 12));
        applyBtn.setEnabled(false);
        applyBtn.addActionListener(e -> applyOptimizedToEditor());

        reportBtn = new JButton("Save Report");
        reportBtn.setFont(new Font("Arial", Font.BOLD, 12));
        reportBtn.setEnabled(false);
        reportBtn.addActionListener(e -> saveOptimizationReport());

        forwardingToggle = new JToggleButton("Forwarding: ON", true);
        forwardingToggle.setFont(new Font("Arial", Font.BOLD, 12));
        forwardingToggle.setForeground(new Color(14, 100, 70));
        forwardingToggle.addActionListener(e -> {
            updateForwardingToggleDisplay();
            refreshCpiStats(true);
        });

        JLabel title = new JLabel("  OptiMIPS — Pipeline-Aware Code Optimizer");
        title.setFont(new Font("Arial", Font.BOLD, 15));
        title.setForeground(new Color(31, 61, 107));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 5, 6, 0));
        buttonPanel.add(forwardingToggle);
        buttonPanel.add(optimizeBtn);
        buttonPanel.add(applyBtn);
        buttonPanel.add(exportBtn);
        buttonPanel.add(reportBtn);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.add(title, BorderLayout.WEST);
        toolbar.add(buttonPanel, BorderLayout.EAST);
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
        root.add(toolbar, BorderLayout.NORTH);

        // ── code tables ──────────────────────────────────────────────
        origTable = makeTable();
        optTable  = makeTable();
        origPressureTable = makePressureTable();
        optPressureTable = makePressureTable();

        JPanel codePanel = new JPanel(new GridLayout(1, 2, 10, 0));
        codePanel.add(wrapTable(origTable, origPressureTable,
                                "Original Code",
                                new Color(255, 235, 235)));
        codePanel.add(wrapTable(optTable, optPressureTable,
                                "Optimized Code",
                                new Color(232, 248, 232)));

        // ── bottom stats + log ───────────────────────────────────────
        origCpiLabel = new JLabel("CPI: —");
        optCpiLabel  = new JLabel("CPI: —");
        gainLabel    = new JLabel("Run optimizer to see results");
        gainLabel.setFont(new Font("Arial", Font.BOLD, 13));
        gainLabel.setForeground(new Color(14, 100, 70));

        JPanel statsRow = new JPanel(new GridLayout(1, 3, 8, 0));
        statsRow.add(makeStatPanel("Before", origCpiLabel,
                                   new Color(254, 226, 226)));
        statsRow.add(makeStatPanel("After",  optCpiLabel,
                                   new Color(220, 252, 231)));
        statsRow.add(makeStatPanel("Gain",   gainLabel,
                                   new Color(237, 233, 254)));

        hardwareModeLabel = new JLabel("Hardware mode: Forwarding ON");
        hardwareModeLabel.setFont(new Font("Arial", Font.BOLD, 12));
        JPanel hardwareModePanel = makeInfoPanel(hardwareModeLabel,
                                                 new Color(219, 234, 254));

        criticalPathLabel = new JLabel(
            "<html>Critical path: —<br>Theoretical minimum CPI: —</html>");
        criticalPathLabel.setFont(new Font("Arial", Font.BOLD, 12));
        JPanel criticalPathPanel = makeInfoPanel(criticalPathLabel,
                                                 new Color(255, 247, 237));

        registerPressureLabel = new JLabel(
            "<html>Peak register pressure (before): —<br>" +
            "Peak register pressure (after): —</html>");
        registerPressureLabel.setFont(new Font("Arial", Font.BOLD, 12));
        JPanel registerPressurePanel = makeInfoPanel(registerPressureLabel,
                                                     new Color(240, 253, 244));

        blockSummaryLabel = new JLabel(
            "<html>Basic blocks: —<br>Per-block CPI contribution: —</html>");
        blockSummaryLabel.setFont(new Font("Arial", Font.BOLD, 12));
        JPanel blockSummaryPanel = makeInfoPanel(blockSummaryLabel,
                                                 new Color(245, 243, 255));

        JPanel detailPanel = new JPanel(new GridLayout(2, 2, 4, 4));
        detailPanel.add(hardwareModePanel);
        detailPanel.add(criticalPathPanel);
        detailPanel.add(registerPressurePanel);
        detailPanel.add(blockSummaryPanel);

        JPanel statsPanel = new JPanel(new BorderLayout(0, 4));
        statsPanel.add(statsRow, BorderLayout.NORTH);
        statsPanel.add(detailPanel, BorderLayout.CENTER);

        hazardLog = new JTextArea(6, 50);
        hazardLog.setFont(new Font("Courier New", Font.PLAIN, 11));
        hazardLog.setEditable(false);
        hazardLog.setBackground(new Color(248, 250, 255));
        hazardLog.setText("Assemble your MIPS program, then click Optimize.");

        explanationTable = makeExplanationTable();
        stallProfileTable = makeStallProfileTable();
        stallSummaryLabel = new JLabel(
            "Run optimizer to see stall elimination details.");
        stallSummaryLabel.setFont(new Font("Arial", Font.BOLD, 12));
        stallSummaryLabel.setForeground(new Color(80, 80, 80));

        JPanel stallProfilePanel = new JPanel(new BorderLayout(0, 6));
        stallProfilePanel.add(new JScrollPane(stallProfileTable),
                              BorderLayout.CENTER);
        stallProfilePanel.add(stallSummaryLabel, BorderLayout.SOUTH);

        JTabbedPane analysisTabs = new JTabbedPane();
        analysisTabs.addTab("Hazard Log", new JScrollPane(hazardLog));
        analysisTabs.addTab("Easy Explanation",
                            new JScrollPane(explanationTable));
        analysisTabs.addTab("Stall Profile", stallProfilePanel);
        analysisTabs.setPreferredSize(new Dimension(400, 190));
        analysisTabs.setMinimumSize(new Dimension(400, 150));

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        bottom.add(statsPanel, BorderLayout.NORTH);
        bottom.add(analysisTabs, BorderLayout.CENTER);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                                              codePanel, bottom);
        mainSplit.setResizeWeight(0.42);
        mainSplit.setDividerLocation(0.42);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setBorder(null);
        codePanel.setMinimumSize(new Dimension(400, 160));
        bottom.setMinimumSize(new Dimension(400, 360));
        root.add(mainSplit, BorderLayout.CENTER);

        return root;
    }

    // ── core optimizer logic ─────────────────────────────────────────
    private void runOptimizer() {
        hazardLog.setText("");
        exportBtn.setEnabled(false);
        applyBtn.setEnabled(false);
        reportBtn.setEnabled(false);

        MarsParser parser = new MarsParser();
        List<MipsInstruction> original = parser.parse();

        if (original.isEmpty()) {
            hazardLog.setText(
                "No assembled program found.\n" +
                "Write your MIPS code, press F3 to assemble, then click Optimize.");
            lastOriginal = null;
            lastOptimized = null;
            lastGraph = null;
            lastCriticalPath = null;
            lastOriginalStallResult = null;
            lastOptimizedStallResult = null;
            lastOriginalPressure = null;
            lastOptimizedPressure = null;
            lastBlocks = null;
            lastOriginalBlockStarts = null;
            lastOptimizedBlockStarts = null;
            clearStallProfile();
            origPressureTable.putClientProperty("blockStarts", null);
            optPressureTable.putClientProperty("blockStarts", null);
            populatePressureTable(origPressureTable, null);
            populatePressureTable(optPressureTable, null);
            updateRegisterPressureLabel();
            updateBlockSummaryLabel();
            return;
        }

        lastOriginal  = original;
        lastGraph     = new DependencyGraph(original);
        lastCriticalPath = lastGraph.computeCriticalPath();
        BasicBlockSplitter blockSplitter = new BasicBlockSplitter();
        lastBlocks = blockSplitter.split(original);
        lastOriginalBlockStarts = blockSplitter.blockStartPositions(lastBlocks);
        lastOptimized = blockSplitter.scheduleBlocks(lastBlocks);
        lastOptimizedBlockStarts = blockSplitter.blockStartPositions(lastBlocks);
        RegisterPressureAnalyzer pressureAnalyzer =
            new RegisterPressureAnalyzer();
        lastOriginalPressure = pressureAnalyzer.analyze(original, lastGraph);
        lastOptimizedPressure = pressureAnalyzer.analyze(
            lastOptimized, new DependencyGraph(lastOptimized));

        refreshCpiStats(false);
        double gain = lastBeforeCpi > 0
            ? ((lastBeforeCpi - lastAfterCpi) / lastBeforeCpi) * 100.0
            : 0.0;

        populateTable(origTable, original,      true);
        populateTable(optTable,  lastOptimized, false);
        origPressureTable.putClientProperty("blockStarts",
                                            lastOriginalBlockStarts);
        optPressureTable.putClientProperty("blockStarts",
                                           lastOptimizedBlockStarts);
        populatePressureTable(origPressureTable, lastOriginalPressure);
        populatePressureTable(optPressureTable, lastOptimizedPressure);
        updateRegisterPressureLabel();
        buildHazardLog(original);
        populateExplanationTable(original, lastOptimized, gain);
        exportBtn.setEnabled(true);
        applyBtn.setEnabled(true);
        reportBtn.setEnabled(true);
    }

    private void updateForwardingToggleDisplay() {
        if (forwardingToggle.isSelected()) {
            forwardingToggle.setText("Forwarding: ON");
            forwardingToggle.setForeground(new Color(14, 100, 70));
            hardwareModeLabel.setText("Hardware mode: Forwarding ON");
        } else {
            forwardingToggle.setText("Forwarding: OFF");
            forwardingToggle.setForeground(Color.RED);
            hardwareModeLabel.setText("Hardware mode: Stall-only");
        }
    }

    private void refreshCpiStats(boolean refreshExplanation) {
        updateForwardingToggleDisplay();
        if (lastOriginal == null || lastOptimized == null) return;

        boolean forwardingEnabled = forwardingToggle.isSelected();
        StallResult before = Scheduler.countStalls(lastOriginal,
                                                   forwardingEnabled);
        StallResult after = Scheduler.countStalls(lastOptimized,
                                                  forwardingEnabled);
        lastOriginalStallResult = before;
        lastOptimizedStallResult = after;
        int sb = before.totalStalls;
        int sa = after.totalStalls;
        double cb = Scheduler.calcCPI(lastOriginal.size(), sb);
        double ca = Scheduler.calcCPI(lastOptimized.size(), sa);
        double gain = cb > 0 ? ((cb - ca) / cb) * 100.0 : 0.0;

        lastBeforeStalls = sb;
        lastAfterStalls = sa;
        lastBeforeCpi = cb;
        lastAfterCpi = ca;

        origCpiLabel.setText(String.format("CPI: %.3f  (%d stalls)", cb, sb));
        optCpiLabel.setText(String.format("CPI: %.3f  (%d stalls)", ca, sa));

        if (gain > 0.5)
            gainLabel.setText(String.format("%.1f%% CPI improvement", gain));
        else
            gainLabel.setText("Already optimal — no reordering possible");

        updateCriticalPathLabel(ca);
        populateStallProfile();
        updateBlockSummaryLabel();

        if (refreshExplanation) {
            populateExplanationTable(lastOriginal, lastOptimized, gain);
        }
    }

    private void updateCriticalPathLabel(double optimizedCpi) {
        if (lastCriticalPath == null || lastOriginal == null ||
            lastOriginal.isEmpty()) {
            criticalPathLabel.setText(
                "<html>Critical path: —<br>Theoretical minimum CPI: —</html>");
            return;
        }

        double minCpi = lastCriticalPath.theoreticalMinCPI;
        double gap = optimizedCpi - minCpi;
        String status;
        if (Math.abs(gap) < 0.0005) {
            status = "<font color='#15803D'>Optimal schedule achieved!</font>";
        } else {
            status = "<font color='#C2410C'>Scheduling gap: +" +
                     String.format("%.3f", Math.max(0.0, gap)) +
                     " CPI above minimum</font>";
        }

        criticalPathLabel.setText(
            "<html>Critical path: " +
            lastCriticalPath.criticalPath.size() + " instructions, " +
            lastCriticalPath.minPossibleCycles + " cycles minimum<br>" +
            "Theoretical minimum CPI: " +
            String.format("%.3f", minCpi) + "<br>" +
            status + "</html>");
    }

    private void populateStallProfile() {
        DefaultTableModel m =
            (DefaultTableModel) stallProfileTable.getModel();
        m.setRowCount(0);

        if (lastOriginalStallResult == null ||
            lastOptimizedStallResult == null) {
            stallSummaryLabel.setText(
                "Run optimizer to see stall elimination details.");
            return;
        }

        List<StallEvent> events =
            new ArrayList<>(lastOriginalStallResult.events);
        Collections.sort(events, new Comparator<StallEvent>() {
            public int compare(StallEvent a, StallEvent b) {
                return b.stallCycles - a.stallCycles;
            }
        });

        Set<String> remainingPairs = new HashSet<>();
        for (StallEvent event : lastOptimizedStallResult.events) {
            remainingPairs.add(stallPairKey(event));
        }

        int eliminated = 0;
        int total = Math.max(1, lastOriginalStallResult.totalStalls);
        for (int i = 0; i < events.size(); i++) {
            StallEvent event = events.get(i);
            boolean isEliminated =
                !remainingPairs.contains(stallPairKey(event));
            if (isEliminated) eliminated++;
            double pct = (event.stallCycles * 100.0) / total;
            m.addRow(new Object[]{
                Integer.valueOf(i + 1),
                event.producerText,
                event.consumerText,
                event.hazardType,
                Integer.valueOf(event.stallCycles),
                String.format("%.1f%%", pct),
                isEliminated ? "YES" : "NO"
            });
        }

        stallSummaryLabel.setText(
            eliminated + " of " + events.size() +
            " stall pairs eliminated by optimizer. " +
            lastOptimizedStallResult.totalStalls +
            " stall cycles remain.");
    }

    private void clearStallProfile() {
        if (stallProfileTable != null) {
            ((DefaultTableModel) stallProfileTable.getModel()).setRowCount(0);
        }
        if (stallSummaryLabel != null) {
            stallSummaryLabel.setText(
                "Run optimizer to see stall elimination details.");
        }
    }

    private String stallPairKey(StallEvent event) {
        return event.producerIndex + ":" + event.consumerIndex;
    }

    private void updateRegisterPressureLabel() {
        if (registerPressureLabel == null) return;
        if (lastOriginalPressure == null || lastOptimizedPressure == null) {
            registerPressureLabel.setText(
                "<html>Peak register pressure (before): —<br>" +
                "Peak register pressure (after): —</html>");
            registerPressureLabel.setForeground(new Color(30, 64, 175));
            return;
        }

        int before = peakPressure(lastOriginalPressure);
        int after = peakPressure(lastOptimizedPressure);
        String status;
        if (after > before) {
            status = "<font color='#C2410C'>Warning: optimization " +
                     "increased register pressure</font>";
        } else {
            status = "<font color='#15803D'>Register pressure maintained " +
                     "or reduced</font>";
        }

        registerPressureLabel.setText(
            "<html>Peak register pressure (before): " + before + "<br>" +
            "Peak register pressure (after):&nbsp;&nbsp;" + after + "<br>" +
            status + "</html>");
    }

    private int peakPressure(int[] pressure) {
        int peak = 0;
        for (int value : pressure) {
            peak = Math.max(peak, value);
        }
        return peak;
    }

    private void updateBlockSummaryLabel() {
        if (blockSummaryLabel == null) return;
        if (lastBlocks == null || lastBlocks.isEmpty()) {
            blockSummaryLabel.setText(
                "<html>Basic blocks: —<br>Per-block CPI contribution: —</html>");
            return;
        }

        boolean forwardingEnabled = forwardingToggle.isSelected();
        StringBuilder sb = new StringBuilder();
        sb.append("<html>Basic blocks: ").append(lastBlocks.size())
          .append("<br>Per-block CPI contribution: ");
        int offset = 0;
        for (int i = 0; i < lastBlocks.size(); i++) {
            BasicBlock block = lastBlocks.get(i);
            StallResult before = Scheduler.countStalls(
                block.instructions, forwardingEnabled);
            List<MipsInstruction> optimizedBlock = lastOptimized.subList(
                offset, offset + block.instructions.size());
            StallResult after = Scheduler.countStalls(optimizedBlock,
                                                      forwardingEnabled);
            double beforeCpi = Scheduler.calcCPI(block.instructions.size(),
                                                 before.totalStalls);
            double afterCpi = Scheduler.calcCPI(block.instructions.size(),
                                                after.totalStalls);
            if (i > 0) sb.append(", ");
            sb.append("B").append(i + 1).append("=")
              .append(String.format("%.3f", beforeCpi))
              .append("→")
              .append(String.format("%.3f", afterCpi));
            offset += block.instructions.size();
            if (i == 4 && lastBlocks.size() > 5) {
                sb.append(", ...");
                break;
            }
        }
        sb.append("</html>");
        blockSummaryLabel.setText(sb.toString());
    }

    private void buildHazardLog(List<MipsInstruction> instrs) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Hazard Analysis ===\n\n");
        boolean any = false;
        for (int i = 0; i < instrs.size(); i++) {
            for (int j = i + 1; j < instrs.size(); j++) {
                if (lastGraph.mustFollow(
                        instrs.get(i).index, instrs.get(j).index) ||
                    lastGraph.mustFollow(i, j)) {
                    String type = lastGraph.getHazardType(
                        instrs.get(i), instrs.get(j));
                    sb.append(String.format(
                        "  Line %d → Line %d  [%s]   %s  →  %s\n",
                        i+1, j+1, type,
                        instrs.get(i).originalText.trim(),
                        instrs.get(j).originalText.trim()));
                    any = true;
                }
            }
        }
        if (!any) sb.append("  No hazards detected in original program.\n");
        sb.append("\n=== Instruction Moves ===\n\n");
        for (int i = 0; i < lastOptimized.size(); i++) {
            int origIdx = lastOptimized.get(i).index;
            if (origIdx != i) {
                sb.append(String.format(
                    "  [%s]  moved from line %d to line %d\n",
                    lastOptimized.get(i).mnemonic, origIdx+1, i+1));
            }
        }
        hazardLog.setText(sb.toString());
    }

    private void populateExplanationTable(List<MipsInstruction> original,
                                          List<MipsInstruction> optimized,
                                          double gain) {
        DefaultTableModel m =
            (DefaultTableModel) explanationTable.getModel();
        m.setRowCount(0);

        addExplanationRow(m, "What was lacking",
            "The original order sometimes used a register before the pipeline had its value ready, so the CPU had to wait.");
        addExplanationRow(m, "Safety rule",
            "OptiMIPS built a dependency graph first, so it did not break RAW, WAR, WAW, memory, or branch order.");

        boolean moved = false;
        for (int i = 0; i < optimized.size(); i++) {
            MipsInstruction instr = optimized.get(i);
            if (instr.index != i) {
                moved = true;
                addExplanationRow(m, "Moved line " + (instr.index + 1),
                    "Placed it at line " + (i + 1) +
                    " because it was safe and could fill a cycle that would otherwise become a stall.");
            }
        }

        if (!moved) {
            addExplanationRow(m, "No move needed",
                "Every useful instruction was already in a safe place, or dependencies prevented safe reordering.");
        }

        addExplanationRow(m, "Result",
            "Stalls changed from " + lastBeforeStalls + " to " +
            lastAfterStalls + "; CPI changed from " +
            String.format("%.3f", lastBeforeCpi) + " to " +
            String.format("%.3f", lastAfterCpi) + ".");

        if (gain > 0.5) {
            addExplanationRow(m, "Optimization gain",
                String.format("The reordered code is %.1f%% better by CPI in this simplified pipeline model.", gain));
        } else {
            addExplanationRow(m, "Optimization gain",
                "No measurable CPI gain was possible for this instruction order.");
        }
    }

    private void addExplanationRow(DefaultTableModel m, String step,
                                   String explanation) {
        m.addRow(new Object[]{step, explanation});
    }

    private void exportOptimizedProgram() {
        if (lastOptimized == null || lastOptimized.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Run Optimize before exporting.",
                "OptiMIPS Export",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setSelectedFile(new File("optimips_optimized.asm"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File out = chooser.getSelectedFile();
        try {
            writeOptimizedFile(out);
            JOptionPane.showMessageDialog(this,
                "Optimized code exported to:\n" + out.getAbsolutePath(),
                "OptiMIPS Export",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Could not export optimized code:\n" + ex.getMessage(),
                "OptiMIPS Export Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveOptimizationReport() {
        if (lastOriginal == null || lastOptimized == null ||
            lastOptimized.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Run Optimize before saving a report.",
                "OptiMIPS Report",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setSelectedFile(new File("optimips_report.txt"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File out = chooser.getSelectedFile();
        try {
            writeReportFile(out);
            JOptionPane.showMessageDialog(this,
                "Optimization report saved to:\n" + out.getAbsolutePath(),
                "OptiMIPS Report",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this,
                "Could not save report:\n" + ex.getMessage(),
                "OptiMIPS Report Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void writeReportFile(File out) throws IOException {
        FileWriter writer = new FileWriter(out);
        try {
            double gain = lastBeforeCpi > 0
                ? ((lastBeforeCpi - lastAfterCpi) / lastBeforeCpi) * 100.0
                : 0.0;

            writer.write("OptiMIPS Optimization Report\n");
            writer.write("============================\n\n");
            writer.write("Summary\n");
            writer.write("-------\n");
            writer.write("Instruction count: " + lastOriginal.size() + "\n");
            writer.write("Original stalls: " + lastBeforeStalls + "\n");
            writer.write("Optimized stalls: " + lastAfterStalls + "\n");
            writer.write("Original CPI: " + String.format("%.3f", lastBeforeCpi) + "\n");
            writer.write("Optimized CPI: " + String.format("%.3f", lastAfterCpi) + "\n");
            writer.write("CPI improvement: " + String.format("%.1f", gain) + "%\n\n");

            writer.write("Thesis Table Row\n");
            writer.write("----------------\n");
            writer.write("Benchmark, Instructions, Stalls Before, Stalls After, CPI Before, CPI After, Improvement\n");
            writer.write("OptiMIPS run, " + lastOriginal.size() + ", " +
                         lastBeforeStalls + ", " + lastAfterStalls + ", " +
                         String.format("%.3f", lastBeforeCpi) + ", " +
                         String.format("%.3f", lastAfterCpi) + ", " +
                         String.format("%.1f", gain) + "%\n\n");

            writer.write("Hazards Found\n");
            writer.write("-------------\n");
            boolean anyHazard = false;
            for (int i = 0; i < lastOriginal.size(); i++) {
                for (int j = i + 1; j < lastOriginal.size(); j++) {
                    if (lastGraph.mustFollow(i, j)) {
                        anyHazard = true;
                        String type = lastGraph.getHazardType(
                            lastOriginal.get(i), lastOriginal.get(j));
                        writer.write("Line " + (i + 1) + " -> Line " +
                                     (j + 1) + " [" + type + "] " +
                                     lastOriginal.get(i).originalText.trim() +
                                     " -> " +
                                     lastOriginal.get(j).originalText.trim() +
                                     "\n");
                    }
                }
            }
            if (!anyHazard) writer.write("No hazards detected.\n");
            writer.write("\n");

            writer.write("Instruction Moves\n");
            writer.write("-----------------\n");
            boolean anyMove = false;
            for (int i = 0; i < lastOptimized.size(); i++) {
                MipsInstruction instr = lastOptimized.get(i);
                if (instr.index != i) {
                    anyMove = true;
                    writer.write(instr.mnemonic + " moved from line " +
                                 (instr.index + 1) + " to line " +
                                 (i + 1) + ": " +
                                 instr.originalText.trim() + "\n");
                }
            }
            if (!anyMove) writer.write("No instructions were moved.\n");
            writer.write("\n");

            writer.write("Plain English Explanation\n");
            writer.write("-------------------------\n");
            writer.write("The original program had instruction orderings where later instructions needed register values before the simplified pipeline model considered those values ready.\n");
            writer.write("OptiMIPS built a dependency graph first, so it only moved instructions when program meaning stayed safe.\n");
            writer.write("It then selected safe ready instructions that could fill stall cycles, reducing wait time and improving CPI when independent work was available.\n\n");

            writer.write("Optimized Instruction Order\n");
            writer.write("---------------------------\n");
            for (int i = 0; i < lastOptimized.size(); i++) {
                writer.write((i + 1) + ". " +
                             lastOptimized.get(i).originalText.trim() + "\n");
            }
        } finally {
            writer.close();
        }
    }

    private void applyOptimizedToEditor() {
        if (lastOriginal == null || lastOptimized == null ||
            lastOptimized.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Run Optimize before applying changes.",
                "OptiMIPS Apply",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (Globals.getGui() == null ||
            Globals.getGui().getMainPane() == null ||
            Globals.getGui().getMainPane().getEditPane() == null) {
            JOptionPane.showMessageDialog(this,
                "No active MARS editor was found.",
                "OptiMIPS Apply",
                JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
            "Apply the optimized instruction order to the current editor?\n" +
            "This keeps labels, comments, directives, and data lines that are not optimized.\n" +
            "Use File > Save As if you want to keep the original file unchanged.",
            "Apply Optimized Code",
            JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            EditPane editPane = Globals.getGui().getMainPane().getEditPane();
            String currentSource = editPane.getSource();
            String updatedSource = buildAppliedSource(currentSource);
            editPane.setSourceCode(updatedSource, true);
            editPane.setFileStatus(FileStatus.EDITED);
            FileStatus.set(FileStatus.EDITED);
            FileStatus.setEdited(true);
            hazardLog.append("\n=== Apply Result ===\n\n");
            hazardLog.append("Optimized instruction order was applied to the current editor.\n");
            JOptionPane.showMessageDialog(this,
                "Optimized code was applied to the current editor.\n" +
                "Save the file when you are ready.",
                "OptiMIPS Apply",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                "Could not apply optimized code:\n" + ex.getMessage(),
                "OptiMIPS Apply Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private String buildAppliedSource(String source) throws Exception {
        String[] lines = source.split("\\r?\\n", -1);
        if (lastOriginal.size() != lastOptimized.size())
            throw new Exception("Original and optimized instruction counts do not match.");

        Set<Integer> usedLines = new HashSet<>();
        for (MipsInstruction instr : lastOriginal) {
            if (instr.sourceLine <= 0 || instr.sourceLine > lines.length)
                throw new Exception("Source line information is unavailable. Assemble the current editor file first.");
            if (usedLines.contains(instr.sourceLine))
                throw new Exception("One source line expanded into multiple instructions. Apply is disabled for this case; use Export Optimized instead.");
            usedLines.add(instr.sourceLine);
        }

        for (int i = 0; i < lastOriginal.size(); i++) {
            int slotLine = lastOriginal.get(i).sourceLine;
            MipsInstruction replacement = lastOptimized.get(i);
            lines[slotLine - 1] = mergeIntoSourceLine(lines[slotLine - 1],
                                                       replacement);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private String mergeIntoSourceLine(String targetLine,
                                       MipsInstruction replacement) {
        String prefix = getIndentAndLabels(targetLine);
        String instrText = cleanupInstructionText(replacement.originalText,
                                                  replacement.mnemonic);
        return prefix + instrText;
    }

    private String getIndentAndLabels(String line) {
        int pos = 0;
        while (pos < line.length() && Character.isWhitespace(line.charAt(pos)))
            pos++;
        String prefix = line.substring(0, pos);
        int scan = pos;
        while (scan < line.length()) {
            int colon = line.indexOf(':', scan);
            if (colon < 0) break;
            String candidate = line.substring(scan, colon).trim();
            if (!isSimpleLabel(candidate)) break;
            prefix = line.substring(0, colon + 1);
            scan = colon + 1;
            while (scan < line.length() &&
                   Character.isWhitespace(line.charAt(scan)))
                scan++;
            prefix = line.substring(0, scan);
        }
        return prefix;
    }

    private boolean isSimpleLabel(String s) {
        if (s.length() == 0) return false;
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

    private String cleanupInstructionText(String text, String fallback) {
        String cleaned = text == null ? fallback : text.trim();
        while (true) {
            int colon = cleaned.indexOf(':');
            if (colon <= 0) break;
            String candidate = cleaned.substring(0, colon).trim();
            if (!isSimpleLabel(candidate)) break;
            cleaned = cleaned.substring(colon + 1).trim();
        }
        return cleaned;
    }

    private void writeOptimizedFile(File out) throws IOException {
        FileWriter writer = new FileWriter(out);
        try {
            writer.write("# Generated by OptiMIPS\n");
            writer.write("# This file contains the optimized instruction order from the assembled program.\n");
            writer.write("# If your original program used a .data section, keep/copy that data section as needed.\n");
            writer.write("# Original CPI: " + String.format("%.3f", lastBeforeCpi) +
                         " (" + lastBeforeStalls + " stalls)\n");
            writer.write("# Optimized CPI: " + String.format("%.3f", lastAfterCpi) +
                         " (" + lastAfterStalls + " stalls)\n\n");
            writer.write(".text\n");
            writer.write(".globl main\n");
            writer.write("main:\n");
            for (MipsInstruction instr : lastOptimized) {
                String text = instr.originalText != null
                    ? instr.originalText.trim()
                    : instr.mnemonic;
                if (text.length() > 0)
                    writer.write("    " + text + "\n");
            }
        } finally {
            writer.close();
        }
    }

    // ── table helpers ─────────────────────────────────────────────────
    private JTable makeTable() {
        DefaultTableModel model = new DefaultTableModel(
            new String[]{"#", "Instruction", "Type"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(model);
        t.setFont(new Font("Courier New", Font.PLAIN, 12));
        t.setRowHeight(24);
        t.getColumnModel().getColumn(0).setMaxWidth(32);
        t.getColumnModel().getColumn(2).setMaxWidth(56);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        return t;
    }

    private JTable makePressureTable() {
        DefaultTableModel model = new DefaultTableModel(
            new String[]{"P"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(model);
        t.setRowHeight(24);
        t.setShowGrid(false);
        t.setTableHeader(null);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setPreferredScrollableViewportSize(new Dimension(40, 0));
        t.setDefaultRenderer(Object.class, new PressureCellRenderer());
        return t;
    }

    private void populatePressureTable(JTable tbl, int[] pressure) {
        DefaultTableModel m = (DefaultTableModel) tbl.getModel();
        m.setRowCount(0);
        if (pressure == null) return;
        for (int value : pressure) {
            m.addRow(new Object[]{Integer.valueOf(value)});
        }
    }

    private class PressureCellRenderer extends JPanel
            implements TableCellRenderer {
        private int pressure;

        PressureCellRenderer() {
            setOpaque(true);
        }

        public Component getTableCellRendererComponent(
                JTable table, Object val, boolean sel, boolean foc,
                int row, int col) {
            pressure = val instanceof Integer
                ? ((Integer)val).intValue()
                : 0;
            setToolTipText(pressure + " live registers at this point");
            setBackground(row%2==0 ? Color.WHITE : new Color(248, 248, 252));
            Object starts = table.getClientProperty("blockStarts");
            if (starts instanceof Set && ((Set)starts).contains(
                    Integer.valueOf(row)) && row > 0) {
                setBorder(BorderFactory.createMatteBorder(
                    1, 0, 0, 0, new Color(210, 210, 210)));
            } else {
                setBorder(BorderFactory.createEmptyBorder());
            }
            return this;
        }

        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            int width = Math.min(40,
                Math.max(0, (int)Math.round((pressure / 16.0) * 40.0)));
            Color barColor;
            if (pressure <= 4)
                barColor = new Color(34, 197, 94);
            else if (pressure <= 8)
                barColor = new Color(249, 115, 22);
            else
                barColor = new Color(220, 38, 38);

            int y = Math.max(4, (getHeight() - 10) / 2);
            g.setColor(barColor);
            g.fillRect(0, y, width, 10);
        }
    }

    private JTable makeExplanationTable() {
        DefaultTableModel model = new DefaultTableModel(
            new String[]{"Step", "Easy explanation"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(model);
        t.setFont(new Font("Arial", Font.PLAIN, 12));
        t.setRowHeight(34);
        t.getColumnModel().getColumn(0).setMaxWidth(130);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setDefaultRenderer(Object.class,
            new DefaultTableCellRenderer() {
                public Component getTableCellRendererComponent(
                        JTable table, Object val, boolean sel,
                        boolean foc, int row, int col) {
                    Component c = super.getTableCellRendererComponent(
                        table, val, sel, foc, row, col);
                    if (!sel) {
                        c.setBackground(row%2==0
                            ? Color.WHITE
                            : new Color(248, 248, 252));
                    }
                    return c;
                }
            });
        return t;
    }

    private JTable makeStallProfileTable() {
        DefaultTableModel model = new DefaultTableModel(
            new String[]{"Rank", "Producer", "Consumer", "Type",
                         "Stalls", "% of total", "Eliminated?"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable t = new JTable(model);
        t.setFont(new Font("Arial", Font.PLAIN, 12));
        t.setRowHeight(26);
        t.getColumnModel().getColumn(0).setMaxWidth(48);
        t.getColumnModel().getColumn(3).setMaxWidth(90);
        t.getColumnModel().getColumn(4).setMaxWidth(64);
        t.getColumnModel().getColumn(5).setMaxWidth(82);
        t.getColumnModel().getColumn(6).setMaxWidth(92);
        t.setShowGrid(false);
        t.setIntercellSpacing(new Dimension(0, 1));
        t.setDefaultRenderer(Object.class,
            new DefaultTableCellRenderer() {
                public Component getTableCellRendererComponent(
                        JTable table, Object val, boolean sel,
                        boolean foc, int row, int col) {
                    Component c = super.getTableCellRendererComponent(
                        table, val, sel, foc, row, col);
                    if (!sel) {
                        c.setBackground(row%2==0
                            ? Color.WHITE
                            : new Color(248, 248, 252));
                        if (col == 6) {
                            String value = val == null ? "" : val.toString();
                            if ("YES".equals(value)) {
                                c.setBackground(new Color(220, 252, 231));
                                c.setForeground(new Color(21, 128, 61));
                            } else if ("NO".equals(value)) {
                                c.setBackground(new Color(254, 226, 226));
                                c.setForeground(new Color(185, 28, 28));
                            } else {
                                c.setForeground(new Color(40, 40, 40));
                            }
                        } else {
                            c.setForeground(new Color(40, 40, 40));
                        }
                    }
                    return c;
                }
            });
        return t;
    }

    private void populateTable(JTable tbl,
                               List<MipsInstruction> instrs,
                               boolean isOriginal) {
        DefaultTableModel m = (DefaultTableModel) tbl.getModel();
        m.setRowCount(0);

        // build hazard set for originals
        Set<Integer> hazardRows = new HashSet<>();
        Map<Integer,String> hazardTypes = new HashMap<>();
        if (isOriginal && lastGraph != null) {
            for (int i = 0; i < instrs.size(); i++) {
                for (int j = i+1; j < instrs.size(); j++) {
                    if (lastGraph.mustFollow(i, j)) {
                        hazardRows.add(i);
                        if (!hazardTypes.containsKey(i))
                            hazardTypes.put(i,
                                lastGraph.getHazardType(
                                    instrs.get(i), instrs.get(j)));
                    }
                }
            }
        }

        // rows that moved (for optimized table)
        Set<Integer> movedRows = new HashSet<>();
        if (!isOriginal) {
            for (int i = 0; i < instrs.size(); i++)
                if (instrs.get(i).index != i) movedRows.add(i);
        }

        Set<Integer> criticalRows = new HashSet<>();
        if (lastCriticalPath != null) {
            Set<Integer> criticalInstructionIndices =
                new HashSet<>(lastCriticalPath.criticalPath);
            for (int i = 0; i < instrs.size(); i++) {
                if (criticalInstructionIndices.contains(instrs.get(i).index))
                    criticalRows.add(i);
            }
        }

        Set<Integer> blockRows = new HashSet<>();
        Set<Integer> blockStarts = isOriginal
            ? lastOriginalBlockStarts
            : lastOptimizedBlockStarts;
        if (blockStarts != null) {
            for (Integer row : blockStarts) {
                if (row.intValue() > 0) blockRows.add(row);
            }
        }

        for (int i = 0; i < instrs.size(); i++) {
            String type = "";
            if (isOriginal && hazardTypes.containsKey(i))
                type = hazardTypes.get(i);
            else if (!isOriginal && movedRows.contains(i))
                type = "↑moved";
            m.addRow(new Object[]{ i+1,
                instrs.get(i).originalText != null
                    ? instrs.get(i).originalText.trim()
                    : instrs.get(i).mnemonic,
                type });
        }

        final Set<Integer> hRows = hazardRows;
        final Set<Integer> mRows = movedRows;
        final Set<Integer> cRows = criticalRows;
        final Set<Integer> bRows = blockRows;
        final boolean orig = isOriginal;

        tbl.setDefaultRenderer(Object.class,
            new DefaultTableCellRenderer() {
                public Component getTableCellRendererComponent(
                        JTable t, Object val, boolean sel,
                        boolean foc, int row, int col) {
                    Component c = super.getTableCellRendererComponent(
                        t, val, sel, foc, row, col);
                    if (!sel) {
                        if (orig && hRows.contains(row))
                            c.setBackground(new Color(255, 210, 210));
                        else if (!orig && mRows.contains(row))
                            c.setBackground(new Color(198, 246, 213));
                        else
                            c.setBackground(row%2==0
                                ? Color.WHITE
                                : new Color(248, 248, 252));
                    }
                    if (c instanceof JComponent) {
                        ((JComponent)c).setBorder(
                            tableCellBorder(cRows.contains(row) && col == 0,
                                            bRows.contains(row)));
                    }
                    return c;
                }
            });
    }

    private javax.swing.border.Border tableCellBorder(boolean critical,
                                                      boolean blockStart) {
        javax.swing.border.Border left = critical
            ? BorderFactory.createMatteBorder(
                0, 3, 0, 0, new Color(204, 0, 0))
            : BorderFactory.createEmptyBorder();
        if (!blockStart) return left;

        javax.swing.border.Border top = BorderFactory.createMatteBorder(
            1, 0, 0, 0, new Color(210, 210, 210));
        return BorderFactory.createCompoundBorder(top, left);
    }

    private JPanel wrapTable(JTable t, JTable pressureTable, String title,
                             Color hdrColor) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel lbl = new JLabel("  " + title);
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setOpaque(true);
        lbl.setBackground(hdrColor);
        lbl.setForeground(new Color(60, 60, 60));
        lbl.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        p.add(lbl, BorderLayout.NORTH);

        JScrollPane tableScroll = new JScrollPane(t);
        JScrollPane pressureScroll = new JScrollPane(pressureTable);
        pressureScroll.getVerticalScrollBar().setModel(
            tableScroll.getVerticalScrollBar().getModel());
        pressureScroll.setHorizontalScrollBarPolicy(
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        pressureScroll.setVerticalScrollBarPolicy(
            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        pressureScroll.setPreferredSize(new Dimension(40, 0));
        JLabel pressureHeaderSpacer = new JLabel("");
        pressureHeaderSpacer.setPreferredSize(new Dimension(40,
            t.getTableHeader().getPreferredSize().height));
        pressureScroll.setColumnHeaderView(pressureHeaderSpacer);
        pressureScroll.setBorder(BorderFactory.createMatteBorder(
            0, 1, 0, 0, new Color(220, 220, 220)));

        JPanel tableBody = new JPanel(new BorderLayout());
        tableBody.add(tableScroll, BorderLayout.CENTER);
        tableBody.add(pressureScroll, BorderLayout.EAST);
        p.add(tableBody, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createLineBorder(
            new Color(200, 200, 200)));
        return p;
    }

    private JPanel wrapTableLikePanel(JComponent component, String title,
                                      Color hdrColor) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel lbl = new JLabel("  " + title);
        lbl.setFont(new Font("Arial", Font.BOLD, 13));
        lbl.setOpaque(true);
        lbl.setBackground(hdrColor);
        lbl.setForeground(new Color(60, 60, 60));
        lbl.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        p.add(lbl, BorderLayout.NORTH);
        p.add(component, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createLineBorder(
            new Color(200, 200, 200)));
        return p;
    }

    private JPanel makeStatPanel(String title, JLabel lbl, Color bg) {
        JPanel p = new JPanel(new BorderLayout(0, 4));
        p.setBackground(bg);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        JLabel t = new JLabel(title);
        t.setFont(new Font("Arial", Font.BOLD, 11));
        t.setForeground(new Color(80, 80, 80));
        lbl.setFont(new Font("Arial", Font.PLAIN, 13));
        p.add(t, BorderLayout.NORTH);
        p.add(lbl, BorderLayout.CENTER);
        return p;
    }

    private JPanel makeInfoPanel(JLabel lbl, Color bg) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(bg);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(147, 197, 253)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        lbl.setForeground(new Color(30, 64, 175));
        p.add(lbl, BorderLayout.CENTER);
        return p;
    }

    @Override
    protected void addAsObserver() {}

    @Override
    protected void processMIPSUpdate(Observable o, AccessNotice n) {}
}
