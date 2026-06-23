#!/bin/bash

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode main --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row
done

for max in {1..100}; do
    for max_circular in 1 5 10 20 30; do
      echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --circular-ordinals --circular-reverse --circular-ordinals-max $max_circular --run-deleter  --deleter-only-if-no-ilm
    done
done
