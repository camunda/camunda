"""Tests for load-test-physical-tenant-profiles.py. Run: python3 -m unittest <this file>."""

import importlib.util
import os
import pathlib
import sys
import tempfile
import unittest
import unittest.mock

_spec = importlib.util.spec_from_file_location(
    "profiles", pathlib.Path(__file__).with_name("load-test-physical-tenant-profiles.py")
)
profiles = importlib.util.module_from_spec(_spec)
sys.modules["profiles"] = profiles
_spec.loader.exec_module(profiles)

DEFAULTS = dict(
    tenant_count=10,
    saturating_count=1,
    capacity=300,
    saturating_multiplier=3,
    quiet_multiplier=0.2,
    weights="",
    overrides="",
    total_rate=0,
)


def run(**changes):
    return {t.name: t for t in profiles.compute(**{**DEFAULTS, **changes})}


class ComputeTest(unittest.TestCase):
    def test_saturating_tenant_offers_a_multiple_of_its_allowance(self):
        tenants = run()
        self.assertAlmostEqual(tenants["pt1"].rate, 300 / 11 * 3)
        self.assertTrue(tenants["pt1"].pinned)

    def test_quiet_tenants_run_at_a_fraction_of_their_allowance(self):
        tenants = run()
        # 300 / 11 tenants * 0.2
        self.assertAlmostEqual(tenants["pt5"].rate, 300 / 11 * 0.2)
        self.assertAlmostEqual(tenants["default"].rate, tenants["pt5"].rate)

    def test_weight_changes_the_allowance(self):
        tenants = run(tenant_count=1, saturating_count=0, weights="pt1=3")
        self.assertAlmostEqual(tenants["pt1"].allowance, 225)
        self.assertAlmostEqual(tenants["default"].allowance, 75)

    def test_override_is_a_multiplier_of_the_allowance_and_pinned(self):
        tenants = run(saturating_count=0, overrides="pt3=4")
        self.assertAlmostEqual(tenants["pt3"].rate, 300 / 11 * 4)
        self.assertTrue(tenants["pt3"].pinned)

    def test_reference_run_has_no_saturating_tenant(self):
        self.assertTrue(all(t.role != "saturating" for t in run(saturating_count=0).values()))

    def test_total_rate_scales_only_unpinned_tenants(self):
        tenants = run(saturating_count=0, overrides="pt1=1", total_rate=100)
        self.assertEqual(sum(profiles.whole_rate(t.rate) for t in tenants.values()), 100)
        self.assertAlmostEqual(tenants["pt1"].rate, 300 / 11)
        self.assertFalse(tenants["pt1"].scaled)
        self.assertTrue(tenants["pt2"].scaled)

    def test_single_tenant_reference_takes_the_total_rate(self):
        tenants = run(tenant_count=0, saturating_count=0, total_rate=210)
        self.assertEqual(tenants["default"].rate, 210)

    def test_total_rate_keeps_the_saturating_tenant_and_scales_the_quiet_ones(self):
        tenants = run(total_rate=300)
        self.assertEqual(sum(profiles.whole_rate(t.rate) for t in tenants.values()), 300)
        self.assertAlmostEqual(tenants["pt1"].rate, 300 / 11 * 3)

    def test_total_rate_is_not_inflated_by_rounding_each_tenant_up(self):
        tenants = run(saturating_count=0, total_rate=101)
        self.assertEqual(sum(profiles.whole_rate(t.rate) for t in tenants.values()), 101)
        self.assertEqual(sorted({profiles.whole_rate(t.rate) for t in tenants.values()}), [9, 10])

    def test_total_rate_that_gives_a_tenant_less_than_one_is_rejected(self):
        with self.assertRaisesRegex(profiles.InputError, "at least 1 PI/s each"):
            run(saturating_count=0, total_rate=5)

    def test_total_rate_must_be_whole(self):
        with self.assertRaisesRegex(profiles.InputError, "whole number"):
            run(saturating_count=0, total_rate=100.5)

    def test_total_rate_below_pinned_load_is_rejected(self):
        with self.assertRaisesRegex(profiles.InputError, "Raise total-rate or lower the pinned load"):
            run(total_rate=50)

    def test_unknown_tenant_in_overrides_is_rejected(self):
        with self.assertRaisesRegex(profiles.InputError, "unknown tenant"):
            run(overrides="pt99=2")

    def test_non_positive_or_bad_values_are_rejected(self):
        for bad in ("pt1=0", "pt1=-1", "pt1=abc", "pt1", "pt1=2,pt1=3"):
            with self.subTest(bad), self.assertRaises(profiles.InputError):
                run(saturating_count=0, overrides=bad)

    def test_more_saturating_tenants_than_tenants_is_rejected(self):
        with self.assertRaises(profiles.InputError):
            run(tenant_count=2, saturating_count=3)


