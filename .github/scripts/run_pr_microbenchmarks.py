#!/usr/bin/env python3
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.

# Automation requirements (implemented across this runner and its workflow):
# - Run only for same-repository pull requests opted in with the `jmh-run` label.
# - Compare the parent of the first first-parent `perf: ` commit after the merge base with the PR tip;
#   if there is no such commit, benchmark only the tip.
# - At each checked-out revision, select changed JMH sources and PR `JMH:` selectors, ignoring HTML
#   comments and selectors whose benchmark source is absent at that revision.
# - The `--select` mode tells the workflow whether to build and whether to post a report.
# - Use the workflow's run-maven action to build only when the checked-out revision has benchmarks.
# - Run each selected JMH selector and continue after individual benchmark failures.
# - Write a deduplicated, size-limited result report before failing on build or benchmark errors.

"""Run changed and PR-requested JMH benchmarks at the checked-out revision.

The workflow invokes ``--select`` before building and invokes the default mode
for reporting. ``--select`` writes ``has_benchmarks`` and ``should_report`` to
``GITHUB_OUTPUT``; it does not require GitHub credentials. Default mode assumes
the workflow's ``run-maven`` action has built the benchmark JAR, runs JMH, and
writes the report to ``JMH_REPORT_FILE``. Posting the report is handled by the
workflow. Running default mode manually does not post a comment.

The workflow supplies ``HEAD_SHA``, ``JMH_KIND``, ``JMH_REPORTED_MARKERS``,
``JMH_REPORT_FILE``, ``CHANGED_JAVA_FILES_JSON``, ``PR_BODY``,
``MAVEN_BUILD_OUTCOME``, and ``GITHUB_RUN_URL``. The workflow posts the report
separately so the GitHub token is never exposed to benchmark code.
"""

import hashlib
import json
import os
import re
import subprocess
import sys
from abc import ABC, abstractmethod
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path

JMH_ANNOTATION = re.compile(r"(?m)^[ \t]*@Benchmark\b")
PACKAGE = re.compile(r"(?m)^\s*package\s+([\w$]+(?:\.[\w$]+)*)\s*;")
PR_SELECTOR_LINE = re.compile(r"^\s*JMH:\s*(.*)$", re.IGNORECASE)
HTML_COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
BENCHMARK_JAR = "microbenchmarks/target/benchmarks.jar"
BENCHMARK_SOURCE_ROOT = "microbenchmarks/src/main/java/"
SHORT_TIMEOUT_SECONDS = 120
BENCHMARK_TIMEOUT_SECONDS = 3600
COMMENT_MAX_LENGTH = 60000


