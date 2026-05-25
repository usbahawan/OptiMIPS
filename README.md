# OptiMIPS

OptiMIPS is a pipeline-aware instruction-reordering and optimization plugin for the **MARS (MIPS Assembler and Runtime Simulator)**. It automatically analyzes assembled MIPS assembly code, detects data/control hazards, and reorganizes the instructions to minimize pipeline stall cycles and improve the Cycles Per Instruction (CPI) without altering the program logic.

---

## Key Features

- **Automatic Hazard Detection**: Identifies Read-After-Write (RAW), Write-After-Read (WAR), Write-After-Write (WAW), control hazards (branches/jumps), and memory hazards.
- **Data Dependency Graph (DDG)**: Graph-based analysis ensures that instruction reordering respects actual program dependencies.
- **List Scheduling Algorithm**: A static instruction scheduler that automatically reorders instructions to fill pipeline stall slots with safe, independent instructions.
- **Pipeline Simulation**: Models a classic MIPS pipeline stage execution to accurately calculate original vs. optimized stalls and CPI.
- **Interactive GUI**: Integrated directly into MARS under the **Tools** menu, displaying original and optimized code side-by-side with color-coded highlighting of hazard cycles.

---

## How It Works

1. **Parser Hook**: OptiMIPS reads the assembled instruction list directly from MARS's internal memory after assembly.
2. **Graph Construction**: Builds a Directed Acyclic Graph (DAG) where nodes represent MIPS instructions and directed edges represent strict dependency constraints.
3. **Instruction Reordering**: Evaluates the critical path and uses list scheduling heuristics to insert independent instructions into identified stall slots.
4. **Visual Profiling**: Presents the results in a Swing-based comparison panel with execution metrics (+% gain, CPI, stall counts) and a detailed hazard log.

---

## File Structure

```text
MARS-main/
│
├── mars/                             # Core MARS Packages
│   └── tools/                        # MARS Tool Plugins
│       ├── OptiMIPS.java             # Main UI & Swing Integration
│       ├── MarsParser.java           # ProgramStatement to MipsInstruction Converter
│       ├── Scheduler.java            # List Scheduling & Stall Calculation
│       ├── DependencyGraph.java      # Data Dependency Graph Builder
│       └── MipsInstruction.java      # MIPS Instruction Data Wrapper
│
├── Mars.jar                          # Compiled Executable with Plugin Included
├── build_complete_updated_report.py  # Automation scripts for compiling reports
└── README.md                         # Project documentation
```

---

## Getting Started & Compiling

To compile and run OptiMIPS:

### 1. Requirements
- Java Development Kit (JDK) 8 or higher.

### 2. Compilation
Compile all Java files from the project root directory:
```bash
javac -cp . mars/tools/*.java
```

### 3. Running MARS
Launch the simulator:
```bash
java -jar Mars.jar
```
*(Alternatively, you can compile and rebuild the JAR file using the `CreateMarsJar.bat` script).*

---

## Usage

1. Open **MARS** (`Mars.jar`).
2. Write or load a MIPS assembly program (e.g., `test_optimips.asm`).
3. Assemble the program (**F3** or *Run > Assemble*).
4. Go to **Tools** in the top menu and select **OptiMIPS — Pipeline Optimizer**.
5. Click **Optimize Program** to generate the dependency graph, reschedule instructions, and view the side-by-side CPI comparison.
