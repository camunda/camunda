"""Unit tests for run_pr_microbenchmarks.py."""

import unittest

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

    def test_recognizes_perf_commit_subjects(self):
        self.assertTrue(runner.is_perf_commit("perf: improve benchmark"))
        self.assertTrue(runner.is_perf_commit("perf(engine): improve benchmark"))
        self.assertFalse(runner.is_perf_commit("feat: improve benchmark"))


if __name__ == "__main__":
    unittest.main()