@dataclass(frozen=True)
class Config:
    """Everything the runner reads from the workflow environment.

    Attributes:
        head_sha: PR tip commit (``HEAD_SHA``). Changed files and PR selectors
            are always resolved against it, even when a baseline is checked out.
        kind: Which revision this run reports on, ``head`` or ``baseline``
            (``JMH_KIND``). Used in the report title and the dedup marker.
        changed_files: Java files changed by the PR
            (``CHANGED_JAVA_FILES_JSON``).
        requested: JMH selectors from the ``JMH:`` lines of the PR body
            (``PR_BODY``), sorted and deduplicated.
        reported_markers_file: File listing the markers of reports already
            posted, one per line (``JMH_REPORTED_MARKERS``).
        report_file: Where the Markdown report is written (``JMH_REPORT_FILE``).
        maven_build_outcome: Outcome of the workflow's Maven build step
            (``MAVEN_BUILD_OUTCOME``); anything but ``success`` skips JMH.
        run_url: Link to the workflow run, shown when the build failed
            (``GITHUB_RUN_URL``).
        github_output: ``GITHUB_OUTPUT`` file for ``--select``; the outputs are
            printed when unset.
        step_summary: ``GITHUB_STEP_SUMMARY`` file; no summary is written when
            unset.
    """

    head_sha: str
    kind: str
    changed_files: list[str]
    requested: list[str]
    reported_markers_file: Path
    report_file: Path
    maven_build_outcome: str = "success"
    run_url: str | None = None
    github_output: Path | None = None
    step_summary: Path | None = None

    @classmethod
    def from_env(cls, env: Mapping[str, str]) -> "Config":
        """Raises ValueError if a required variable is missing or malformed."""

        def required(name: str) -> str:
            if name not in env:
                raise ValueError(f"Missing environment variable {name}")
            return env[name]

        def optional_path(name: str) -> Path | None:
            return Path(env[name]) if env.get(name) else None

        return cls(
            head_sha=required("HEAD_SHA"),
            kind=required("JMH_KIND"),
            changed_files=json.loads(env.get("CHANGED_JAVA_FILES_JSON") or "[]"),
            requested=parse_pr_selectors(env.get("PR_BODY", "")),
            reported_markers_file=Path(required("JMH_REPORTED_MARKERS")),
            report_file=Path(required("JMH_REPORT_FILE")),
            maven_build_outcome=env.get("MAVEN_BUILD_OUTCOME", "success"),
            run_url=env.get("GITHUB_RUN_URL"),
            github_output=optional_path("GITHUB_OUTPUT"),
            step_summary=optional_path("GITHUB_STEP_SUMMARY"),
        )


@dataclass(frozen=True)
class BenchmarkRun:
    returncode: int
    output: str


class Toolchain(ABC):
    """The git and JMH commands the runner needs, so tests can swap them."""

    @abstractmethod
    def head_revision(self) -> str:
        """Full SHA of the checked-out revision. Raises RuntimeError on failure."""

    @abstractmethod
    def show_file(self, revision: str, path: str) -> str | None:
        """File content at the revision, or None if it does not exist there."""

    @abstractmethod
    def list_files(self, revision: str, root: str) -> list[str]:
        """Paths below root at the revision. Raises RuntimeError on failure."""

    @abstractmethod
    def run_benchmark(self, selector: str) -> BenchmarkRun:
        """Run the JMH selector against the built benchmark JAR."""


class SubprocessToolchain(Toolchain):
    def __init__(self, workspace: Path):
        self.workspace = workspace

    def head_revision(self) -> str:
        result = self._run(["git", "rev-parse", "HEAD"], SHORT_TIMEOUT_SECONDS)
        if result.returncode:
            raise RuntimeError(
                f"Could not determine checked-out revision:\n{result.output}"
            )
        return result.output.strip()

    def show_file(self, revision: str, path: str) -> str | None:
        result = self._run(["git", "show", f"{revision}:{path}"], SHORT_TIMEOUT_SECONDS)
        return None if result.returncode else result.output

    def list_files(self, revision: str, root: str) -> list[str]:
        result = self._run(
            ["git", "ls-tree", "-r", "--name-only", revision, "--", root],
            SHORT_TIMEOUT_SECONDS,
        )
        if result.returncode:
            raise RuntimeError(
                f"Could not list benchmark sources at {revision}:\n{result.output}"
            )
        return result.output.splitlines()

    def run_benchmark(self, selector: str) -> BenchmarkRun:
        return self._run(
            ["java", "-jar", BENCHMARK_JAR, selector], BENCHMARK_TIMEOUT_SECONDS
        )

    def _run(self, args: list[str], timeout: int) -> BenchmarkRun:
        try:
            completed = subprocess.run(
                args,
                cwd=self.workspace,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=timeout,
            )
            return BenchmarkRun(completed.returncode, completed.stdout)
        except subprocess.TimeoutExpired as error:
            output = error.stdout
            if isinstance(output, bytes):
                text = output.decode(errors="replace")
            else:
                text = output or ""
            return BenchmarkRun(124, f"{text}\nCommand timed out after {timeout}s.")
        except OSError as error:
            return BenchmarkRun(127, str(error))


