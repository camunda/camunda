import io
import sys
from pathlib import Path
from unittest import mock

import pytest
from pydantic import ValidationError

import load_test_report
from load_test_report import prometheus
from load_test_report.cli import Options
from load_test_report.cli import build_parser
from load_test_report.cli import parse_args
from load_test_report.cli import query_substitutions
from load_test_report.cli import resolve_queries_file
from load_test_report.cli import run
from load_test_report.errors import MissingMetric
from load_test_report.errors import ReportError
from load_test_report.prometheus import PrometheusClient
from load_test_report.prometheus import auth_headers
from load_test_report.queries import QueriesDocument
from load_test_report.queries import Query
from load_test_report.report import build_report
from load_test_report.report import extract_metric_value
from load_test_report.report import render_report
from load_test_report.report import warn

PROJECT_DIR = Path(load_test_report.__file__).resolve().parent
PACKAGED_QUERY_FILES = (
    "report-queries.yaml",
    "report-queries-stable-87.yaml",
)


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


def test_should_build_parser():
    parser = build_parser()

    assert parser.prog == "load-test-report"


def test_should_return_argparse_exit_code_for_help():
    assert run(["--help"]) == 0


def test_should_return_error_for_missing_namespace():
    stderr = io.StringIO()

    with mock.patch.object(sys, "stderr", stderr):
        exit_code = run([])

    assert exit_code == 1
    assert "Missing <namespace>" in stderr.getvalue()


def test_should_extract_numeric_sample():
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

    assert extract_metric_value(response, "", "throughput") == 42.5


def test_should_extract_sorted_unique_label_values():
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

    assert extract_metric_value(response, "image", "image") == "registry/camunda:1, registry/camunda:2"


def test_should_warn_when_numeric_sample_is_missing():
    response = {"status": "success", "data": {"result": []}}
    warnings = []

    with pytest.raises(MissingMetric, match="no numeric sample"):
        extract_metric_value(response, "", "throughput", warnings.append)

    assert warnings == ["throughput: no numeric sample"]


def test_should_warn_when_label_sample_is_missing():
    response = {"status": "success", "data": {"result": []}}
    warnings = []

    with pytest.raises(MissingMetric, match="no label sample"):
        extract_metric_value(response, "image", "image", warnings.append)

    assert warnings == ["image: no label sample"]


def test_should_warn_when_prometheus_status_is_not_success():
    response = {"status": "error", "data": {"result": []}}
    warnings = []

    with pytest.raises(MissingMetric, match="Prometheus returned non-success status"):
        extract_metric_value(response, "", "throughput", warnings.append)

    assert warnings == ["throughput: Prometheus returned non-success status"]


def test_should_build_report_without_network_side_effects():
    options = Options(
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
        queries_file=Path("queries.yaml"),
        output_file=None,
    )
    query_document = QueriesDocument(
        queries=[
            Query(
                key="namespace",
                description="Namespace.",
                header="Namespace",
                query="namespace_query",
                valueLabel="namespace",
            ),
            Query(
                key="throughput",
                description="Throughput.",
                header="Throughput",
                query='rate(total{namespace="c8-ck-test"}[30s])',
            ),
            Query(
                key="image",
                description="Image.",
                header="Image",
                query="image_query[900s:15s]",
                valueLabel="image",
            ),
            Query(
                key="missing",
                description="Missing.",
                header="Missing",
                query="missing_query",
            ),
        ]
    )
    client = FakePrometheusClient(
        [
            {"status": "success", "data": {"result": [{"metric": {"namespace": "c8-ck-test"}}]}},
            {"status": "success", "data": {"result": [{"value": [123, "10"]}]}},
            {"status": "success", "data": {"result": [{"metric": {"image": "camunda:SNAPSHOT"}}]}},
            {"status": "success", "data": {"result": []}},
        ]
    )
    warnings = []

    report = build_report(
        options,
        query_document,
        client,
        generated_at="2026-09-07T18:00:00Z",
        warning_sink=warnings.append,
    )

    assert client.queries == [
        "namespace_query",
        'rate(total{namespace="c8-ck-test"}[30s])',
        "image_query[900s:15s]",
        "missing_query",
    ]
    assert report["columns"] == ["namespace", "throughput", "image", "missing"]
    assert report["headers"] == ["Namespace", "Throughput", "Image", "Missing"]
    assert report["metrics"]["namespace"] == "c8-ck-test"
    assert report["metrics"]["throughput"] == 10
    assert report["metrics"]["image"] == "camunda:SNAPSHOT"
    assert report["metrics"]["missing"] is None
    assert "missing" not in report
    assert warnings == ["missing: no numeric sample"]


