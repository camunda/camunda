"""Unit tests for run_pr_microbenchmarks.py."""

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import run_pr_microbenchmarks as runner


class JmhScriptTests(unittest.TestCase):
    def test_parses_pr_selectors_and_ignores_html_comments(self):
        body = """
<!-- JMH: IgnoredBenchmark -->
JMH: MsgpackBenchmark.serialize, DeduplicationCacheBenchmark
"""
        self.assertEqual(
            runner.parse_pr_selectors(body),
            ["DeduplicationCacheBenchmark", "MsgpackBenchmark.serialize"],
        )

    def test_rejects_invalid_selector_syntax(self):
        with self.assertRaises(ValueError):
            runner.parse_pr_selectors("JMH: MsgpackBenchmark.*")

    def test_fingerprints_selector_sets_independent_of_order(self):
        selectors = ["MsgpackBenchmark.serialize", "DeduplicationCacheBenchmark"]
        self.assertEqual(
            runner.selector_marker_suffix(selectors),
            runner.selector_marker_suffix(list(reversed(selectors))),
        )
        self.assertEqual(runner.selector_marker_suffix([]), "")

    def test_detects_benchmark_annotations_and_extracts_class_selector(self):
        source = """
package io.example;
class ExampleBenchmark {
  // @Benchmark should not count
  @org.openjdk.jmh.annotations.Benchmark
  public void run() {}
}
"""
        self.assertTrue(runner.is_jmh_benchmark_source(source))
        self.assertEqual(
            runner.benchmark_selector("microbenchmarks/ExampleBenchmark.java", source),
            "io.example.ExampleBenchmark",
        )

    def test_resolves_class_and_method_selectors(self):
        available = [
            "io.example.MsgpackBenchmark.serialize",
            "io.example.MsgpackBenchmark.deserialize",
        ]
        self.assertEqual(
            runner.resolve_requested_selectors(
                ["MsgpackBenchmark.serialize"], available
            ),
            (["io.example.MsgpackBenchmark.serialize"], []),
        )

    def test_marks_failed_benchmarks_and_restores_head(self):
        # given
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            head = "b" * 40
            environment = {
                "BASE_SHA": "a" * 40,
                "HEAD_SHA": head,
                "GITHUB_WORKSPACE": directory,
                "JMH_RESULTS_DIR": str(workspace / "results"),
                "JMH_REPORTED_MARKERS": str(workspace / "reported"),
                "JMH_FAILURE_FILE": str(workspace / "failure"),
                "CHANGED_JAVA_FILES_JSON": json.dumps(["ChangedBenchmark.java"]),
                "PR_BODY": "",
            }
            commands = []
            comments = []

            def fake_command(args, _workspace, timeout=None, input_text=None):
                commands.append(args)
                if args[:2] == ["git", "merge-base"]:
                    return subprocess.CompletedProcess(args, 0, "a" * 40 + "\n")
                if args[:2] == ["git", "show"]:
                    return subprocess.CompletedProcess(
                        args, 0, "@Benchmark public void run() {}"
                    )
                if args[-1:] == ["-l"]:
                    return subprocess.CompletedProcess(
                        args, 0, "ChangedBenchmark.run\n"
                    )
                if args[:3] == ["java", "-jar", runner.BENCHMARK_JAR]:
                    return subprocess.CompletedProcess(args, 1, "benchmark failed\n")
                return subprocess.CompletedProcess(args, 0, "")

            with (
                patch.dict(os.environ, environment),
                patch.object(runner, "run_command", side_effect=fake_command),
                patch.object(
                    runner,
                    "post_pr_comment",
                    side_effect=lambda _, body: comments.append(body),
                ),
            ):
                # when
                self.assertEqual(runner.main(), 0)

            # then
            self.assertTrue((workspace / "failure").exists())
            self.assertEqual(len(comments), 2)
            self.assertIn("JMH exited with status 1", comments[0])
            self.assertEqual(commands[-1], ["git", "checkout", "--detach", head])

    def test_runs_changed_and_requested_benchmarks_and_skips_reported_revisions(self):
        # given
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            reported = workspace / "reported"
            base, head = "a" * 40, "b" * 40
            path = "microbenchmarks/src/main/java/ChangedBenchmark.java"
            source = "package io.example;\n@Benchmark public void run() {}"
            environment = {
                "BASE_SHA": base,
                "HEAD_SHA": head,
                "GITHUB_WORKSPACE": directory,
                "JMH_RESULTS_DIR": str(workspace / "results"),
                "JMH_REPORTED_MARKERS": str(reported),
                "JMH_FAILURE_FILE": str(workspace / "failure"),
                "CHANGED_JAVA_FILES_JSON": json.dumps([path]),
                "PR_BODY": "JMH: RequestedBenchmark, ChangedBenchmark.run",
            }
            commands = []
            comments = []

            def fake_command(args, _workspace, timeout=None, input_text=None):
                commands.append(args)
                if args[:2] == ["git", "merge-base"]:
                    output = base + "\n"
                elif args[:2] == ["git", "show"]:
                    output = source
                elif args[-1:] == ["-l"]:
                    output = (
                        "io.example.ChangedBenchmark.run\n"
                        "io.example.RequestedBenchmark.run\n"
                    )
                else:
                    output = "score\n"
                return subprocess.CompletedProcess(args, 0, output)

            with (
                patch.dict(os.environ, environment),
                patch.object(runner, "run_command", side_effect=fake_command),
                patch.object(
                    runner,
                    "post_pr_comment",
                    side_effect=lambda _, body: comments.append(body),
                ),
            ):
                # when
                self.assertEqual(runner.main(), 0)

                # then
                self.assertEqual(len(comments), 2)
                self.assertEqual(
                    [
                        args[-1]
                        for args in commands
                        if args[:3] == ["java", "-jar", runner.BENCHMARK_JAR]
                        and args[-1] != "-l"
                    ],
                    ["io.example.ChangedBenchmark", "io.example.RequestedBenchmark"]
                    * 2,
                )
                self.assertEqual(commands[-1], ["git", "checkout", "--detach", head])
                self.assertEqual(len(list((workspace / "results").glob("*.md"))), 2)
                reported.write_text(
                    "\n".join(body.splitlines()[0] for body in comments)
                )
                commands.clear()
                comments.clear()
                self.assertEqual(runner.main(), 0)
                self.assertEqual(comments, [])
                self.assertFalse(
                    any(args[0] in ("java", "./mvnw") for args in commands)
                )


if __name__ == "__main__":
    unittest.main()
