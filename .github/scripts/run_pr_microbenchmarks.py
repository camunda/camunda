#!/usr/bin/env python3
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.

"""Compare changed and PR-requested JMH benchmarks across a PR's baseline and perf commits.

The workflow supplies changed Java paths and posts each result file as a PR comment.
"""

import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

JMH_ANNOTATION = re.compile(r"(?m)^\s*(?:@[\w$.]+\s*)*@(?:[\w$]+\.)*Benchmark\b")
PACKAGE = re.compile(r"(?m)^\s*package\s+([\w$]+(?:\.[\w$]+)*)\s*;")
BENCHMARK_NAME = re.compile(r"[A-Za-z0-9_$][A-Za-z0-9_.$]*")
PERF_SUBJECT = re.compile(r"^perf(?:\([^)]*\))?!?:")
PR_SELECTOR_LINE = re.compile(r"^\s*JMH:\s*(.*)$", re.IGNORECASE)
PR_SELECTOR = re.compile(r"[A-Za-z0-9_$]+(?:\.[A-Za-z0-9_$]+)*")
HTML_COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
MICROBENCHMARKS_MODULE = "microbenchmarks"
BENCHMARK_JAR = f"{MICROBENCHMARKS_MODULE}/target/benchmarks.jar"


def parse_changed_files(value):
    try:
        paths = json.loads(value)
    except json.JSONDecodeError as error:
        raise ValueError(
            "Changed Java files must be supplied as a JSON array."
        ) from error
    if not isinstance(paths, list) or not all(isinstance(path, str) for path in paths):
        raise ValueError("Changed Java files must be a JSON string array.")
    if any(
        not path.startswith(f"{MICROBENCHMARKS_MODULE}/") or not path.endswith(".java")
        for path in paths
    ):
        raise ValueError(
            f"Changed file list contains a path outside {MICROBENCHMARKS_MODULE} Java sources."
        )
    return [path for path in paths if isinstance(path, str)]


def selector_marker_suffix(selectors):
    # A changed request gets a new marker; identical requests remain idempotent.
    if not selectors:
        return ""
    digest = hashlib.sha256("\n".join(sorted(set(selectors))).encode()).hexdigest()
    return f":{digest}"


def is_jmh_benchmark_source(source):
    return JMH_ANNOTATION.search(source) is not None


def benchmark_selector(path, source):
    package = PACKAGE.search(source)
    name = Path(path).stem
    return f"{package.group(1)}.{name}" if package else name


def available_benchmarks(output):
    return [
        line.strip()
        for line in output.splitlines()
        if BENCHMARK_NAME.fullmatch(line.strip())
    ]


def parse_pr_selectors(body):
    selectors = set()
    for line in HTML_COMMENT.sub("", body).splitlines():
        match = PR_SELECTOR_LINE.match(line)
        if match:
            for selector in match.group(1).replace("`", "").replace(",", " ").split():
                if not PR_SELECTOR.fullmatch(selector):
                    raise ValueError(
                        f"Invalid JMH selector {selector!r}; use ClassName or ClassName.methodName."
                    )
                selectors.add(selector)
    return sorted(selectors)


def is_perf_commit(subject):
    return PERF_SUBJECT.match(subject) is not None


def resolve_requested_selectors(requested, available):
    classes = list(dict.fromkeys(name.rsplit(".", 1)[0] for name in available))
    selected_classes, selected_methods, missing = [], [], []
    for selector in requested:
        matches = [
            name
            for name in classes
            if name == selector or name.endswith(f".{selector}")
        ]
        if matches:
            selected_classes.extend(matches)
            continue
        matches = [
            name
            for name in available
            if name == selector or name.endswith(f".{selector}")
        ]
        if matches:
            selected_methods.extend(matches)
        else:
            missing.append(selector)
    selected_classes = list(dict.fromkeys(selected_classes))
    selected_methods = list(dict.fromkeys(selected_methods))
    # Selecting a class already covers its methods, so don't benchmark them twice.
    selected_methods = [
        name
        for name in selected_methods
        if not any(name.startswith(f"{class_name}.") for class_name in selected_classes)
    ]
    return selected_classes + selected_methods, missing


def run_command(args, workspace):
    try:
        return subprocess.run(
            args,
            cwd=workspace,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
        )
    except OSError as error:
        return subprocess.CompletedProcess(args, 127, stdout=str(error))


