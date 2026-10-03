"""Unit tests for run_pr_microbenchmarks.py."""

import json
import os
import shlex
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

import run_pr_microbenchmarks as runner
from run_pr_microbenchmarks import BenchmarkRun, Config

SOURCE_ROOT = runner.BENCHMARK_SOURCE_ROOT
BENCHMARK_SOURCE = "package io.example;\n@Benchmark public void run() {}"


class FakeToolchain(runner.Toolchain):
    """In-memory stand-in for git and JMH at one checked-out revision.

    head: SHA that ``head_revision`` reports as checked out.
    sources: file content per ``(revision, path)``; absent files return None.
    files: benchmark source paths per revision.
    benchmark_runs: result per selector; unlisted selectors succeed.
    executed: selectors passed to ``run_benchmark``, in order.
    """

    def __init__(self, head, sources=None, files=None, benchmark_runs=None):
        self.head = head
        self.sources = sources or {}  # (revision, path) -> content
        self.files = files or {}  # revision -> paths
        self.benchmark_runs = benchmark_runs or {}  # selector -> BenchmarkRun
        self.executed: list[str] = []

    def head_revision(self):
        return self.head

    def show_file(self, revision, path):
        return self.sources.get((revision, path))

    def list_files(self, revision, root):
        return self.files.get(revision, [])

    def run_benchmark(self, selector):
        self.executed.append(selector)
        return self.benchmark_runs.get(selector, BenchmarkRun(0, "score\n"))


def make_config(directory: str, **overrides) -> Config:
    """A head-run Config with all files inside ``directory``."""
    values = {
        "head_sha": "b" * 40,
        "kind": "head",
        "changed_files": [],
        "requested": [],
        "reported_markers_file": Path(directory) / "reported",
        "report_file": Path(directory) / "report.md",
        "github_output": Path(directory) / "github-output",
    }
    values.update(overrides)
    return Config(**values)


def select(config: Config, toolchain: FakeToolchain) -> int:
    """Run the ``--select`` mode."""
    return runner.select_mode(config, runner.discover_selection(config, toolchain))


def run(config: Config, toolchain: FakeToolchain) -> int:
    """Run the default mode that executes JMH and writes the report."""
    selection = runner.discover_selection(config, toolchain)
    return runner.run_mode(config, selection, toolchain)


