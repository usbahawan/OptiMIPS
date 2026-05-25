# OptiMIPS Benchmark: Counting loop with loads and arithmetic
# Mixed load-use hazards and independent pointer/counter updates.

.data
array: .word 3, 7, 11, 15, 19, 23, 29, 31
sum:   .word 0

.text
.globl main
main:
    la   $t0, array         # base pointer
    addi $t1, $zero, 8      # loop count
    add  $t2, $zero, $zero  # running sum
    add  $s0, $zero, $zero  # independent accumulator

loop:
    lw   $t3, 0($t0)        # load current value
    add  $t2, $t2, $t3      # RAW load-use on $t3
    addi $t0, $t0, 4        # independent pointer update can fill delay
    addi $t1, $t1, -1       # independent counter update
    xor  $s0, $s0, $t1      # extra arithmetic independent of loaded value
    bne  $t1, $zero, loop   # loop branch boundary

    sw   $t2, sum

