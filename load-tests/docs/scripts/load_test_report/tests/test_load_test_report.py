#!/usr/bin/env python3

from __future__ import annotations

import io
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import load_test_report
import prometheus

PROJECT_DIR = Path(__file__).resolve().parents[1]


class FakePrometheusClient:
    def __init__(self, responses):
        self.responses = list(responses)
        self.queries = []

    def query(self, query):
        self.queries.append(query)
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return response


class FakeHttpResponse:
    def __init__(self, payload):
        self.payload = payload.encode("utf-8")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        return False

    def read(self):
        return self.payload


class ExtractMetricValueTest(unittest.TestCase):
    def test_should_extract_numeric_sample(self):
        response = {
            "status": "success",
            "data": {
                "resultType": "vector",
                "result": [
                    {
                        "metric": {
                            "__name__": "up",
                            "job": "prometheus",
                            "instance": "localhost:9090",
                        },
                        "value": [1435781451.781, "42.5"],
                    }
                ],
            },
        }

        self.assertEqual(load_test_report.extract_metric_value(response, "", "throughput"), 42.5)

    def test_should_extract_sorted_unique_label_values(self):
        response = {
            "status": "success",
            "data": {
                "resultType": "vector",
                "result": [
                    {"metric": {"image": "registry/camunda:2"}, "value": [1435781451.781, "1"]},
                    {"metric": {"image": "registry/camunda:1"}, "value": [1435781451.781, "1"]},
                    {"metric": {"image": "registry/camunda:2"}, "value": [1435781451.781, "1"]},
                ],
            },
        }

        self.assertEqual(
            load_test_report.extract_metric_value(response, "image", "image"),
            "registry/camunda:1, registry/camunda:2",
        )

    def test_should_warn_when_numeric_sample_is_missing(self):
        response = {"status": "success", "data": {"result": []}}
        warnings = []

        with self.assertRaisesRegex(load_test_report.MissingMetric, "no numeric sample"):
            load_test_report.extract_metric_value(response, "", "throughput", warnings.append)

        self.assertEqual(warnings, ["throughput: no numeric sample"])

    def test_should_warn_when_label_sample_is_missing(self):
        response = {"status": "success", "data": {"result": []}}
        warnings = []

        with self.assertRaisesRegex(load_test_report.MissingMetric, "no label sample"):
            load_test_report.extract_metric_value(response, "image", "image", warnings.append)

        self.assertEqual(warnings, ["image: no label sample"])

    def test_should_warn_when_prometheus_status_is_not_success(self):
        response = {"status": "error", "data": {"result": []}}
        warnings = []

        with self.assertRaisesRegex(load_test_report.MissingMetric, "Prometheus returned non-success status"):
            load_test_report.extract_metric_value(response, "", "throughput", warnings.append)

        self.assertEqual(warnings, ["throughput: Prometheus returned non-success status"])


