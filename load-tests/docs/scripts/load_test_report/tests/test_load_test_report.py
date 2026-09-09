#!/usr/bin/env python3

from __future__ import annotations

import unittest

import load_test_report


class LoadTestReportTest(unittest.TestCase):
    def test_should_build_parser(self):
        parser = load_test_report.build_parser()

        self.assertEqual(parser.prog, "load_test_report.py")

    def test_should_run_without_arguments(self):
        self.assertEqual(load_test_report.run([]), 0)


if __name__ == "__main__":
    unittest.main()
