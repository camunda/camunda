#!/usr/bin/env python3
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.

"""Compare changed and PR-requested JMH benchmarks at the PR baseline and tip."""

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
PR_SELECTOR_LINE = re.compile(r"^\s*JMH:\s*(.*)$", re.IGNORECASE)
PR_SELECTOR = re.compile(r"[A-Za-z0-9_$]+(?:\.[A-Za-z0-9_$]+)*")
HTML_COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
BENCHMARK_JAR = "microbenchmarks/target/benchmarks.jar"
SHORT_TIMEOUT_SECONDS = 120
BUILD_TIMEOUT_SECONDS = 1800
BENCHMARK_TIMEOUT_SECONDS = 3600
COMMENT_MAX_LENGTH = 60000


def parse_changed_files(value: str) -> list[str]:
    try:
        return json.loads(value) if value else []
    except json.JSONDecodeError as error:
        raise ValueError(
            "Changed Java files must be supplied as a JSON array."
        ) from error


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


def available_benchmarks(output: str) -> list[str]:
    return [
        line.strip()
        for line in output.splitlines()
        if BENCHMARK_NAME.fullmatch(line.strip())
    ]


def parse_pr_selectors(body: str) -> list[str]:
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


def resolve_requested_selectors(
    requested: list[str], available: list[str]
) -> tuple[list[str], list[str]]:
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
    return selected_classes + [
        name
        for name in selected_methods
        if not any(name.startswith(f"{class_name}.") for class_name in selected_classes)
    ], missing


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


def main() -> int:
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

    def command(
        *args: str, timeout: int = SHORT_TIMEOUT_SECONDS
    ) -> subprocess.CompletedProcess[str]:
        return run_command(list(args), workspace, timeout=timeout)

    def git(*args: str) -> str:
        result = command("git", *args)
        if result.returncode:
            raise RuntimeError(f"git {' '.join(args)} failed:\n{result.stdout}")
        return result.stdout

    def checkout(revision: str) -> bool:
        return command("git", "checkout", "--detach", revision).returncode == 0

    def changed_selectors(revision: str) -> list[str]:
        selectors = []
        for path in changed_files:
            source = command("git", "show", f"{revision}:{path}")
            if source.returncode == 0 and is_jmh_benchmark_source(source.stdout):
                selectors.append(benchmark_selector(path, source.stdout))
        return list(dict.fromkeys(selectors))

    def benchmark_results(selectors: list[str]) -> list[str]:
        nonlocal failed
        build = command(
            "./mvnw",
            "-pl",
            "microbenchmarks",
            "-am",
            "-DskipTests",
            "clean",
            "package",
            timeout=BUILD_TIMEOUT_SECONDS,
        )
        if build.returncode:
            failed = True
            return [
                f"Maven benchmark build failed with status {build.returncode}.",
                "",
                "Last 120 lines of build output:",
                "```text",
                *build.stdout.splitlines()[-120:],
                "```",
            ]

        listing = command("java", "-jar", BENCHMARK_JAR, "-l")
        if listing.returncode:
            failed = True
            return [
                f"Could not list available JMH benchmarks (exit {listing.returncode}).",
                "```text",
                *listing.stdout.splitlines()[-80:],
                "```",
            ]

        explicit, missing = resolve_requested_selectors(
            requested, available_benchmarks(listing.stdout)
        )
        selectors = list(
            dict.fromkeys(
                selectors
                + [
                    name
                    for name in explicit
                    if not any(
                        name.startswith(f"{class_name}.") for class_name in selectors
                    )
                ]
            )
        )
        result = []
        if missing:
            result.extend(
                [
                    "Requested selectors unavailable at this revision:",
                    *[f"- `{name}`" for name in missing],
                    "",
                ]
            )
        if not selectors:
            return result + [
                "No selected benchmark is available at this revision; execution was skipped."
            ]

        result.extend([f"Benchmarks: `{', '.join(selectors)}`", "", "```text"])
        for selector in selectors:
            print(f"Running JMH selector: {selector}")
            run = command(
                "java",
                "-jar",
                BENCHMARK_JAR,
                selector,
                timeout=BENCHMARK_TIMEOUT_SECONDS,
            )
            result.extend(run.stdout.rstrip().splitlines())
            if run.returncode:
                failed = True
                result.append(
                    f"JMH exited with status {run.returncode} for `{selector}`."
                )
        result.append("```")
        return result

    def run_revision(index: int, kind: str, revision: str, suffix: str) -> None:
        marker = f"<!-- jmh-run:{kind}:{revision}{suffix} -->"
        if marker in reported:
            print(f"Already benchmarked: {marker}")
            return
        if not checkout(revision):
            raise RuntimeError(f"Could not check out revision {revision}")

        result = [marker, "", f"## JMH {kind} results for `{revision}`", ""]
        selectors = changed_selectors(revision)
        if selectors or requested:
            result.extend(benchmark_results(selectors))
        else:
            result.append(
                "No selected benchmark source exists at this revision; execution was skipped."
            )
        body = "\n".join(result).rstrip() + "\n"
        (results_dir / f"{index:04d}-{kind}-{revision}.md").write_text(
            body, encoding="utf-8"
        )
        post_pr_comment(workspace, body)
        print(f"Posted result: {marker}")

    try:
        baseline = git("merge-base", base_sha, head_sha).strip()
        selected = changed_selectors(head_sha)
        if not selected and not requested:
            print(
                "No changed Java benchmark files or explicit JMH selectors were found."
            )
            if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
                with Path(summary).open("a", encoding="utf-8") as file:
                    file.write("## JMH PR benchmarks\n\nNo benchmarks were selected.\n")
            return 0

        suffix = selector_marker_suffix(requested + selected)
        revisions = [("baseline", baseline)]
        if head_sha != baseline:
            revisions.append(("head", head_sha))
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
