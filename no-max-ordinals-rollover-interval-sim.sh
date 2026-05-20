#!/bin/bash

./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max 1 --rollover-interval 1 --ticks 30000 --rate 100 --csv-row --csv-header

for rollover in {2..10}; do
  for max in {1..30}; do
    ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --rollover-interval $rollover --ticks 30000 --rate 100 --csv-row
  done
done