@dataclass(frozen=True)
class BenchmarkSelection:
    revision: str
    selected_at_head: list[str]
    requested_at_head: list[str]
    selectors: list[str]

    @property
    def should_report(self) -> bool:
        return bool(self.selected_at_head or self.requested_at_head)


def selector_marker_suffix(selectors: list[str]) -> str:
    if not selectors:
        return ""
    digest = hashlib.sha256("\n".join(sorted(set(selectors))).encode()).hexdigest()
    return f":{digest}"


def is_jmh_benchmark_source(source: str) -> bool:
    return JMH_ANNOTATION.search(source) is not None


def benchmark_selector(path: str, source: str) -> str:
    package = PACKAGE.search(source)
    name = Path(path).stem
    return f"{package.group(1)}.{name}" if package else name


def parse_pr_selectors(body: str) -> list[str]:
    selectors: list[str] = []
    for line in HTML_COMMENT.sub("", body).splitlines():
        if match := PR_SELECTOR_LINE.match(line):
            selectors.extend(match.group(1).replace("`", "").replace(",", " ").split())
    return sorted(set(selectors))


def write_report(report_file: Path, body: str) -> None:
    if len(body) > COMMENT_MAX_LENGTH:
        body = (
            body[:COMMENT_MAX_LENGTH]
            + "\n\n[Output truncated to fit in a GitHub comment.]"
        )
    report_file.write_text(body, encoding="utf-8")


def write_selection_outputs(
    config: Config, has_benchmarks: bool, should_report: bool
) -> None:
    outputs = (
        f"has_benchmarks={str(has_benchmarks).lower()}\n"
        f"should_report={str(should_report).lower()}\n"
    )
    if config.github_output:
        with config.github_output.open("a", encoding="utf-8") as file:
            file.write(outputs)
    else:
        print(outputs, end="")


def write_no_benchmarks_summary(config: Config) -> None:
    print("No changed Java benchmark files or explicit JMH selectors were found.")
    if config.step_summary:
        with config.step_summary.open("a", encoding="utf-8") as file:
            file.write("## JMH PR benchmarks\n\nNo benchmarks were selected.\n")


def changed_selectors(
    revision: str, changed_files: list[str], toolchain: Toolchain
) -> list[str]:
    selectors = []
    for path in changed_files:
        source = toolchain.show_file(revision, path)
        if source is not None and is_jmh_benchmark_source(source):
            selectors.append(benchmark_selector(path, source))
    return list(dict.fromkeys(selectors))


def requested_at(
    revision: str, requested: list[str], toolchain: Toolchain
) -> list[str]:
    names: set[str] = set()
    for path in toolchain.list_files(revision, BENCHMARK_SOURCE_ROOT):
        if path.endswith(".java"):
            source = Path(path.removeprefix(BENCHMARK_SOURCE_ROOT)).with_suffix("")
            names.update((source.name, ".".join(source.parts)))
    return [
        selector
        for selector in requested
        if any(selector == name or selector.startswith(f"{name}.") for name in names)
    ]


def split_jmh_output(lines: list[str]) -> tuple[list[str], list[str]]:
    """Split JMH output into (progress log, final result table)."""
    for index in range(len(lines) - 1, -1, -1):
        if lines[index].startswith("Benchmark ") and lines[index].endswith("Units"):
            return lines[:index], lines[index:]
    return lines, []


def benchmark_results(
    selectors: list[str], toolchain: Toolchain
) -> tuple[list[str], bool]:
    result = [f"Benchmarks: `{', '.join(selectors)}`"]
    failed = False
    for selector in selectors:
        print(f"Running JMH selector: {selector}")
        run = toolchain.run_benchmark(selector)
        log, table = split_jmh_output(run.output.rstrip().splitlines())
        if run.returncode:
            failed = True
            log.append(f"JMH exited with status {run.returncode} for `{selector}`.")
        result.extend(["", f"### `{selector}`", ""])
        if table:
            result.extend(["```text", *table, "```", ""])
        result.extend(
            [
                "<details>",
                f"<summary>JMH log for <code>{selector}</code></summary>",
                "",
                "```text",
                *log,
                "```",
                "</details>",
            ]
        )
    return result, failed


