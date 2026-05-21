#!/bin/bash

for rollover in {1..10}; do
  for max in {1..30}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --rollover-interval $rollover --ticks 30000 --rate 100 --csv-row
  done
done