def main():
    try:
        base_sha = os.environ["BASE_SHA"]
        head_sha = os.environ["HEAD_SHA"]
        results_dir = Path(os.environ["JMH_RESULTS_DIR"])
        reported_file = Path(os.environ["JMH_REPORTED_MARKERS"])
        failure_file = Path(os.environ["JMH_FAILURE_FILE"])
        changed_files = parse_changed_files(
            os.environ.get("CHANGED_JAVA_FILES_JSON", "[]")
        )
        requested = parse_pr_selectors(os.environ.get("PR_BODY", ""))
    except (KeyError, ValueError) as error:
        print(f"::error::{error}")
        return 1

    workspace = Path(os.environ.get("GITHUB_WORKSPACE", str(Path.cwd())))
    results_dir.mkdir(parents=True, exist_ok=True)
    failure_file.unlink(missing_ok=True)
    reported = (
        set(reported_file.read_text().splitlines()) if reported_file.exists() else set()
    )
    failed = False

    def command(*args):
        return run_command(list(args), workspace)

    def git(*args):
        result = command("git", *args)
        if result.returncode:
            raise RuntimeError(f"git {' '.join(args)} failed:\n{result.stdout}")
        return result.stdout

    def checkout(revision):
        result = command("git", "checkout", "--detach", revision)
        return result.returncode == 0

    def source_at(revision, path):
        result = command("git", "show", f"{revision}:{path}")
        return result.stdout if result.returncode == 0 else None

    def run_revision(index, kind, revision, marker_suffix):
        nonlocal failed
        marker = f"<!-- jmh-run:{kind}:{revision}{marker_suffix} -->"
        if marker in reported:
            print(f"Already benchmarked: {marker}")
            return
        # Build each historical snapshot in place, then restore the PR head for later workflow steps.
        if not checkout(revision):
            raise RuntimeError(f"Could not check out revision {revision}")

        result = [marker, "", f"## JMH {kind} results for `{revision}`", ""]
        selectors = []
        for path in changed_files:
            source = source_at(revision, path)
            if source and is_jmh_benchmark_source(source):
                selectors.append(benchmark_selector(path, source))
        selectors = list(dict.fromkeys(selectors))

        if not selectors and not requested:
            result.append(
                "No selected benchmark source exists at this revision; execution was skipped."
            )
        else:
            build = command(
                "./mvnw",
                "-pl",
                MICROBENCHMARKS_MODULE,
                "-am",
                "-DskipTests",
                "clean",
                "package",
            )
            if build.returncode:
                failed = True
                result.extend(
                    [
                        f"Maven benchmark build failed with status {build.returncode}.",
                        "",
                        "Last 120 lines of build output:",
                        "```text",
                        *build.stdout.splitlines()[-120:],
                        "```",
                    ]
                )
            else:
                listing = command("java", "-jar", BENCHMARK_JAR, "-l")
                if listing.returncode:
                    failed = True
                    result.extend(
                        [
                            f"Could not list available JMH benchmarks (exit {listing.returncode}).",
                            "```text",
                            *listing.stdout.splitlines()[-80:],
                            "```",
                        ]
                    )
                else:
                    explicit, missing = resolve_requested_selectors(
                        requested, available_benchmarks(listing.stdout)
                    )
                    selectors = list(dict.fromkeys(selectors + explicit))
                    if missing:
                        result.extend(
                            [
                                "Requested selectors unavailable at this revision:",
                                *[f"- `{name}`" for name in missing],
                                "",
                            ]
                        )
                    if selectors:
                        result.extend(
                            [f"Benchmarks: `{', '.join(selectors)}`", "", "```text"]
                        )
                        # Pass no iteration flags so JMH uses the benchmark annotations/defaults.
                        for selector in selectors:
                            print(f"Running JMH selector: {selector}")
                            run = command("java", "-jar", BENCHMARK_JAR, selector)
                            result.extend(run.stdout.rstrip().splitlines())
                            if run.returncode:
                                failed = True
                                result.append(
                                    f"JMH exited with status {run.returncode} for `{selector}`."
                                )
                    else:
                        result.append(
                            "No selected benchmark is available at this revision; execution was skipped."
                        )
                    if selectors:
                        result.append("```")

        result_path = results_dir / f"{index:04d}-{kind}-{revision}.md"
        result_path.write_text("\n".join(result).rstrip() + "\n", encoding="utf-8")

    try:
        baseline = git("merge-base", base_sha, head_sha).strip()
        changed_benchmarks = []
        for path in changed_files:
            source = source_at(head_sha, path)
            if source and is_jmh_benchmark_source(source):
                changed_benchmarks.append(benchmark_selector(path, source))
        if not changed_benchmarks and not requested:
            print(
                "No changed Java benchmark files or explicit JMH selectors were found."
            )
            summary = os.environ.get("GITHUB_STEP_SUMMARY")
            if summary:
                with Path(summary).open("a", encoding="utf-8") as file:
                    file.write("## JMH PR benchmarks\n\nNo benchmarks were selected.\n")
            return 0

        commits = git(
            "log",
            "--no-merges",
            "--reverse",
            "--format=%H%x09%s",
            f"{baseline}..{head_sha}",
        )
        revisions = [("baseline", baseline)]
        revisions.extend(
            ("commit", sha)
            for line in commits.splitlines()
            for sha, _, subject in [line.partition("\t")]
            if is_perf_commit(subject)
        )
        suffix = selector_marker_suffix(requested)
        for index, (kind, revision) in enumerate(revisions):
            run_revision(index, kind, revision, suffix)

        if failed:
            failure_file.touch()
        print(f"Benchmark result files: {len(list(results_dir.glob('*.md')))}")
        return 0
    except RuntimeError as error:
        print(f"::error::{error}")
        return 1
    finally:
        if not checkout(head_sha):
            print(f"::warning::Could not restore PR head {head_sha}")


if __name__ == "__main__":
    sys.exit(main())
