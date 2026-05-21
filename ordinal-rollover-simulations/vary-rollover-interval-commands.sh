#!/bin/bash

for interval in 1 2 4 8; do
  for max in {1..100}; do
      echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --rollover-interval $interval --run-deleter  --deleter-only-if-no-ilm --circular-ordinals --circular-reverse
  done
done