def test_should_warn_when_query_fails():
    options = Options(
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
        queries_file=Path("queries.yaml"),
        output_file=None,
    )
    query_document = QueriesDocument(
        queries=[
            Query(
                key="throughput",
                description="Throughput.",
                header="Throughput",
                query="throughput_query",
            )
        ]
    )
    client = FakePrometheusClient([ReportError("query failed")])
    warnings = []

    report = build_report(options, query_document, client, warning_sink=warnings.append)

    assert report["metrics"]["throughput"] is None
    assert "missing" not in report
    assert warnings == ["throughput: query failed"]


def test_should_build_basic_auth_header():
    headers = auth_headers("", "user", "pass")

    assert headers["Authorization"] == "Basic dXNlcjpwYXNz"


def test_should_build_bearer_auth_header():
    headers = auth_headers("abc123", "", "")

    assert headers["Authorization"].startswith("Bearer ")
    assert len(headers["Authorization"]) > len("Bearer ")


def test_should_reject_combined_auth_modes():
    with pytest.raises(ReportError, match="--token cannot be combined"):
        auth_headers("abc123", "user", "pass")


def test_should_reject_incomplete_basic_auth():
    with pytest.raises(ReportError, match="--user and --password"):
        auth_headers("", "user", "")


def test_should_query_prometheus_with_urlencoded_query_and_headers():
    seen_requests = []

    def fake_urlopen(request, timeout):
        seen_requests.append((request, timeout))
        return FakeHttpResponse('{"status":"success","data":{"result":[]}}')

    with mock.patch.object(prometheus, "urlopen", side_effect=fake_urlopen):
        client = PrometheusClient(
            "https://prometheus.example/",
            "",
            "user",
            "pass",
            "2026-09-07T18:00:00Z",
        )

        response = client.query('rate(total{namespace="c8-ck-test"}[5m])')

    assert response["status"] == "success"
    request, timeout = seen_requests[0]
    assert timeout == 30
    assert "query=rate%28total%7Bnamespace%3D%22c8-ck-test%22%7D%5B5m%5D%29" in request.full_url
    assert "time=2026-09-07T18%3A00%3A00Z" in request.full_url
    assert request.get_header("Authorization") == "Basic dXNlcjpwYXNz"


