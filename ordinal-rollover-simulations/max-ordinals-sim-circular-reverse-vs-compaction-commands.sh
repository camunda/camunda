#!/bin/bash

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --run-deleter  --deleter-only-if-no-ilm --circular-ordinals --circular-reverse
done

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --compaction
done
