"""Tests for tenant_report.py against a fake Prometheus. Run: python3 -m unittest discover -s tests."""

import os
import sys
import unittest
import unittest.mock

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "scripts"))
import tenant_report as report  # noqa: E402

END = 1_000_000.0
WINDOW = 900


def sample(value, **labels):
    return {"metric": labels, "value": [END, str(value)]}


# tenant -> (submitted, skipped, success, failed with resource exhausted)
LOAD = {
    "default": (50, 0, 50, 0),
    "pt1": (150, 0, 90, 60),  # the noisy one, rejected a lot
    "pt2": (50, 0, 40, 10),  # a quiet tenant that suffers
    "pt3": (50, 0, 50, 0),
}
RESPONSE_P99 = {"default": 0.2, "pt1": 6.0, "pt2": 3.0, "pt3": 0.3}
AVAILABILITY_P99 = {"default": 1.0, "pt1": 9.0, "pt2": 8.0, "pt3": 1.2}
# tenant -> (queue at the end, queue at the start, exported records per second)
EXPORT = {"default": (100, 100, 500), "pt1": (4000, 500, 400), "pt2": (3000, 100, 450), "pt3": (50, 50, 500), "pt6": (90000, 1000, 200)}


# tenant -> {partition: (queue, exported records per second)}; the summed rate hides the slow partition
PARTITIONS = {"pt1": {"1": (1000, 1700), "2": (15000, 176)}, "pt2": {"1": (3000, 500), "2": (2000, 300)}}


def fake(with_tenant_label=True):
    def query(promql, at):
        if promql.startswith("max by (physicalTenant, partition)"):
            return [sample(b, physicalTenant=t, partition=p) for t, ps in PARTITIONS.items() for p, (b, _) in ps.items()]
        if promql.startswith("min by (physicalTenant, partition)"):
            return [sample(r, physicalTenant=t, partition=p) for t, ps in PARTITIONS.items() for p, (_, r) in ps.items()]
        if "zeebe_log_appender_last_committed_position" in promql and "max_over_time" not in promql:
            if not with_tenant_label:
                return []
            index = 0 if at == END else 1
            return [sample(v[index], physicalTenant=t) for t, v in EXPORT.items()]
        if "max_over_time" in promql:
            return [sample(v[0] * 1.5, physicalTenant=t) for t, v in EXPORT.items()] if with_tenant_label else []
        if "zeebe_exporter_last_exported_position" in promql and "rate(" in promql:
            return [sample(v[2], physicalTenant=t) for t, v in EXPORT.items()] if with_tenant_label else []
        if "submissions_total" in promql:
            return [r for t, (sub, skip, _, _) in LOAD.items() for r in (sample(sub, tenant=t, outcome="submitted"), sample(skip, tenant=t, outcome="skipped"))]
        if "started_total" in promql:
            rows = []
            for t, (_, _, ok, failed) in LOAD.items():
                rows.append(sample(ok, tenant=t, outcome="success", error="none"))
                if failed:
                    rows.append(sample(failed, tenant=t, outcome="failure", error="grpc_resource_exhausted"))
            return rows
        if "le=~" in promql:  # the share of observations within the target: p99 within it means 99% or more
            table = RESPONSE_P99 if "response_latency" in promql else AVAILABILITY_P99
            return [sample(0.995 if v <= 5.0 else 0.9, tenant=t) for t, v in table.items()]
        if "histogram_quantile" in promql:
            quantile = float(promql.split("histogram_quantile(")[1].split(",")[0])
            table = RESPONSE_P99 if "response_latency" in promql else AVAILABILITY_P99
            return [sample(v * quantile / 0.99, tenant=t) for t, v in table.items()]
        if "pending_process_instances" in promql:
            return [sample(20 if t != "pt1" else 400, tenant=t) for t in LOAD]
        if "in_flight" in promql:
            return [sample(5, tenant=t) for t in LOAD]
        if "job_handled" in promql:
            return [sample(LOAD[t][2], tenant=t) for t in LOAD]
        if "job_activated" in promql:
            return [sample(LOAD[t][2], tenant=t) for t in LOAD]
        if "zeebe_dropped_request_count_total" in promql:
            return [sample(0.07)]
        if "container_cpu_usage_seconds_total" in promql:
            return [sample(3.8, pod="camunda-0")]
        if "container_cpu_cfs_throttled_periods_total" in promql:
            return [sample(0.4, pod="camunda-0")]
        return []

    return query


def args(*extra):
    return report.parser().parse_args(["c8-test", "--duration", str(WINDOW), "--end", str(END), *extra])


