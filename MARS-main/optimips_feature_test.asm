# OptiMIPS Feature Test Program
# This version uses real/basic instructions instead of pseudo-instructions.
# It is designed to test:
# 1. Forwarding ON/OFF
# 2. Critical path
# 3. Stall profile
# 4. Register pressure
# 5. Loop-aware basic block splitting

.data
array:  .word 4, 8, 12, 16, 20, 24, 28, 32
result: .word 0

.text
.globl main
main:
    lui  $s0, 0x1001       # line 1: base address of data segment
    addi $s1, $zero, 8     # line 2: loop limit
    addi $s2, $zero, 0     # line 3: loop counter
    addi $s3, $zero, 0     # line 4: running sum
    addi $t7, $zero, 3     # line 5: constant used in loop

loop:
    lw   $t0, 0($s0)       # line 6: load array value
    add  $t1, $t0, $s3     # line 7: load-use RAW hazard, reads $t0
    add  $t2, $s2, $t7     # line 8: independent filler candidate
    sub  $t3, $s1, $s2     # line 9: independent filler candidate
    and  $t4, $t1, $t2     # line 10: depends on $t1 and $t2
    or   $t5, $t4, $t3     # line 11: depends on $t4 and $t3
    xor  $t6, $t5, $t0     # line 12: depends on $t5 and $t0
    add  $s3, $s3, $t6     # line 13: updates running sum
    addi $s0, $s0, 4       # line 14: move to next array element
    addi $s2, $s2, 1       # line 15: increment counter
    bne  $s2, $s1, loop    # line 16: loop branch

after_loop:
    sw   $s3, result       # line 17: store final sum
    add  $zero, $zero, $zero  # line 18: nop-style instruction
