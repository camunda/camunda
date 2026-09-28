"""Unit tests for run_pr_microbenchmarks.py."""

import json
import os
import shlex
import shutil
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

    def test_passes_selector_syntax_to_jmh(self):
        self.assertEqual(
            runner.parse_pr_selectors("JMH: MsgpackBenchmark.*"),
            ["MsgpackBenchmark.*"],
        )

    def test_fingerprints_selector_sets_independent_of_order(self):
        selectors = ["MsgpackBenchmark.serialize", "DeduplicationCacheBenchmark"]
        self.assertEqual(
            runner.selector_marker_suffix(selectors),
            runner.selector_marker_suffix(list(reversed(selectors))),
        )
        self.assertEqual(runner.selector_marker_suffix([]), "")

    def test_selection_mode_tells_the_workflow_whether_to_build_and_report(self):
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            head, baseline = "b" * 40, "a" * 40
            benchmark_path = (
                "microbenchmarks/src/main/java/io/example/ChangedBenchmark.java"
            )
            output_file = workspace / "github-output"
            reported = workspace / "reported"
            environment = {
                "HEAD_SHA": head,
                "JMH_KIND": "head",
                "JMH_REPORTED_MARKERS": str(reported),
                "GITHUB_WORKSPACE": directory,
                "CHANGED_JAVA_FILES_JSON": json.dumps([benchmark_path]),
                "GITHUB_OUTPUT": str(output_file),
                "PR_BODY": "",
            }
            current = head

            def fake_command(args, _workspace, timeout=None, input_text=None):
                if args[:3] == ["git", "rev-parse", "HEAD"]:
                    return subprocess.CompletedProcess(args, 0, current + "\n")
                if args[:2] == ["git", "show"]:
                    if args[2].startswith(f"{head}:"):
                        return subprocess.CompletedProcess(
                            args,
                            0,
                            "package io.example;\n@Benchmark public void run() {}",
                        )
                    return subprocess.CompletedProcess(args, 1, "source is absent")
                return subprocess.CompletedProcess(args, 0, "")

            with (
                patch.dict(os.environ, environment),
                patch.object(runner, "run_command", side_effect=fake_command),
            ):
                # then: build at the tip, skip a duplicate, and report a baseline with no source
                self.assertEqual(runner.main(["--select"]), 0)
                marker = (
                    f"<!-- jmh-run:head:{head}"
                    f"{runner.selector_marker_suffix(['io.example.ChangedBenchmark'])} -->"
                )
                reported.write_text(marker + "\n")
                self.assertEqual(runner.main(["--select"]), 0)
                current = baseline
                os.environ["JMH_KIND"] = "baseline"
                self.assertEqual(runner.main(["--select"]), 0)

            self.assertEqual(
                output_file.read_text(),
                "has_benchmarks=true\nshould_report=true\n"
                "has_benchmarks=false\nshould_report=false\n"
                "has_benchmarks=false\nshould_report=true\n",
            )

    def test_detects_benchmark_annotations_and_extracts_class_selector(self):
        source = """
package io.example;
class ExampleBenchmark {
  // @Benchmark should not count
  @BenchmarkMode(Mode.AverageTime)
  @Benchmark
  public void run() {}
}
"""
        self.assertTrue(runner.is_jmh_benchmark_source(source))
        self.assertFalse(
            runner.is_jmh_benchmark_source(
                "// @Benchmark\n@BenchmarkMode(Mode.AverageTime)"
            )
        )
        self.assertEqual(
            runner.benchmark_selector("microbenchmarks/ExampleBenchmark.java", source),
            "io.example.ExampleBenchmark",
        )

    def test_runs_each_checked_out_revision_and_skips_reported_markers(self):
        # given
        with tempfile.TemporaryDirectory() as directory:
            baseline, head = "a" * 40, "b" * 40
            reported = Path(directory) / "reported"
            environment = {
                "HEAD_SHA": head,
                "GITHUB_WORKSPACE": directory,
                "JMH_REPORTED_MARKERS": str(reported),
                "CHANGED_JAVA_FILES_JSON": json.dumps(["ChangedBenchmark.java"]),
                "PR_BODY": "JMH: RequestedBenchmark, ChangedBenchmark.run, MissingBenchmark",
            }
            current = baseline
            commands = []
            comments = []

            def fake_command(args, _workspace, timeout=None, input_text=None):
                commands.append(args)
                if args[:3] == ["git", "rev-parse", "HEAD"]:
                    output = current + "\n"
                elif args[:2] == ["git", "show"]:
                    revision = args[2].split(":")[0]
                    output = (
                        "package io.example;\n@Benchmark public void run() {}"
                        if revision == head
                        else "package io.example;\nclass ChangedBenchmark {}"
                    )
                elif args[:2] == ["git", "ls-tree"]:
                    output = "microbenchmarks/src/main/java/io/example/RequestedBenchmark.java\n"
                    if args[4] == head:
                        output += "microbenchmarks/src/main/java/io/example/ChangedBenchmark.java\n"
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
                os.environ["JMH_KIND"] = "baseline"
                self.assertEqual(runner.main([]), 0)
                current = head
                os.environ["JMH_KIND"] = "head"
                self.assertEqual(runner.main([]), 0)

                # then
                self.assertEqual(len(comments), 2)
                self.assertIn("## JMH baseline results", comments[0])
                self.assertIn("## JMH head results", comments[1])
                self.assertEqual(
                    [
                        args[-1]
                        for args in commands
                        if args[:3] == ["java", "-jar", runner.BENCHMARK_JAR]
                    ],
                    [
                        "RequestedBenchmark",
                        "io.example.ChangedBenchmark",
                        "ChangedBenchmark.run",
                        "RequestedBenchmark",
                    ],
                )
                self.assertFalse(
                    any(args[:2] == ["git", "checkout"] for args in commands)
                )
                reported.write_text(
                    "\n".join(body.splitlines()[0] for body in comments)
                )
                comments.clear()
                commands.clear()
                self.assertEqual(runner.main([]), 0)
                self.assertEqual(comments, [])
                self.assertFalse(
                    any(args[0] in ("java", "./mvnw") for args in commands)
                )

                # A newly added benchmark does not exist at the baseline.
                os.environ["PR_BODY"] = ""
                os.environ["JMH_KIND"] = "baseline"
                current = baseline
                commands.clear()
                self.assertEqual(runner.main([]), 0)
                self.assertIn("execution was skipped", comments[0])
                self.assertFalse(
                    any(args[0] in ("java", "./mvnw") for args in commands)
                )

    def test_reports_a_failed_maven_action_without_running_jmh(self):
        # given
        with tempfile.TemporaryDirectory() as directory:
            head = "b" * 40
            environment = {
                "HEAD_SHA": head,
                "JMH_KIND": "head",
                "GITHUB_WORKSPACE": directory,
                "JMH_REPORTED_MARKERS": str(Path(directory) / "reported"),
                "CHANGED_JAVA_FILES_JSON": "[]",
                "PR_BODY": "JMH: RequestedBenchmark",
                "MAVEN_BUILD_OUTCOME": "failure",
                "GITHUB_RUN_URL": "https://github.com/camunda/camunda/actions/runs/123",
            }
            commands = []
            comments = []

            def fake_command(args, _workspace, timeout=None, input_text=None):
                commands.append(args)
                if args[:3] == ["git", "rev-parse", "HEAD"]:
                    return subprocess.CompletedProcess(args, 0, head + "\n")
                if args[:2] == ["git", "ls-tree"]:
                    return subprocess.CompletedProcess(
                        args,
                        0,
                        "microbenchmarks/src/main/java/io/example/RequestedBenchmark.java\n",
                    )
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
                self.assertEqual(runner.main([]), 1)

            # then
            self.assertEqual(len(comments), 1)
            self.assertIn("Maven benchmark build failed", comments[0])
            self.assertIn("actions/runs/123", comments[0])
            self.assertFalse(
                any(
                    args[:3] == ["java", "-jar", runner.BENCHMARK_JAR]
                    for args in commands
                )
            )

    def test_fails_job_after_reporting_failed_benchmark(self):
        # given
        with tempfile.TemporaryDirectory() as directory:
            head = "b" * 40
            environment = {
                "HEAD_SHA": head,
                "JMH_KIND": "head",
                "GITHUB_WORKSPACE": directory,
                "JMH_REPORTED_MARKERS": str(Path(directory) / "reported"),
                "PR_BODY": "JMH: RequestedBenchmark.noSuchMethod",
            }
            comments = []

            def fake_command(args, _workspace, timeout=None, input_text=None):
                if args[:3] == ["git", "rev-parse", "HEAD"]:
                    return subprocess.CompletedProcess(args, 0, head + "\n")
                if args[:2] == ["git", "ls-tree"]:
                    return subprocess.CompletedProcess(
                        args,
                        0,
                        "microbenchmarks/src/main/java/io/example/RequestedBenchmark.java\n",
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
                self.assertEqual(runner.main([]), 1)

            # then
            self.assertEqual(len(comments), 1)
            self.assertIn("RequestedBenchmark.noSuchMethod", comments[0])
            self.assertIn("JMH exited with status 1", comments[0])


class RevisionDiscoveryTests(unittest.TestCase):
    SCRIPT = Path(__file__).with_name("find_pr_microbenchmark_revisions.sh").resolve()

    def create_repo(self, directory: str) -> Path:
        repo = Path(directory)
        subprocess.run(["git", "init", "--quiet", "-b", "main", str(repo)], check=True)
        subprocess.run(
            ["git", "-C", str(repo), "config", "user.name", "Test"], check=True
        )
        subprocess.run(
            ["git", "-C", str(repo), "config", "user.email", "test@example.com"],
            check=True,
        )
        return repo

    def commit(self, repo: Path, message: str, index: int) -> str:
        (repo / f"file-{index}").write_text(message, encoding="utf-8")
        subprocess.run(["git", "-C", str(repo), "add", "."], check=True)
        subprocess.run(
            ["git", "-C", str(repo), "commit", "--quiet", "-m", message], check=True
        )
        return subprocess.check_output(
            ["git", "-C", str(repo), "rev-parse", "HEAD"], text=True
        ).strip()

    def discover(
        self,
        repo: Path,
        base: str,
        head: str,
        extra_env: dict[str, str] | None = None,
    ) -> dict[str, str]:
        environment = os.environ.copy()
        if extra_env:
            environment.update(extra_env)
        result = subprocess.run(
            [str(self.SCRIPT), base, head],
            cwd=repo,
            check=True,
            text=True,
            capture_output=True,
            env=environment,
        )
        return dict(line.split("=", 1) for line in result.stdout.splitlines())

    def test_uses_parent_of_first_perf_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = self.create_repo(directory)
            base = self.commit(repo, "base: start", 0)
            before_perf = self.commit(repo, "fix: add benchmark source", 1)
            self.commit(repo, "perf: optimize benchmark", 2)
            head = self.commit(repo, "perf: tune benchmark", 3)

            outputs = self.discover(repo, base, head)

            self.assertEqual(outputs["baseline_sha"], before_perf)
            self.assertEqual(
                json.loads(outputs["revisions"]), ["baseline", "branch tip"]
            )

    def test_uses_merge_base_when_first_pr_commit_is_perf(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = self.create_repo(directory)
            base = self.commit(repo, "base: start", 0)
            head = self.commit(repo, "perf: optimize benchmark", 1)

            outputs = self.discover(repo, base, head)

            self.assertEqual(outputs["baseline_sha"], base)
            self.assertEqual(
                json.loads(outputs["revisions"]), ["baseline", "branch tip"]
            )

    def test_reads_git_log_after_first_perf_match_without_sigpipe(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = self.create_repo(directory)
            base = self.commit(repo, "base: start", 0)
            before_perf = self.commit(repo, "fix: prepare benchmark", 1)
            head = self.commit(repo, "perf: optimize benchmark", 2)
            real_git = shutil.which("git")
            if real_git is None:
                self.fail("git must be available for the revision discovery test")

            fake_bin = repo / "fake-bin"
            fake_bin.mkdir()
            producer = fake_bin / "emit-git-log.py"
            producer.write_text(
                "import os\n"
                "print(f\"{os.environ['PERF_COMMIT']}\\tperf: first\")\n"
                "for index in range(100000):\n"
                '    print(f"{index:040d}\\tchore: filler")\n',
                encoding="utf-8",
            )
            fake_git = fake_bin / "git"
            fake_git.write_text(
                "#!/usr/bin/env bash\n"
                "set -euo pipefail\n"
                'if [[ "$1" == "log" ]]; then\n'
                f"  exec python3 {shlex.quote(str(producer))}\n"
                "else\n"
                f'  exec {shlex.quote(real_git)} "$@"\n'
                "fi\n",
                encoding="utf-8",
            )
            fake_git.chmod(0o755)

            outputs = self.discover(
                repo,
                base,
                head,
                {
                    "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
                    "PERF_COMMIT": head,
                },
            )

            self.assertEqual(outputs["baseline_sha"], before_perf)
            self.assertEqual(
                json.loads(outputs["revisions"]), ["baseline", "branch tip"]
            )

    def test_runs_tip_only_without_first_parent_perf_commit(self):
        with tempfile.TemporaryDirectory() as directory:
            repo = self.create_repo(directory)
            base = self.commit(repo, "base: start", 0)
            subprocess.run(
                ["git", "-C", str(repo), "checkout", "--quiet", "-b", "side"],
                check=True,
            )
            self.commit(repo, "perf: side-branch change", 1)
            subprocess.run(
                ["git", "-C", str(repo), "checkout", "--quiet", "main"], check=True
            )
            self.commit(repo, "fix: mentions perf: but is not a perf commit", 2)
            subprocess.run(
                [
                    "git",
                    "-C",
                    str(repo),
                    "merge",
                    "--no-ff",
                    "--quiet",
                    "side",
                    "-m",
                    "merge side branch",
                ],
                check=True,
            )
            head = subprocess.check_output(
                ["git", "-C", str(repo), "rev-parse", "HEAD"], text=True
            ).strip()

            outputs = self.discover(repo, base, head)

            self.assertEqual(outputs["baseline_sha"], "")
            self.assertEqual(json.loads(outputs["revisions"]), ["branch tip"])


if __name__ == "__main__":
    unittest.main()