class RenderTest(unittest.TestCase):
    def test_profiles_and_default_flags(self):
        tenants = profiles.compute(**{**DEFAULTS, "tenant_count": 2})
        out, default_flags, overload, table = profiles.render(tenants, 300, 4)
        self.assertEqual(out, "300:12 20:1")
        self.assertEqual(
            default_flags, "--set load-tester.starter.rate=20 --set load-tester.workers.worker.replicas=1"
        )
        self.assertTrue(overload)
        self.assertIn("overload", table)

    def test_run_within_capacity_is_not_flagged(self):
        tenants = profiles.compute(**{**DEFAULTS, "saturating_count": 0})
        self.assertFalse(profiles.render(tenants, 300, 4)[2])


class MainTest(unittest.TestCase):
    def run_main(self, tenant_count, saturating=1):
        directory = pathlib.Path(tempfile.mkdtemp())
        env = {
            "TENANT_COUNT": str(tenant_count), "SATURATING_COUNT": str(saturating), "SATURATING_MULTIPLIER": "3",
            "QUIET_MULTIPLIER": "0.2", "WORKER_REPLICAS_PER_100_PI": "0.4", "STORAGE": "elasticsearch",
            "CAPACITY_RATE": "", "PREFER_REST": "true", "TOTAL_RATE": "0", "TENANT_WEIGHTS": "", "TENANT_OVERRIDES": "",
            "GITHUB_OUTPUT": str(directory / "out"), "GITHUB_STEP_SUMMARY": str(directory / "summary"),
        }
        with unittest.mock.patch.dict(os.environ, env):
            code = profiles.main()
        out = (directory / "out").read_text() if (directory / "out").exists() else ""
        return code, out

    def test_tenant_count_includes_the_default_tenant(self):
        code, out = self.run_main(10)
        self.assertEqual(code, 0)
        self.assertIn("physical-tenant-count=9\n", out)
        self.assertEqual(len(out.split("profiles=")[1].split("\n")[0].split()), 9)

    def test_a_single_tenant_has_no_physical_tenants(self):
        self.assertIn("physical-tenant-count=0\n", self.run_main(1, saturating=0)[1])

    def test_a_single_tenant_cannot_also_be_saturating(self):
        self.assertEqual(self.run_main(1)[0], 1)

    def test_zero_tenants_is_rejected(self):
        self.assertEqual(self.run_main(0)[0], 1)


class WorkersTest(unittest.TestCase):
    def test_workers_scale_linearly_with_the_rate_and_round_up(self):
        self.assertEqual(profiles.workers(900, 4), 36)
        self.assertEqual(profiles.workers(901, 4), 37)

    def test_default_ratio_gives_three_workers_for_the_saturating_tenant(self):
        self.assertEqual(profiles.workers(720, 0.4), 3)
        self.assertEqual(profiles.workers(800, 0.4), 4)

    def test_every_tenant_has_at_least_one_worker(self):
        self.assertEqual(profiles.workers(1, 4), 1)


class CapacityTest(unittest.TestCase):
    def test_grpc_uses_the_estimate(self):
        self.assertEqual(
            profiles.capacity_of("elasticsearch", "", rest=False), profiles.ESTIMATED_GRPC_CAPACITY["elasticsearch"]
        )

    def test_rest_has_less_capacity_than_grpc(self):
        grpc = profiles.capacity_of("elasticsearch", "", rest=False)
        self.assertAlmostEqual(profiles.capacity_of("elasticsearch", "", rest=True), grpc * 0.65)

    def test_a_given_capacity_wins_for_both_apis(self):
        self.assertEqual(profiles.capacity_of("postgresql", "120", rest=True), 120)
        self.assertEqual(profiles.capacity_of("elasticsearch", "120", rest=False), 120)

    def test_postgresql_over_rest_uses_the_elasticsearch_estimate(self):
        self.assertEqual(profiles.capacity_of("postgresql", "", rest=True), profiles.capacity_of("elasticsearch", "", rest=True))

    def test_postgresql_over_grpc_uses_the_elasticsearch_estimate(self):
        self.assertEqual(profiles.capacity_of("postgresql", "", rest=False), profiles.capacity_of("elasticsearch", "", rest=False))

    def test_storage_without_an_estimate_needs_a_given_capacity(self):
        with self.assertRaisesRegex(profiles.InputError, "required for secondary storage 'mysql'"):
            profiles.capacity_of("mysql", "")


if __name__ == "__main__":
    unittest.main()