def discover_selection(config: Config, toolchain: Toolchain) -> BenchmarkSelection:
    sha = toolchain.head_revision()
    requested = config.requested
    selected_at_head = changed_selectors(
        config.head_sha, config.changed_files, toolchain
    )
    requested_at_head = (
        requested_at(config.head_sha, requested, toolchain) if requested else []
    )

    if sha == config.head_sha:
        selectors = selected_at_head + requested_at_head
    else:
        selectors = changed_selectors(sha, config.changed_files, toolchain)
        if requested:
            selectors.extend(requested_at(sha, requested, toolchain))

    return BenchmarkSelection(
        revision=sha,
        selected_at_head=selected_at_head,
        requested_at_head=requested_at_head,
        selectors=list(dict.fromkeys(selectors)),
    )


def report_marker(selection: BenchmarkSelection, config: Config) -> str:
    suffix = selector_marker_suffix(config.requested + selection.selected_at_head)
    return f"<!-- jmh-run:{config.kind}:{selection.revision}{suffix} -->"


def marker_was_reported(marker: str, reported_file: Path) -> bool:
    return reported_file.exists() and marker in reported_file.read_text().splitlines()


def select_mode(config: Config, selection: BenchmarkSelection) -> int:
    if not selection.should_report:
        write_selection_outputs(config, False, False)
        write_no_benchmarks_summary(config)
        return 0

    marker = report_marker(selection, config)
    if marker_was_reported(marker, config.reported_markers_file):
        print(f"Already benchmarked: {marker}")
        write_selection_outputs(config, False, False)
        return 0

    write_selection_outputs(config, bool(selection.selectors), True)
    return 0


def run_mode(config: Config, selection: BenchmarkSelection, toolchain: Toolchain) -> int:
    if not selection.should_report:
        write_no_benchmarks_summary(config)
        return 0

    marker = report_marker(selection, config)
    if marker_was_reported(marker, config.reported_markers_file):
        print(f"Already benchmarked: {marker}")
        return 0

    result = [
        marker,
        "",
        f"## JMH {config.kind} results for `{selection.revision}`",
        "",
    ]
    failed = False
    if selection.selectors:
        if config.maven_build_outcome != "success":
            failed = True
            result.append(
                "Maven benchmark build failed "
                f"(step outcome: {config.maven_build_outcome})."
            )
            if config.run_url:
                result.extend(
                    [
                        "",
                        f"See [the GitHub Actions run]({config.run_url}) for Maven output.",
                    ]
                )
        else:
            output, failed = benchmark_results(selection.selectors, toolchain)
            result.extend(output)
    else:
        result.append(
            "No selected benchmark source exists at this revision; execution was skipped."
        )

    write_report(config.report_file, "\n".join(result).rstrip() + "\n")
    print(f"Wrote report: {marker}")
    return int(failed)


def main(args: list[str] | None = None, env: Mapping[str, str] | None = None) -> int:
    arguments = sys.argv[1:] if args is None else args
    if arguments not in ([], ["--select"]):
        print("Usage: run_pr_microbenchmarks.py [--select]")
        return 2

    environment = os.environ if env is None else env
    try:
        config = Config.from_env(environment)
        workspace = Path(environment.get("GITHUB_WORKSPACE") or Path.cwd())
        toolchain = SubprocessToolchain(workspace)
        selection = discover_selection(config, toolchain)
    except (ValueError, RuntimeError) as error:
        print(f"::error::{error}")
        return 1

    if arguments == ["--select"]:
        return select_mode(config, selection)
    return run_mode(config, selection, toolchain)


if __name__ == "__main__":
    sys.exit(main())