class JmhScriptTests(unittest.TestCase):
    def test_parses_pr_selectors_and_ignores_html_comments(self):
        """Only ``JMH:`` lines outside HTML comments count, sorted and comma-split."""
        body = """
<!-- JMH: IgnoredBenchmark -->
JMH: MsgpackBenchmark.serialize, DeduplicationCacheBenchmark
"""
        self.assertEqual(
            runner.parse_pr_selectors(body),
            ["DeduplicationCacheBenchmark", "MsgpackBenchmark.serialize"],
        )

    def test_passes_selector_syntax_to_jmh(self):
        """JMH patterns such as ``.*`` reach the runner unchanged."""
        self.assertEqual(
            runner.parse_pr_selectors("JMH: MsgpackBenchmark.*"),
            ["MsgpackBenchmark.*"],
        )

    def test_fingerprints_selector_sets_independent_of_order(self):
        """The dedup marker depends on the selector set, not its order."""
        selectors = ["MsgpackBenchmark.serialize", "DeduplicationCacheBenchmark"]
        self.assertEqual(
            runner.selector_marker_suffix(selectors),
            runner.selector_marker_suffix(list(reversed(selectors))),
        )
        self.assertEqual(runner.selector_marker_suffix([]), "")

    def test_builds_config_from_workflow_environment(self):
        """Parses the variables and defaults the optional ones."""
        # when
        config = Config.from_env(
            {
                "HEAD_SHA": "abc",
                "JMH_KIND": "baseline",
                "JMH_REPORTED_MARKERS": "reported",
                "JMH_REPORT_FILE": "report.md",
                "CHANGED_JAVA_FILES_JSON": json.dumps(["A.java"]),
                "PR_BODY": "JMH: B",
                "GITHUB_OUTPUT": "",
            }
        )

        # then
        self.assertEqual(config.changed_files, ["A.java"])
        self.assertEqual(config.requested, ["B"])
        self.assertEqual(config.maven_build_outcome, "success")
        self.assertIsNone(config.github_output)

    def test_rejects_environment_without_required_variables(self):
        """A missing required variable is reported by name."""
        with self.assertRaisesRegex(ValueError, "HEAD_SHA"):
            Config.from_env({})

    def test_selection_mode_tells_the_workflow_whether_to_build_and_report(self):
        """``--select`` writes ``has_benchmarks`` and ``should_report`` outputs.

        Three cases: the tip builds and reports, an already-reported marker is
        skipped, and a baseline without the benchmark source reports only.
        """
        with tempfile.TemporaryDirectory() as directory:
            # given
            head, baseline = "b" * 40, "a" * 40
            path = SOURCE_ROOT + "io/example/ChangedBenchmark.java"
            sources = {(head, path): BENCHMARK_SOURCE}
            config = make_config(directory, changed_files=[path])

            # when
            self.assertEqual(select(config, FakeToolchain(head, sources)), 0)
            marker = (
                f"<!-- jmh-run:head:{head}"
                f"{runner.selector_marker_suffix(['io.example.ChangedBenchmark'])} -->"
            )
            config.reported_markers_file.write_text(marker + "\n")
            self.assertEqual(select(config, FakeToolchain(head, sources)), 0)
            baseline_config = make_config(
                directory, changed_files=[path], kind="baseline"
            )
            self.assertEqual(select(baseline_config, FakeToolchain(baseline, sources)), 0)

            # then: build at the tip, skip a duplicate, and report a baseline with no source
            self.assertEqual(
                config.github_output.read_text(),
                "has_benchmarks=true\nshould_report=true\n"
                "has_benchmarks=false\nshould_report=false\n"
                "has_benchmarks=false\nshould_report=true\n",
            )

    def test_detects_benchmark_annotations_and_extracts_class_selector(self):
        """Only a real ``@Benchmark`` annotation counts; the selector is package-qualified."""
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
        """Baseline and tip run different selectors, and reported markers are not rerun.

        Selectors come from the PR-changed benchmark and the ``JMH:`` requests
        that exist at the checked-out revision. A benchmark added by the PR is
        absent at the baseline, so nothing runs there.
        """
        with tempfile.TemporaryDirectory() as directory:
            # given
            baseline, head = "a" * 40, "b" * 40
            sources = {
                (head, "ChangedBenchmark.java"): BENCHMARK_SOURCE,
                (baseline, "ChangedBenchmark.java"): "class ChangedBenchmark {}",
            }
            requested_file = SOURCE_ROOT + "io/example/RequestedBenchmark.java"
            changed_file = SOURCE_ROOT + "io/example/ChangedBenchmark.java"
            files = {baseline: [requested_file], head: [requested_file, changed_file]}
            config = make_config(
                directory,
                changed_files=["ChangedBenchmark.java"],
                requested=[
                    "ChangedBenchmark.run",
                    "MissingBenchmark",
                    "RequestedBenchmark",
                ],
                kind="baseline",
            )
            head_config = make_config(
                directory,
                changed_files=config.changed_files,
                requested=config.requested,
            )
            at_baseline = FakeToolchain(baseline, sources, files)
            at_head = FakeToolchain(head, sources, files)

            # when
            self.assertEqual(run(config, at_baseline), 0)
            baseline_report = config.report_file.read_text()
            self.assertEqual(run(head_config, at_head), 0)
            head_report = head_config.report_file.read_text()

            # then
            self.assertIn("## JMH baseline results", baseline_report)
            self.assertIn("## JMH head results", head_report)
            self.assertEqual(at_baseline.executed, ["RequestedBenchmark"])
            self.assertEqual(
                at_head.executed,
                [
                    "io.example.ChangedBenchmark",
                    "ChangedBenchmark.run",
                    "RequestedBenchmark",
                ],
            )

            # when: both markers are already reported
            config.reported_markers_file.write_text(
                "\n".join(r.splitlines()[0] for r in (baseline_report, head_report))
            )
            rerun = FakeToolchain(head, sources, files)
            self.assertEqual(run(head_config, rerun), 0)

            # then
            self.assertEqual(head_config.report_file.read_text(), head_report)
            self.assertEqual(rerun.executed, [])

            # when: a newly added benchmark does not exist at the baseline
            no_requests = make_config(
                directory, changed_files=config.changed_files, kind="baseline"
            )
            skipped = FakeToolchain(baseline, sources, files)
            self.assertEqual(run(no_requests, skipped), 0)

            # then
            self.assertIn("execution was skipped", no_requests.report_file.read_text())
            self.assertEqual(skipped.executed, [])

    def test_reports_a_failed_maven_action_without_running_jmh(self):
        """A failed build is reported with the run link and fails the job."""
        with tempfile.TemporaryDirectory() as directory:
            # given
            head = "b" * 40
            config = make_config(
                directory,
                requested=["RequestedBenchmark"],
                maven_build_outcome="failure",
                run_url="https://github.com/camunda/camunda/actions/runs/123",
            )
            toolchain = FakeToolchain(
                head, files={head: [SOURCE_ROOT + "io/example/RequestedBenchmark.java"]}
            )

            # when
            self.assertEqual(run(config, toolchain), 1)

            # then
            report = config.report_file.read_text()
            self.assertIn("Maven benchmark build failed", report)
            self.assertIn("actions/runs/123", report)
            self.assertEqual(toolchain.executed, [])

    def test_fails_job_after_reporting_failed_benchmark(self):
        """The report is written before a failing benchmark fails the job."""
        with tempfile.TemporaryDirectory() as directory:
            # given
            head = "b" * 40
            config = make_config(directory, requested=["RequestedBenchmark.noSuchMethod"])
            toolchain = FakeToolchain(
                head,
                files={head: [SOURCE_ROOT + "io/example/RequestedBenchmark.java"]},
                benchmark_runs={
                    "RequestedBenchmark.noSuchMethod": BenchmarkRun(
                        1, "benchmark failed\n"
                    )
                },
            )

            # when
            self.assertEqual(run(config, toolchain), 1)

            # then
            report = config.report_file.read_text()
            self.assertIn("RequestedBenchmark.noSuchMethod", report)
            self.assertIn("JMH exited with status 1", report)

    def test_posts_result_table_and_folds_the_jmh_log(self):
        """The final result table is split from the progress log."""
        # given
        output = [
            "# Warmup Iteration   1: 0.5 us/op",
            "Result: 0.4 us/op",
            "Benchmark            (batchSize)  Mode  Cnt  Score  Units",
            "Example.run                 1000  avgt    2  0.400  us/op",
        ]

        # when
        log, table = runner.split_jmh_output(output)

        # then
        self.assertEqual(log, output[:2])
        self.assertEqual(table, output[2:])
        self.assertEqual(runner.split_jmh_output(["boom"]), (["boom"], []))


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