class BuildReportTest(unittest.TestCase):
    def test_should_build_report_without_network_side_effects(self):
        options = load_test_report.Options(
            namespace="c8-ck-test",
            duration_seconds=900,
            rate_interval="30s",
            sample_step="15s",
            endpoint="http://prometheus.example",
            bearer_token="",
            basic_auth_user="",
            basic_auth_password="",
            time_anchor="",
            start_label="",
            end_label="",
            output_format="json",
            include_header=True,
            missing_value="NaN",
            queries_source="custom",
            queries_file=Path("queries.json"),
            output_file=None,
        )
        query_document = {
            "queries": [
                {"key": "namespace", "header": "Namespace", "value": "c8-ck-test"},
                {
                    "key": "throughput",
                    "header": "Throughput",
                    "query": 'rate(total{namespace="c8-ck-test"}[30s])',
                },
                {
                    "key": "image",
                    "header": "Image",
                    "query": "image_query[900s:15s]",
                    "valueLabel": "image",
                },
                {"key": "missing", "header": "Missing", "query": "missing_query"},
            ]
        }
        client = FakePrometheusClient(
            [
                {"status": "success", "data": {"result": [{"value": [123, "10"]}]}},
                {"status": "success", "data": {"result": [{"metric": {"image": "camunda:SNAPSHOT"}}]}},
                {"status": "success", "data": {"result": []}},
            ]
        )
        warnings = []

        report = load_test_report.build_report(
            options,
            query_document,
            client,
            generated_at="2026-09-07T18:00:00Z",
            warning_sink=warnings.append,
        )

        self.assertEqual(
            client.queries,
            [
                'rate(total{namespace="c8-ck-test"}[30s])',
                "image_query[900s:15s]",
                "missing_query",
            ],
        )
        self.assertEqual(report["metrics"]["namespace"], "c8-ck-test")
        self.assertEqual(report["metrics"]["throughput"], 10)
        self.assertEqual(report["metrics"]["image"], "camunda:SNAPSHOT")
        self.assertIsNone(report["metrics"]["missing"])
        self.assertNotIn("missing", report)
        self.assertEqual(warnings, ["missing: no numeric sample"])

    def test_should_warn_when_query_fails(self):
        options = load_test_report.Options(
            namespace="c8-ck-test",
            duration_seconds=900,
            rate_interval="30s",
            sample_step="15s",
            endpoint="http://prometheus.example",
            bearer_token="",
            basic_auth_user="",
            basic_auth_password="",
            time_anchor="",
            start_label="",
            end_label="",
            output_format="json",
            include_header=True,
            missing_value="NaN",
            queries_source="custom",
            queries_file=Path("queries.json"),
            output_file=None,
        )
        query_document = {"queries": [{"key": "throughput", "query": "throughput_query"}]}
        client = FakePrometheusClient([load_test_report.ReportError("query failed")])
        warnings = []

        report = load_test_report.build_report(options, query_document, client, warning_sink=warnings.append)

        self.assertIsNone(report["metrics"]["throughput"])
        self.assertNotIn("missing", report)
        self.assertEqual(warnings, ["throughput: query failed"])


class AuthHeadersTest(unittest.TestCase):
    def test_should_build_basic_auth_header(self):
        headers = load_test_report.auth_headers("", "user", "pass")

        self.assertEqual(headers["Authorization"], "Basic dXNlcjpwYXNz")

    def test_should_build_bearer_auth_header(self):
        headers = load_test_report.auth_headers("abc123", "", "")

        self.assertEqual(headers["Authorization"], "Bearer abc123")

    def test_should_reject_combined_auth_modes(self):
        with self.assertRaisesRegex(load_test_report.ReportError, "--token cannot be combined"):
            load_test_report.auth_headers("abc123", "user", "pass")

    def test_should_reject_incomplete_basic_auth(self):
        with self.assertRaisesRegex(load_test_report.ReportError, "--user and --password"):
            load_test_report.auth_headers("", "user", "")


class PrometheusClientTest(unittest.TestCase):
    def test_should_query_prometheus_with_urlencoded_query_and_headers(self):
        seen_requests = []

        def fake_urlopen(request, timeout):
            seen_requests.append((request, timeout))
            return FakeHttpResponse('{"status":"success","data":{"result":[]}}')

        with mock.patch.object(prometheus, "urlopen", side_effect=fake_urlopen):
            client = load_test_report.PrometheusClient(
                "https://prometheus.example/",
                "",
                "user",
                "pass",
                "2026-09-07T18:00:00Z",
            )

            response = client.query('rate(total{namespace="c8-ck-test"}[5m])')

        self.assertEqual(response["status"], "success")
        request, timeout = seen_requests[0]
        self.assertEqual(timeout, 30)
        self.assertIn("query=rate%28total%7Bnamespace%3D%22c8-ck-test%22%7D%5B5m%5D%29", request.full_url)
        self.assertIn("time=2026-09-07T18%3A00%3A00Z", request.full_url)
        self.assertEqual(request.get_header("Authorization"), "Basic dXNlcjpwYXNz")


