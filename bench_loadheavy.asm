# OptiMIPS Benchmark: Load-heavy sum
# Loads five memory values, sums them, and stores the result.

.data
values: .word 5, 10, 15, 20, 25
result: .word 0

.text
.globl main
main:
    la   $t0, values

    lw   $t1, 0($t0)        # load value 0
    add  $t4, $zero, $t1    # RAW load-use on $t1
    addi $s0, $zero, 1      # independent, can move up
    lw   $t2, 4($t0)        # load value 1
    add  $t4, $t4, $t2      # RAW load-use on $t2
    addi $s1, $s0, 3        # independent, can move up
    lw   $t3, 8($t0)        # load value 2
    add  $t4, $t4, $t3      # RAW load-use on $t3
    addi $s2, $s1, 5        # independent, can move up
    lw   $t5, 12($t0)       # load value 3
    add  $t4, $t4, $t5      # RAW load-use on $t5
    lw   $t6, 16($t0)       # load value 4
    add  $t9, $t4, $t6      # RAW load-use on $t6

    sw   $t9, result
