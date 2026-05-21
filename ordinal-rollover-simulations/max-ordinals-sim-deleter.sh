#!/bin/bash

./ordinal-rollover-sim.py --mode main --duration-min 1 --duration-max 1 --ticks 30000 --rate 100 --csv-row --csv-header
for max in {2..30}; do
    ./ordinal-rollover-sim.py --mode main --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row
done

for max in {1..30}; do
    ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --run-deleter
done