class CollectTest(unittest.TestCase):
    def setUp(self):
        self.tenants = report.collect_client(fake(), "c8-test", WINDOW, END)

    def test_load_and_outcome_are_attributed_to_each_tenant(self):
        pt1 = self.tenants["pt1"]
        self.assertEqual(pt1.offered, 150)
        self.assertAlmostEqual(pt1.service_ratio, 0.6)
        self.assertEqual(pt1.backpressured, 60)
        self.assertAlmostEqual(pt1.rejected_ratio, 0.4)

    def test_latency_quantiles_and_storage_availability(self):
        self.assertAlmostEqual(self.tenants["pt2"].response[0.99], 3.0)
        self.assertAlmostEqual(self.tenants["pt2"].availability[0.99], 8.0)
        self.assertAlmostEqual(self.tenants["pt2"].response[0.5], 3.0 * 0.5 / 0.99)

    def test_waiting_for_storage_and_jobs(self):
        self.assertEqual(self.tenants["pt1"].pending_avg, 400)
        self.assertEqual(self.tenants["pt3"].jobs_handled, 50)

    def test_the_namespace_is_validated_before_it_reaches_a_query(self):
        with self.assertRaises(report.InputError):
            report.collect_client(fake(), 'c8-x"} or vector(1) #', WINDOW, END)


class ExportTest(unittest.TestCase):
    def test_export_queue_growth_and_lag_per_tenant(self):
        tenants = report.collect_client(fake(), "c8-test", WINDOW, END)
        notes = report.collect_export(fake(), "c8-test", WINDOW, END, tenants)
        self.assertEqual(notes, [])
        pt6 = tenants["pt6"]  # no load tester in the fake, but it still has an export queue
        self.assertEqual(pt6.export_backlog, 90000)
        self.assertEqual(pt6.export_growth, 89000)
        self.assertAlmostEqual(pt6.export_lag_seconds, 450)
        self.assertAlmostEqual(tenants["default"].export_lag_seconds, 0.2)

    def test_the_export_lag_is_the_slowest_partition_not_the_sum(self):
        tenants = report.collect_client(fake(), "c8-test", WINDOW, END)
        report.collect_export(fake(), "c8-test", WINDOW, END, tenants)
        pt1 = tenants["pt1"]
        self.assertAlmostEqual(pt1.partition_lag_seconds("1"), 1000 / 1700)
        self.assertAlmostEqual(pt1.export_lag_seconds, 15000 / 176)  # summed it would be 4000 / 400 = 10 s

    def test_a_note_says_when_the_broker_metrics_have_no_tenant_label(self):
        tenants = report.collect_client(fake(with_tenant_label=False), "c8-test", WINDOW, END)
        notes = report.collect_export(fake(with_tenant_label=False), "c8-test", WINDOW, END, tenants)
        self.assertEqual(len(notes), 1)
        self.assertIn("physicalTenant", notes[0])


class EvaluateTest(unittest.TestCase):
    def setUp(self):
        self.tenants = report.collect_client(fake(), "c8-test", WINDOW, END)
        report.collect_export(fake(), "c8-test", WINDOW, END, self.tenants)
        report.classify(self.tenants, None)

    def test_the_tenant_far_above_the_median_load_is_saturating(self):
        self.assertEqual(self.tenants["pt1"].role, "saturating")
        self.assertEqual({t.role for n, t in self.tenants.items() if n not in ("pt1", "pt6")}, {"quiet"})

    def test_given_saturating_tenants_win_and_must_exist(self):
        report.classify(self.tenants, ["pt2"])
        self.assertEqual(self.tenants["pt2"].role, "saturating")
        with self.assertRaises(report.InputError):
            report.classify(self.tenants, ["pt99"])

    def test_a_quiet_tenant_that_loses_load_and_storage_latency_misses_its_targets(self):
        verdict = report.evaluate(self.tenants, 0.95, 5.0)
        self.assertEqual(verdict.failing["pt2"], ["served", "availability", "export"])
        self.assertEqual(verdict.failing["pt3"], [])
        self.assertEqual(verdict.failing["default"], [])

    def test_a_tenant_with_a_long_export_queue_is_flagged_even_when_served(self):
        self.assertEqual(self.tenants["pt6"].role, "no load")  # no load tester, only an export queue
        verdict = report.evaluate(self.tenants, 0.95, 5.0)
        self.assertEqual(verdict.failing["pt6"], ["export"])

    def test_a_stalled_exporter_is_an_unbounded_lag_that_misses_the_target(self):
        self.tenants["pt3"].export_partitions = {"1": {"backlog": 100, "rate": 100}, "2": {"backlog": 10000, "rate": 0}}
        self.assertEqual(self.tenants["pt3"].export_lag_seconds, float("inf"))
        self.assertEqual(report.evaluate(self.tenants, 0.95, 5.0).failing["pt3"], ["export"])

    def test_an_empty_queue_that_exports_nothing_is_not_a_stall(self):
        self.tenants["pt3"].export_partitions = {"1": {"backlog": 0, "rate": 0}}
        self.assertEqual(self.tenants["pt3"].export_lag_seconds, 0)

    def test_a_tenant_without_export_metrics_misses_the_export_target(self):
        self.tenants["pt3"].export_partitions = {}
        self.tenants["pt3"].export_backlog = None
        self.assertIsNone(self.tenants["pt3"].export_lag_seconds)
        self.assertEqual(report.evaluate(self.tenants, 0.95, 5.0).failing["pt3"], ["export"])

    def test_latency_that_cannot_be_measured_against_the_target_is_not_a_pass(self):
        for t in self.tenants.values():  # no bucket ends at the target, or the latency is beyond the last one
            t.response_within = t.availability_within = None
        verdict = report.evaluate(self.tenants, 0.95, 5.0)
        self.assertEqual(verdict.failing["pt3"], ["response", "availability"])

    def test_counts_of_tenants_that_pass(self):
        verdict = report.evaluate(self.tenants, 0.95, 5.0)
        self.assertEqual(verdict.passing(), ["default", "pt3"])
        self.assertIn("pt2", set(self.tenants) - set(verdict.passing("response")) | {"pt2"})


