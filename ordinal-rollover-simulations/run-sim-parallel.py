#!/usr/bin/env python3

import sys
import subprocess

from concurrent.futures import ThreadPoolExecutor

commands_generator=sys.argv[1]

header = subprocess.check_output(["./ordinal-rollover-sim.py", "--csv-header-only"]).decode("utf-8").strip()
print(header)

commands = subprocess.check_output([commands_generator]).decode("utf-8").strip().splitlines()

with ThreadPoolExecutor() as executor:
  for command in commands:
    executor.submit(lambda cmd: print(subprocess.check_output(cmd, shell=True).decode("utf-8").strip()), command)
