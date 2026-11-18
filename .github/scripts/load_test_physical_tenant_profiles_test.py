"""Tests for load-test-physical-tenant-profiles.py. Run: python3 -m unittest <this file>."""

import importlib.util
import pathlib
import sys
import unittest

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
    saturating_ratio=1.5,
    quiet_multiplier=0.2,
    weights="",
    overrides="",
    total_rate=0,
)


def run(**changes):
    return {t.name: t for t in profiles.compute(**{**DEFAULTS, **changes})}


class ComputeTest(unittest.TestCase):
    def test_saturating_tenant_offers_more_than_the_cluster_capacity(self):
        tenants = run()
        self.assertAlmostEqual(tenants["pt1"].rate, 450)
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
        self.assertAlmostEqual(sum(t.rate for t in tenants.values()), 100)
        self.assertAlmostEqual(tenants["pt1"].rate, 300 / 11)
        self.assertFalse(tenants["pt1"].scaled)
        self.assertTrue(tenants["pt2"].scaled)

    def test_single_tenant_reference_takes_the_total_rate(self):
        tenants = run(tenant_count=0, saturating_count=0, total_rate=210)
        self.assertAlmostEqual(tenants["default"].rate, 210)

    def test_total_rate_below_pinned_load_is_rejected(self):
        with self.assertRaisesRegex(profiles.InputError, "Overload runs cannot use total-rate"):
            run(total_rate=100)

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
        out, default_flags, overload, table = profiles.render(tenants, 300)
        self.assertEqual(out, "450 20")
        self.assertEqual(default_flags, "--set load-tester.starter.rate=20")
        self.assertTrue(overload)
        self.assertIn("overload", table)

    def test_run_within_capacity_is_not_flagged(self):
        tenants = profiles.compute(**{**DEFAULTS, "saturating_count": 0})
        self.assertFalse(profiles.render(tenants, 300)[2])


class CapacityTest(unittest.TestCase):
    def test_elasticsearch_uses_the_estimate(self):
        self.assertEqual(profiles.capacity_of("elasticsearch", ""), profiles.ESTIMATED_CAPACITY["elasticsearch"])

    def test_a_given_capacity_wins(self):
        self.assertEqual(profiles.capacity_of("postgresql", "120"), 120)

    def test_storage_without_an_estimate_needs_a_given_capacity(self):
        with self.assertRaisesRegex(profiles.InputError, "required for secondary storage 'postgresql'"):
            profiles.capacity_of("postgresql", "")


if __name__ == "__main__":
    unittest.main()
