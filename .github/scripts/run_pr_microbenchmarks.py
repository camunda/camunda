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
# - Post a deduplicated, size-limited result comment before failing on build or benchmark errors.

"""Run changed and PR-requested JMH benchmarks at the checked-out revision.

The workflow invokes ``--select`` before building and invokes the default mode
for reporting. ``--select`` writes ``has_benchmarks`` and ``should_report`` to
``GITHUB_OUTPUT``; it does not require GitHub credentials. Default mode assumes
the workflow's ``run-maven`` action has built the benchmark JAR, runs JMH, and
posts a PR comment. Running default mode manually can post a real comment.

The workflow supplies ``HEAD_SHA``, ``JMH_KIND``, ``JMH_REPORTED_MARKERS``,
``CHANGED_JAVA_FILES_JSON``, ``PR_BODY``, ``GITHUB_REPOSITORY``, ``PR_NUMBER``,
``MAVEN_BUILD_OUTCOME``, and ``GITHUB_RUN_URL``. ``GH_TOKEN`` is the workflow's
built-in GitHub token, used only when posting a comment.
"""

import hashlib
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

JMH_ANNOTATION = re.compile(r"(?m)^[ \t]*@Benchmark\b")
PACKAGE = re.compile(r"(?m)^\s*package\s+([\w$]+(?:\.[\w$]+)*)\s*;")
PR_SELECTOR_LINE = re.compile(r"^\s*JMH:\s*(.*)$", re.IGNORECASE)
HTML_COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
BENCHMARK_JAR = "microbenchmarks/target/benchmarks.jar"
SHORT_TIMEOUT_SECONDS = 120
BENCHMARK_TIMEOUT_SECONDS = 3600
COMMENT_MAX_LENGTH = 60000


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


def run_command(
    args: list[str],
    workspace: Path,
    timeout: int | None = None,
    input_text: str | None = None,
) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            args,
            cwd=workspace,
            text=True,
            input=input_text,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
        )
    except subprocess.TimeoutExpired as error:
        output = error.stdout
        if isinstance(output, bytes):
            text = output.decode(errors="replace")
        else:
            text = output or ""
        return subprocess.CompletedProcess(
            args, 124, stdout=f"{text}\nCommand timed out after {timeout}s."
        )
    except OSError as error:
        return subprocess.CompletedProcess(args, 127, stdout=str(error))


def post_pr_comment(workspace: Path, body: str) -> None:
    repo = os.environ["GITHUB_REPOSITORY"]
    pr_number = os.environ["PR_NUMBER"]
    if len(body) > COMMENT_MAX_LENGTH:
        body = (
            body[:COMMENT_MAX_LENGTH]
            + "\n\n[Output truncated to fit in a GitHub comment.]"
        )
    result = run_command(
        [
            "gh",
            "api",
            "--method",
            "POST",
            f"repos/{repo}/issues/{pr_number}/comments",
            "--input",
            "-",
        ],
        workspace,
        timeout=SHORT_TIMEOUT_SECONDS,
        input_text=json.dumps({"body": body}),
    )
    if result.returncode:
        raise RuntimeError(f"Failed to post PR comment:\n{result.stdout}")


def write_selection_outputs(has_benchmarks: bool, should_report: bool) -> None:
    outputs = (
        f"has_benchmarks={str(has_benchmarks).lower()}\n"
        f"should_report={str(should_report).lower()}\n"
    )
    if output_file := os.environ.get("GITHUB_OUTPUT"):
        with Path(output_file).open("a", encoding="utf-8") as file:
            file.write(outputs)
    else:
        print(outputs, end="")


def write_no_benchmarks_summary() -> None:
    print("No changed Java benchmark files or explicit JMH selectors were found.")
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(summary).open("a", encoding="utf-8") as file:
            file.write("## JMH PR benchmarks\n\nNo benchmarks were selected.\n")


def changed_selectors(
    revision: str, changed_files: list[str], workspace: Path
) -> list[str]:
    selectors = []
    for path in changed_files:
        source = run_command(
            ["git", "show", f"{revision}:{path}"],
            workspace,
            timeout=SHORT_TIMEOUT_SECONDS,
        )
        if source.returncode == 0 and is_jmh_benchmark_source(source.stdout):
            selectors.append(benchmark_selector(path, source.stdout))
    return list(dict.fromkeys(selectors))


def requested_at(revision: str, requested: list[str], workspace: Path) -> list[str]:
    root = "microbenchmarks/src/main/java/"
    files = run_command(
        ["git", "ls-tree", "-r", "--name-only", revision, "--", root],
        workspace,
        timeout=SHORT_TIMEOUT_SECONDS,
    )
    if files.returncode:
        raise RuntimeError(
            f"Could not list benchmark sources at {revision}:\n{files.stdout}"
        )
    names: set[str] = set()
    for path in files.stdout.splitlines():
        if path.endswith(".java"):
            source = Path(path.removeprefix(root)).with_suffix("")
            names.update((source.name, ".".join(source.parts)))
    return [
        selector
        for selector in requested
        if any(selector == name or selector.startswith(f"{name}.") for name in names)
    ]


