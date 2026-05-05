"""
Reads .ci/db-versions.yml and writes the configured database versions as key=value
lines to stdout, ready to be appended to $GITHUB_OUTPUT.
"""

import yaml


def set_output(name, value):
    print(f"{name}={value}")


with open(".ci/db-versions.yml") as f:
    versions = yaml.safe_load(f)

es8_versions = versions["elasticsearch"]["es8"]

set_output("elasticsearch-8", es8_versions[-1])
set_output("saas",            versions["elasticsearch"]["saas"])
