#!/bin/bash

for max_ordinal in 15 30 45 60; do
  for max in {1..100}; do
      echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals $max_ordinal --rollover-interval 1 --run-deleter  --deleter-only-if-no-ilm --circular-ordinals --circular-reverse
  done
done
