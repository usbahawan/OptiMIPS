# OptiMIPS Benchmark: Straight-line arithmetic
# Intentional arithmetic RAW hazards with independent instructions available
# for reordering between producer/use pairs.

.text
.globl main
main:
    add  $t0, $t1, $t2      # produces $t0
    sub  $t3, $t0, $t4      # RAW on $t0
    add  $s0, $s1, $s2      # independent, can move before sub
    xor  $t5, $t3, $t6      # RAW on $t3
    or   $s3, $s4, $s5      # independent
    and  $t7, $t5, $t0      # RAW on $t5 and $t0
    addi $s6, $s6, 4        # independent
    slt  $t8, $t7, $t3      # RAW on $t7
    subu $s7, $s7, $s4      # independent
    addu $t9, $t8, $t5      # RAW on $t8 and $t5
