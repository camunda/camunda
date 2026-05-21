#!/usr/bin/env python3

import sys
import subprocess

from concurrent.futures import ThreadPoolExecutor

commands_generator=sys.argv[1]

header = subprocess.check_output(["./ordinal-rollover-sim.py", "--csv-header-only"]).decode("utf-8").strip()
print(header)

commands = subprocess.check_output([commands_generator]).decode("utf-8").strip().splitlines()

commands_output = {}

with ThreadPoolExecutor() as executor:
  for i, command in enumerate(commands):
    executor.submit(lambda pos, cmd: commands_output.update({pos:subprocess.check_output(cmd, shell=True).decode("utf-8").strip()}), i, command)

# ensure we retain original order for final output
for pos in sorted(commands_output):
  print(commands_output[pos])