class LoadQueryDocumentTest(unittest.TestCase):
    def test_should_load_yaml_query_file_with_pyyaml(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.yaml"
            queries_file.write_text("queries:\n- key: namespace\n  value: $NAMESPACE\n", encoding="utf-8")

            document = load_test_report.load_query_document(queries_file, {"$NAMESPACE": "c8-ck-test"})

        self.assertEqual(document["queries"][0]["key"], "namespace")
        self.assertEqual(document["queries"][0]["value"], "c8-ck-test")

    def test_should_substitute_query_file_before_json_decoding(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.json"
            queries_file.write_text(
                '{"queries":[{"key":"throughput","query":"rate(total{namespace=\\"$NAMESPACE\\"}[$RATE_INTERVAL])"}]}',
                encoding="utf-8",
            )

            document = load_test_report.load_query_document(
                queries_file,
                {"$NAMESPACE": "c8-ck-test", "$RATE_INTERVAL": "30s"},
            )

        self.assertEqual(document["queries"][0]["query"], 'rate(total{namespace="c8-ck-test"}[30s])')

    def test_should_report_invalid_json_query_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.json"
            queries_file.write_text('{"queries":[', encoding="utf-8")

            with self.assertRaisesRegex(load_test_report.ReportError, "Could not parse queries file"):
                load_test_report.load_query_document(queries_file, {})

    def test_should_reject_legacy_load_test_metrics_schema(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.yaml"
            queries_file.write_text(
                """queries:
- name: throughput-per-second
  description: Client Process Instance Started Rate (avg)
  query: numeric_metric{namespace="$NAMESPACE"}
""",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(load_test_report.ReportError, "non-empty string key"):
                load_test_report.load_query_document(queries_file, {})

    def test_should_reject_duplicate_query_keys(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.yaml"
            queries_file.write_text(
                """queries:
- key: duplicate_metric
  header: Duplicate 1
  query: numeric_metric{namespace="$NAMESPACE"}
- key: duplicate_metric
  header: Duplicate 2
  value: static
""",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(load_test_report.ReportError, "duplicate query key: duplicate_metric"):
                load_test_report.load_query_document(queries_file, {})

    def test_should_reject_entries_without_exactly_one_value_source(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.yaml"
            queries_file.write_text(
                """queries:
- key: missing_source
  header: Missing source
""",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(load_test_report.ReportError, "must set exactly one of query or value"):
                load_test_report.load_query_document(queries_file, {})

    def test_should_reject_value_label_without_query(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.yaml"
            queries_file.write_text(
                """queries:
- key: invalid_label
  header: Invalid label
  value: static
  valueLabel: image
""",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(load_test_report.ReportError, "sets valueLabel without query"):
                load_test_report.load_query_document(queries_file, {})

    def test_should_load_builtin_query_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "report-queries.yaml"
            queries_file.write_text(
                """queries:
- key: namespace
  value: $NAMESPACE

- key: throughput
  query: rate(total{namespace="$NAMESPACE"}[30s])
""",
                encoding="utf-8",
            )

            document = load_test_report.load_query_document(
                queries_file,
                {"$NAMESPACE": "c8-ck-test"},
            )

        self.assertEqual([query["key"] for query in document["queries"]], ["namespace", "throughput"])
        self.assertEqual(document["queries"][0]["value"], "c8-ck-test")
        self.assertEqual(document["queries"][1]["query"], 'rate(total{namespace="c8-ck-test"}[30s])')

    def test_should_reject_duplicate_keys_in_query_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "report-queries.yaml"
            queries_file.write_text(
                """queries:
- key: duplicate
  value: first

- key: duplicate
  value: second
""",
                encoding="utf-8",
            )

            with self.assertRaisesRegex(load_test_report.ReportError, "duplicate query key: duplicate"):
                load_test_report.load_query_document(queries_file, {})

    def test_should_load_all_builtin_query_files(self):
        script_dir = PROJECT_DIR
        substitutions = {
            "$NAMESPACE": "c8-ck-test",
            "$DURATION_S": "600s",
            "$RATE_INTERVAL": "5m",
            "$SAMPLE_STEP": "1m",
        }

        for queries_name, query_file_name in load_test_report.BUILTIN_QUERY_FILES.items():
            with self.subTest(queries_name=queries_name):
                document = load_test_report.load_query_document(script_dir / query_file_name, substitutions)
                self.assertGreater(len(document["queries"]), 0)

    def test_should_keep_builtin_spreadsheet_column_order(self):
        script_dir = PROJECT_DIR
        substitutions = {
            "$NAMESPACE": "c8-ck-test",
            "$DURATION_S": "600s",
            "$RATE_INTERVAL": "5m",
            "$SAMPLE_STEP": "1m",
        }
        expected_columns = {
            "camunda": [
                ("namespace", "Namespace"),
                ("docker_image", "Version"),
                ("camunda_nodes", "Nodes"),
                ("secondary_storage_nodes", "Nodes"),
                ("camunda_cpu_limit", "limit"),
                ("camunda_cpu_used_p50", "used (p50)"),
                ("camunda_cpu_used_p99", "used(p99)"),
                ("camunda_cpu_throttling_percent", "throttling (%)"),
                ("camunda_memory_limit_gib", "limit"),
                ("camunda_memory_rss_gib", "RSS (GB)"),
                ("camunda_heap_gib", "heap"),
                ("camunda_disk_capacity_gib", "capacity"),
                ("camunda_disk_used_gib", "used"),
                ("camunda_write_iops", "write IOPS"),
                ("secondary_storage_cpu_limit", "limit"),
                ("secondary_storage_cpu_used_p50", "used (p50)"),
                ("secondary_storage_cpu_used_p99", "used(p99)"),
                ("secondary_storage_cpu_throttling_percent", "throttling (%)"),
                ("secondary_storage_memory_limit_gib", "limit"),
                ("secondary_storage_memory_rss_gib", "RSS (GB)"),
                ("secondary_storage_heap_gib", "heap (GB)"),
                ("secondary_storage_disk_capacity_gib", "capacity"),
                ("secondary_storage_disk_used_gib", "used"),
                ("secondary_storage_write_iops", "write IOPS"),
                ("records_processed_per_second", "Proc rec"),
                ("records_exported_per_second", "Export rec"),
                ("secondary_storage_indexed_documents_per_second", "ES idx docs"),
                ("process_instances_per_second", "PI"),
                ("archived_process_instances_per_second", "Archived PI"),
                ("flow_node_instances_per_second", "FNI"),
                ("service_task_instances_per_second", "STI"),
                ("jobs_per_second", "Job"),
                ("processing_latency_p50_ms", "ProcLat p50 (ms)"),
                ("processing_latency_p99_ms", "ProcLat p99 (ms)"),
                ("exporting_latency_p50_ms", "ExportLat p50 (ms)"),
                ("exporting_latency_p99_ms", "ExportLat p99 (ms)"),
                ("process_instance_execution_p50_ms", "PIExec p50"),
                ("process_instance_execution_p99_ms", "PIExec p99"),
                ("data_availability_p50_seconds", "DataAvail p50 (sec)"),
                ("data_availability_p99_seconds", "DataAvail p99 (sec)"),
                ("response_latency_p50_ms", "Response p50 (ms)"),
                ("response_latency_p99_ms", "Response p99 (ms)"),
                ("backpressure_percent", "Backpressure (max of 3)"),
                ("processing_backlog", "Proc backlog"),
                ("exporting_backlog", "Export backlog"),
            ],
            "stable-87": [
                ("namespace", "Namespace"),
                ("docker_image", "Version"),
                ("zeebe_nodes", "Nodes"),
                ("zeebe_gateway_nodes", "Nodes"),
                ("secondary_storage_nodes", "Nodes"),
                ("zeebe_cpu_limit", "limit"),
                ("zeebe_cpu_used_p50", "used (p50)"),
                ("zeebe_cpu_used_p99", "used(p99)"),
                ("zeebe_cpu_throttling_percent", "throttling (%)"),
                ("zeebe_memory_limit_gib", "limit"),
                ("zeebe_memory_rss_gib", "RSS (GB)"),
                ("zeebe_heap_gib", "heap"),
                ("zeebe_gateway_cpu_limit", "limit"),
                ("zeebe_gateway_cpu_used_p50", "used (p50)"),
                ("zeebe_gateway_cpu_used_p99", "used(p99)"),
                ("zeebe_gateway_cpu_throttling_percent", "throttling (%)"),
                ("zeebe_gateway_memory_limit_gib", "limit"),
                ("zeebe_gateway_memory_rss_gib", "RSS (GB)"),
                ("zeebe_gateway_heap_gib", "heap"),
                ("zeebe_disk_capacity_gib", "capacity"),
                ("zeebe_disk_used_gib", "used"),
                ("zeebe_write_iops", "write IOPS"),
                ("secondary_storage_cpu_limit", "limit"),
                ("secondary_storage_cpu_used_p50", "used (p50)"),
                ("secondary_storage_cpu_used_p99", "used(p99)"),
                ("secondary_storage_cpu_throttling_percent", "throttling (%)"),
                ("secondary_storage_memory_limit_gib", "limit"),
                ("secondary_storage_memory_rss_gib", "RSS (GB)"),
                ("secondary_storage_heap_gib", "heap (GB)"),
                ("secondary_storage_disk_capacity_gib", "capacity"),
                ("secondary_storage_disk_used_gib", "used"),
                ("secondary_storage_write_iops", "write IOPS"),
                ("records_processed_per_second", "Proc rec"),
                ("records_exported_per_second", "Export rec"),
                ("secondary_storage_indexed_documents_per_second", "ES idx docs"),
                ("process_instances_per_second", "PI"),
                ("archived_process_instances_per_second", "Archived PI"),
                ("flow_node_instances_per_second", "FNI"),
                ("service_task_instances_per_second", "STI"),
                ("jobs_per_second", "Job"),
                ("processing_latency_p50_ms", "ProcLat p50 (ms)"),
                ("processing_latency_p99_ms", "ProcLat p99 (ms)"),
                ("exporting_latency_p50_ms", "ExportLat p50 (ms)"),
                ("exporting_latency_p99_ms", "ExportLat p99 (ms)"),
                ("process_instance_execution_p50_ms", "PIExec p50"),
                ("process_instance_execution_p99_ms", "PIExec p99"),
                ("data_availability_p50_seconds", "DataAvail p50 (sec)"),
                ("data_availability_p99_seconds", "DataAvail p99 (sec)"),
                ("response_latency_p50_ms", "Response p50 (ms)"),
                ("response_latency_p99_ms", "Response p99 (ms)"),
                ("backpressure_percent", "Backpressure (max of 3)"),
                ("processing_backlog", "Proc backlog"),
                ("exporting_backlog", "Export backlog"),
            ],
        }

        for queries_name, query_file_name in load_test_report.BUILTIN_QUERY_FILES.items():
            with self.subTest(queries_name=queries_name):
                document = load_test_report.load_query_document(script_dir / query_file_name, substitutions)

                self.assertEqual(
                    [(query["key"], query["header"]) for query in document["queries"]],
                    expected_columns[queries_name],
                )

    def test_should_keep_builtin_descriptions_before_headers(self):
        script_dir = PROJECT_DIR

        for query_file_name in load_test_report.BUILTIN_QUERY_FILES.values():
            with self.subTest(query_file_name=query_file_name):
                query_text = (script_dir / query_file_name).read_text(encoding="utf-8").split("queries:\n", 1)[1]
                entries = [entry for entry in query_text.split("\n\n") if entry.startswith("  - key:")]

                self.assertGreater(len(entries), 0)
                for entry in entries:
                    description_index = entry.find("\n    description:")
                    header_index = entry.find("\n    header:")
                    self.assertNotEqual(description_index, -1)
                    self.assertNotEqual(header_index, -1)
                    self.assertLess(description_index, header_index)

    def test_should_resolve_stable_87_builtin_queries(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            Path(temp_dir, "report-queries-stable-87.yaml").write_text(
                """queries:
- key: namespace
  value: $NAMESPACE
""",
                encoding="utf-8",
            )

            self.assertEqual(
                load_test_report.resolve_queries_file(Path(temp_dir), "stable-87"),
                Path(temp_dir) / "report-queries-stable-87.yaml",
            )

    def test_should_resolve_custom_queries_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.yaml"
            queries_file.write_text(
                """queries:
- key: namespace
  value: $NAMESPACE
""",
                encoding="utf-8",
            )

            self.assertEqual(
                load_test_report.resolve_queries_file(Path(temp_dir), str(queries_file)),
                queries_file,
            )


class WarningTest(unittest.TestCase):
    def test_should_prefix_default_stderr_warnings(self):
        stderr = io.StringIO()

        with mock.patch.object(sys, "stderr", stderr):
            load_test_report.warn("throughput: no numeric sample")

        self.assertEqual(stderr.getvalue(), "Warning: throughput: no numeric sample\n")


class RenderReportTest(unittest.TestCase):
    def test_should_render_tsv_without_header(self):
        report = {
            "columns": ["namespace", "throughput", "missing"],
            "headers": ["Namespace", "Throughput", "Missing"],
            "metrics": {"namespace": "c8-ck-test", "throughput": 10, "missing": None},
        }

        rendered = load_test_report.render_report(report, "tsv", include_header=False, missing_value="NaN")

        self.assertEqual(rendered, "c8-ck-test\t10\tNaN")

    def test_should_quote_csv_cells(self):
        report = {
            "columns": ["namespace", "image"],
            "headers": ["Namespace", "Image"],
            "metrics": {"namespace": "c8-ck-test", "image": "camunda:1, camunda:2"},
        }

        rendered = load_test_report.render_report(report, "csv", include_header=True, missing_value="NaN")

        self.assertEqual(rendered, 'Namespace,Image\nc8-ck-test,"camunda:1, camunda:2"')


class ParseArgsTest(unittest.TestCase):
    def test_should_parse_auth_flags(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.json"
            queries_file.write_text('{"queries":[]}', encoding="utf-8")

            options = load_test_report.parse_args(
                [
                    "c8-ck-test",
                    "--token",
                    "abc123",
                    "--queries",
                    str(queries_file),
                ],
                Path(temp_dir),
            )

        self.assertEqual(options.bearer_token, "abc123")
        self.assertEqual(options.basic_auth_user, "")
        self.assertEqual(options.basic_auth_password, "")
        self.assertEqual(options.queries_source, str(queries_file))
        self.assertEqual(options.queries_file, queries_file)

    def test_should_derive_duration_from_start_and_end(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            queries_file = Path(temp_dir) / "queries.json"
            queries_file.write_text('{"queries":[]}', encoding="utf-8")

            options = load_test_report.parse_args(
                [
                    "c8-ck-test",
                    "--start",
                    "2026-08-14T10:00:00Z",
                    "--end",
                    "2026-08-14T10:30:00Z",
                    "--queries",
                    str(queries_file),
                ],
                Path(temp_dir),
            )

        self.assertEqual(options.duration_seconds, 1800)
        self.assertEqual(options.time_anchor, "2026-08-14T10:30:00Z")
        self.assertEqual(options.start_label, "2026-08-14T10:00:00Z")
        self.assertEqual(options.end_label, "2026-08-14T10:30:00Z")

    def test_should_use_camunda_queries_by_default(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            Path(temp_dir, "report-queries.yaml").write_text(
                """queries:
- key: namespace
  value: test
""",
                encoding="utf-8",
            )
            options = load_test_report.parse_args(["c8-ck-test"], Path(temp_dir))

        self.assertEqual(options.queries_source, "camunda")
        self.assertEqual(options.queries_file.name, "report-queries.yaml")

    def test_should_use_stable_87_queries_file(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            Path(temp_dir, "report-queries-stable-87.yaml").write_text(
                """queries:
- key: namespace
  value: test
""",
                encoding="utf-8",
            )

            options = load_test_report.parse_args(["c8-ck-test", "--queries", "stable-87"], Path(temp_dir))

        self.assertEqual(options.queries_source, "stable-87")
        self.assertEqual(options.queries_file.name, "report-queries-stable-87.yaml")

    def test_should_reject_unknown_queries_source(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            with self.assertRaisesRegex(load_test_report.ReportError, "Unsupported --queries"):
                load_test_report.parse_args(
                    ["c8-ck-test", "--queries", "daily"],
                    Path(temp_dir),
                )


if __name__ == "__main__":
    unittest.main()