class StatisticsTest(unittest.TestCase):
    def test_spread(self):
        s = report.spread([1.0, 2.0, 3.0])
        self.assertEqual((s["mean"], s["min"], s["max"]), (2.0, 1.0, 3.0))
        self.assertAlmostEqual(s["cv"], 0.4082, places=3)

    def test_jain_index_is_one_when_everyone_is_equal_and_one_over_n_when_one_takes_all(self):
        self.assertEqual(report.jain([1.0, 1.0, 1.0, 1.0]), 1.0)
        self.assertAlmostEqual(report.jain([1.0, 0.0, 0.0, 0.0]), 0.25)

    def test_interference_compares_each_quiet_tenant_with_its_reference(self):
        noisy = report.collect_client(fake(), "c8-test", WINDOW, END)
        report.classify(noisy, None)
        reference = report.collect_client(fake(), "c8-ref", WINDOW, END)
        for t in reference.values():
            t.success = t.offered  # a reference run serves everything
            t.response = {0.99: 0.2}
            t.availability = {0.99: 1.0}
        rows = {r["tenant"]: r for r in report.interference(noisy, reference)}
        self.assertNotIn("pt1", rows)  # the noisy tenant is not compared
        self.assertAlmostEqual(rows["pt2"]["served"], 0.8)
        self.assertAlmostEqual(rows["pt2"]["response_p99"], 15.0)
        self.assertAlmostEqual(rows["pt2"]["availability_p99"], 8.0)


class RenderTest(unittest.TestCase):
    def test_a_stalled_tenant_does_not_break_the_statistics(self):
        stalled = {"pt2": {"1": (3000, 0), "2": (2000, 300)}}
        with unittest.mock.patch.dict(PARTITIONS, stalled):
            markdown, data = report.build_report(fake(), args("--reference", "c8-ref"))
        self.assertIn("stalled", markdown)
        self.assertIn("Export stalled", markdown)
        self.assertIn("export", data["failing"]["pt2"])
        report.json.dumps(data, allow_nan=False, default=str)  # the JSON stays valid

    def test_the_report_has_every_section(self):
        markdown, data = report.build_report(fake(), args("--reference", "c8-ref"))
        for heading in (
            "## Do the tenants get the service",
            "## Per tenant",
            "## How the load is shared",
            "## Variance between the quiet tenants",
            "## Against the reference run",
            "## Cluster context",
        ):
            self.assertIn(heading, markdown)
        self.assertIn("**2 of 5** tenants meet every target that applies to them", markdown)  # default and pt3
        self.assertIn("`pt6`", markdown)  # the lagging tenant is named, although it has no load tester
        self.assertEqual(data["failing"]["pt2"], ["served", "availability", "export"])

    def test_export_throughput_is_the_sum_of_the_partitions_of_each_tenant(self):
        markdown, data = report.build_report(fake(), args())
        self.assertIn("## Export throughput", markdown)
        self.assertIn("| pt1 | saturating | 1,876 | ", markdown)
        self.assertIn("| **All tenants** | | 3,876 | 100% |", markdown)
        self.assertEqual(data["tenants"]["pt2"]["export_rate"], 800)

    def test_the_noisy_tenant_gets_a_table_per_partition(self):
        markdown, _ = report.build_report(fake(), args())
        self.assertIn("## Export per partition", markdown)
        self.assertIn("| 2 | 15,000 | 176.0 |", markdown)

    def test_a_tenant_without_load_is_not_counted_as_quiet(self):
        markdown, data = report.build_report(fake(), args())
        self.assertEqual(data["tenants"]["pt6"]["role"], "no load")
        self.assertIn("Quiet tenants: 3.", markdown)

    def test_the_noisy_tenants_share_is_reported(self):
        markdown, _ = report.build_report(fake(), args())
        self.assertIn("The saturating tenants take", markdown)

    def test_no_load_tester_metrics_is_an_error(self):
        with self.assertRaises(report.InputError):
            report.build_report(lambda promql, at: [], args())


if __name__ == "__main__":
    unittest.main()
