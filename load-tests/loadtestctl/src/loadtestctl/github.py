"""Helpers for running loadtestctl in GitHub Actions."""

import os
from typing import Any


def output(name: str, value: Any) -> None:
    """Sets a step output when running in GitHub Actions and prints `name=value` otherwise."""
    content = f"{name}={value}"
    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a") as output_file:
            print(content, file=output_file)
    else:
        print(content)
