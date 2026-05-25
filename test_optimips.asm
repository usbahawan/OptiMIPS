# OptiMIPS Test Program
# This program has 3 intentional RAW hazards
# The optimizer should reorder to eliminate them

.text
.globl main
main:
    add  $t0, $t1, $t2    # writes $t0
    lw   $t3, 0($t0)      # RAW: reads $t0 immediately
    sub  $t4, $t3, $t1    # RAW: reads $t3 before lw done
    add  $t5, $t6, $t7    # independent - safe to move up
    mul  $t8, $t5, $t0    # reads $t5, $t0 (both available after reorder)
    sw   $t8, 4($t0)      # store result
    add  $zero, $zero, $zero  # nop
