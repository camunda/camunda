#!/bin/bash

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode main --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row
done

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --circular-ordinals --circular-reverse --run-deleter --deleter-only-if-no-ilm
done

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --circular-ordinals --circular-reverse --circular-ordinals-max 20 --run-deleter --deleter-only-if-no-ilm
done

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --circular-ordinals --circular-reverse --circular-ordinals-max 29 --run-deleter --deleter-only-if-no-ilm
done

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --circular-ordinals --circular-reverse --circular-by-least-used --run-deleter --deleter-only-if-no-ilm
done

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --circular-ordinals --circular-reverse --circular-by-least-used --circular-ordinals-max 20 --run-deleter --deleter-only-if-no-ilm
done

for max in {1..100}; do
    echo ./ordinal-rollover-sim.py --mode ordinal --duration-min 1 --duration-max $max --ticks 30000 --rate 100 --csv-row --max-ordinals 30 --circular-ordinals --circular-reverse --circular-by-least-used --circular-ordinals-max 29 --run-deleter --deleter-only-if-no-ilm
done