def test_should_load_yaml_query_file_with_pyyaml(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text(
        """queries:
- key: namespace
  description: Namespace label.
  header: Namespace
  query: namespace_metric{namespace="$NAMESPACE"}
""",
        encoding="utf-8",
    )

    document = QueriesDocument.from_file(queries_file, {"$NAMESPACE": "c8-ck-test"})

    assert document.queries[0].key == "namespace"
    assert document.queries[0].query == 'namespace_metric{namespace="c8-ck-test"}'


def test_should_reject_empty_file(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text("{}", encoding="utf-8")

    with pytest.raises(ValidationError, match="Field required"):
        QueriesDocument.from_file(queries_file, {})


def test_should_reject_invalid_queries(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text(
        """queries:
- key: missing_source
  description: Missing source.
  header: Missing source
""",
        encoding="utf-8",
    )

    with pytest.raises(ValidationError, match="Field required"):
        QueriesDocument.from_file(queries_file, {})


def test_should_reject_duplicate_keys_in_query_file(tmp_path):
    queries_file = tmp_path / "report-queries.yaml"
    queries_file.write_text(
        """queries:
- key: duplicate
  description: Duplicate.
  header: Duplicate
  query: first

- key: duplicate
  description: Duplicate.
  header: Duplicate
  query: second
""",
        encoding="utf-8",
    )

    with pytest.raises(ValidationError, match="duplicate query key: duplicate"):
        QueriesDocument.from_file(queries_file, {})


def test_should_load_packaged_query_files():
    substitutions = {
        "$NAMESPACE": "c8-ck-test",
        "$DURATION_S": "600s",
        "$RATE_INTERVAL": "5m",
        "$SAMPLE_STEP": "1m",
    }

    for query_file_name in PACKAGED_QUERY_FILES:
        document = QueriesDocument.from_file(PROJECT_DIR / query_file_name, substitutions)
        assert len(document.queries) > 0


def test_should_substitute_queries(tmp_path):
    substitutions = {
        "$NAMESPACE": "c8-ck-test",
        "$DURATION_S": "600s",
        "$SAMPLE_STEP": "1m",
    }

    queries_file = tmp_path / "report-queries.yaml"
    queries_file.write_text(
        """queries:
- key: query
  description: query to substitute
  header: query
  query: |
    count(max_over_time((kube_pod_status_phase{namespace="$NAMESPACE", pod=~"(camunda|zeebe)-[0-9]+", phase="Running"} == 1)[$DURATION_S:$SAMPLE_STEP]))
- key: query2
  description: query2 to substitute
  header: query2
  query: |
    max(max_over_time(kube_pod_container_resource_limits{namespace="$NAMESPACE", pod=~"(camunda|zeebe).*", container=~".*(orchestration|zeebe|camunda).*", resource="cpu", unit="core"}[$DURATION_S])) or max(max_over_time(kube_pod_container_resource_limits_cpu_cores{namespace="$NAMESPACE", pod=~"(camunda|zeebe).*", container=~".*(orchestration|zeebe|camunda).*"}[$DURATION_S]))
""",
        encoding="utf-8",
    )

    for query_file_name in PACKAGED_QUERY_FILES:
        document = QueriesDocument.from_file(queries_file, substitutions)

        assert len(document.queries) > 0
        for query in document.queries:
            assert "c8-ck-test" in query.query
            assert "600s" in query.query


def test_should_use_namespace_created_metric():
    substitutions = {
        "$NAMESPACE": "c8-ck-test",
        "$DURATION_S": "600s",
        "$RATE_INTERVAL": "5m",
        "$SAMPLE_STEP": "1m",
    }

    for query_file_name in PACKAGED_QUERY_FILES:
        document = QueriesDocument.from_file(PROJECT_DIR / query_file_name, substitutions)
        namespace_query = document.queries[0]

        assert namespace_query.key == "namespace"
        assert namespace_query.value_label == "namespace"
        assert namespace_query.query == 'max_over_time(kube_namespace_created{namespace="c8-ck-test"}[600s])'


def test_should_use_stable_87_specific_metric_sources():
    document = QueriesDocument.from_file(
        PROJECT_DIR / "report-queries-stable-87.yaml",
        {
            "$NAMESPACE": "c8-ck-test",
            "$DURATION_S": "600s",
            "$RATE_INTERVAL": "5m",
            "$SAMPLE_STEP": "1m",
        },
    )

    queries = {query.key: query for query in document.queries}

    assert "operate_archived_process_instances_total" in queries["archived_process_instances_per_second"].query
    assert queries["data_availability_p50_seconds"].value_label == "report_value"
    assert "N/A" in queries["data_availability_p50_seconds"].query
    assert queries["data_availability_p99_seconds"].value_label == "report_value"
    assert "N/A" in queries["data_availability_p99_seconds"].query


def test_should_keep_packaged_descriptions_before_headers():
    for query_file_name in PACKAGED_QUERY_FILES:
        query_text = (PROJECT_DIR / query_file_name).read_text(encoding="utf-8").split("queries:\n", 1)[1]
        entries = [entry for entry in query_text.split("\n\n") if entry.startswith("  - key:")]

        assert len(entries) > 0
        for entry in entries:
            description_index = entry.find("\n    description:")
            header_index = entry.find("\n    header:")
            assert description_index != -1
            assert header_index != -1
            assert description_index < header_index


def test_should_prefix_default_stderr_warnings():
    stderr = io.StringIO()

    with mock.patch.object(sys, "stderr", stderr):
        warn("throughput: no numeric sample")

    assert stderr.getvalue() == "Warning: throughput: no numeric sample\n"


def test_should_render_tsv_without_header():
    report = {
        "columns": ["namespace", "throughput", "missing"],
        "headers": ["Namespace", "Throughput", "Missing"],
        "metrics": {"namespace": "c8-ck-test", "throughput": 10, "missing": None},
    }

    rendered = render_report(report, "tsv", include_header=False, missing_value="NaN")

    assert rendered == "c8-ck-test\t10\tNaN"


def test_should_quote_csv_cells():
    report = {
        "columns": ["namespace", "image"],
        "headers": ["Namespace", "Image"],
        "metrics": {"namespace": "c8-ck-test", "image": "camunda:1, camunda:2"},
    }

    rendered = render_report(report, "csv", include_header=True, missing_value="NaN")

    assert rendered == 'Namespace,Image\nc8-ck-test,"camunda:1, camunda:2"'


def test_should_parse_auth_flags(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text("queries: []", encoding="utf-8")

    options = parse_args(
        [
            "c8-ck-test",
            "--token",
            "abc123",
            "--queries",
            str(queries_file),
        ],
        tmp_path,
    )

    assert options.bearer_token == "abc123"
    assert options.basic_auth_user == ""
    assert options.basic_auth_password == ""
    assert options.queries_file == queries_file


def test_should_derive_duration_from_start_and_end(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text("queries: []", encoding="utf-8")

    options = parse_args(
        [
            "c8-ck-test",
            "--start",
            "2026-08-14T10:00:00Z",
            "--end",
            "2026-08-14T10:30:00Z",
            "--queries",
            str(queries_file),
        ],
        tmp_path,
    )

    assert options.duration_seconds == 1800
    assert options.time_anchor == "2026-08-14T10:30:00Z"
    assert options.start_label == "2026-08-14T10:00:00Z"
    assert options.end_label == "2026-08-14T10:30:00Z"


def test_should_use_packaged_default_queries(tmp_path):
    default_queries_file = tmp_path / "report-queries.yaml"
    default_queries_file.write_text(
        """queries:
- key: namespace
  description: Namespace.
  header: Namespace
  query: namespace_metric{namespace="test"}
""",
        encoding="utf-8",
    )

    options = parse_args(["c8-ck-test"], tmp_path)

    assert options.queries_file == default_queries_file


def test_should_use_packaged_queries_file_by_path(tmp_path):
    stable_queries_file = tmp_path / "report-queries-stable-87.yaml"
    stable_queries_file.write_text(
        """queries:
- key: namespace
  description: Namespace.
  header: Namespace
  query: namespace_metric{namespace="test"}
""",
        encoding="utf-8",
    )

    options = parse_args(["c8-ck-test", "--queries", "report-queries-stable-87.yaml"], tmp_path)

    assert options.queries_file == stable_queries_file


def test_should_reject_missing_queries_file(tmp_path):
    with pytest.raises(ReportError, match="must be an existing YAML file"):
        parse_args(
            ["c8-ck-test", "--queries", "daily"],
            tmp_path,
        )


def test_should_resolve_external_queries_file(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text(
        """queries:
- key: namespace
  description: Namespace.
  header: Namespace
  query: namespace_metric{namespace="$NAMESPACE"}
""",
        encoding="utf-8",
    )

    assert resolve_queries_file(tmp_path, str(queries_file)) == queries_file


def test_should_build_query_substitutions():
    options = Options(
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
        queries_file=Path("queries.yaml"),
        output_file=None,
    )

    assert query_substitutions(options) == {
        "$NAMESPACE": "c8-ck-test",
        "$DURATION_S": "900s",
        "$RATE_INTERVAL": "30s",
        "$SAMPLE_STEP": "15s",
    }