def benchmark_results(selectors: list[str], workspace: Path) -> tuple[list[str], bool]:
    result = [f"Benchmarks: `{', '.join(selectors)}`", "", "```text"]
    failed = False
    for selector in selectors:
        print(f"Running JMH selector: {selector}")
        run = run_command(
            ["java", "-jar", BENCHMARK_JAR, selector],
            workspace,
            timeout=BENCHMARK_TIMEOUT_SECONDS,
        )
        result.extend(run.stdout.rstrip().splitlines())
        if run.returncode:
            failed = True
            result.append(f"JMH exited with status {run.returncode} for `{selector}`.")
    result.append("```")
    return result, failed


def discover_selection(
    head_sha: str,
    changed_files: list[str],
    requested: list[str],
    workspace: Path,
) -> BenchmarkSelection:
    revision = run_command(
        ["git", "rev-parse", "HEAD"], workspace, timeout=SHORT_TIMEOUT_SECONDS
    )
    if revision.returncode:
        raise RuntimeError(
            f"Could not determine checked-out revision:\n{revision.stdout}"
        )
    sha = revision.stdout.strip()
    selected_at_head = changed_selectors(head_sha, changed_files, workspace)
    requested_at_head = (
        requested_at(head_sha, requested, workspace) if requested else []
    )

    if sha == head_sha:
        selectors = selected_at_head + requested_at_head
    else:
        selectors = changed_selectors(sha, changed_files, workspace)
        if requested:
            selectors.extend(requested_at(sha, requested, workspace))

    return BenchmarkSelection(
        revision=sha,
        selected_at_head=selected_at_head,
        requested_at_head=requested_at_head,
        selectors=list(dict.fromkeys(selectors)),
    )


def report_marker(
    selection: BenchmarkSelection, kind: str, requested: list[str]
) -> str:
    suffix = selector_marker_suffix(requested + selection.selected_at_head)
    return f"<!-- jmh-run:{kind}:{selection.revision}{suffix} -->"


def marker_was_reported(marker: str, reported_file: Path) -> bool:
    return reported_file.exists() and marker in reported_file.read_text().splitlines()


def select_mode(
    selection: BenchmarkSelection,
    kind: str,
    requested: list[str],
    reported_file: Path,
) -> int:
    if not selection.should_report:
        write_selection_outputs(False, False)
        write_no_benchmarks_summary()
        return 0

    marker = report_marker(selection, kind, requested)
    if marker_was_reported(marker, reported_file):
        print(f"Already benchmarked: {marker}")
        write_selection_outputs(False, False)
        return 0

    write_selection_outputs(bool(selection.selectors), True)
    return 0


def run_mode(
    selection: BenchmarkSelection,
    workspace: Path,
    kind: str,
    requested: list[str],
    reported_file: Path,
) -> int:
    if not selection.should_report:
        write_no_benchmarks_summary()
        return 0

    marker = report_marker(selection, kind, requested)
    if marker_was_reported(marker, reported_file):
        print(f"Already benchmarked: {marker}")
        return 0

    result = [marker, "", f"## JMH {kind} results for `{selection.revision}`", ""]
    failed = False
    if selection.selectors:
        build_outcome = os.environ.get("MAVEN_BUILD_OUTCOME", "success")
        if build_outcome != "success":
            failed = True
            result.append(
                f"Maven benchmark build failed (step outcome: {build_outcome})."
            )
            if run_url := os.environ.get("GITHUB_RUN_URL"):
                result.extend(
                    ["", f"See [the GitHub Actions run]({run_url}) for Maven output."]
                )
        else:
            output, failed = benchmark_results(selection.selectors, workspace)
            result.extend(output)
    else:
        result.append(
            "No selected benchmark source exists at this revision; execution was skipped."
        )

    try:
        post_pr_comment(workspace, "\n".join(result).rstrip() + "\n")
    except RuntimeError as error:
        print(f"::error::{error}")
        return 1
    print(f"Posted result: {marker}")
    return int(failed)


def main(args: list[str] | None = None) -> int:
    arguments = sys.argv[1:] if args is None else args
    if arguments not in ([], ["--select"]):
        print("Usage: run_pr_microbenchmarks.py [--select]")
        return 2

    try:
        head_sha = os.environ["HEAD_SHA"]
        changed_files: list[str] = json.loads(
            os.environ.get("CHANGED_JAVA_FILES_JSON") or "[]"
        )
        requested = parse_pr_selectors(os.environ.get("PR_BODY", ""))
        kind = os.environ["JMH_KIND"]
        reported_file = Path(os.environ["JMH_REPORTED_MARKERS"])
    except (KeyError, ValueError) as error:
        print(f"::error::{error}")
        return 1

    workspace = Path(os.environ.get("GITHUB_WORKSPACE", str(Path.cwd())))
    try:
        selection = discover_selection(head_sha, changed_files, requested, workspace)
    except RuntimeError as error:
        print(f"::error::{error}")
        return 1

    if arguments == ["--select"]:
        return select_mode(selection, kind, requested, reported_file)
    return run_mode(selection, workspace, kind, requested, reported_file)


if __name__ == "__main__":
    sys.exit(main())
