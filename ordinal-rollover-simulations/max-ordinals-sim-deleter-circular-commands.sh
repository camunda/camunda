#!/bin/bash

for max in {1..30}; do
    echo ./ordinal-rollover-sim.py --mode main --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row
done

for max in {1..30}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --run-deleter --circular-ordinals
done
