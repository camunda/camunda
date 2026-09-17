import json
from collections.abc import Mapping
from pathlib import Path
from typing import Any
from unittest import mock

import pytest

import load_test_report
from load_test_report.cli import Options
from load_test_report.cli import run
from load_test_report.errors import ReportError
from load_test_report.prometheus import PrometheusResponse
from load_test_report.queries import QueriesDocument
from load_test_report.queries import Query
from load_test_report.report import build_report
from load_test_report.report import render_report

PROJECT_DIR = Path(load_test_report.__file__).resolve().parent
PACKAGED_QUERY_FILES = (
    "report-queries.yaml",
    "report-queries-stable-87.yaml",
)


class FakePrometheusClient:
    def __init__(self, responses: list[Mapping[str, Any] | Exception]) -> None:
        self.responses = responses
        self.queries: list[str] = []

    def query(self, query: str) -> PrometheusResponse:
        self.queries.append(query)
        response = self.responses.pop(0)
        if isinstance(response, Exception):
            raise response
        return PrometheusResponse.model_validate(response)


def write_single_query_file(tmp_path: Path) -> Path:
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text(
        """queries:
- key: throughput
  description: Throughput.
  header: Throughput
  query: up
""",
        encoding="utf-8",
    )
    return queries_file


def successful_client() -> FakePrometheusClient:
    return FakePrometheusClient(
        [
            {
                "status": "success",
                "data": {
                    "resultType": "vector",
                    "result": [{"metric": {}, "value": [1435781451.781, "42.5"]}],
                },
            }
        ]
    )


def test_should_run_report_to_stdout(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    queries_file = write_single_query_file(tmp_path)
    client = successful_client()

    with (
        mock.patch("load_test_report.cli.PrometheusClient", return_value=client),
        mock.patch("load_test_report.cli.check_endpoint") as endpoint_check,
    ):
        exit_code = run(["c8-ck-test", "--queries", str(queries_file)])

    assert exit_code == 0
    assert json.loads(capsys.readouterr().out)["metrics"] == {"throughput": 42.5}
    endpoint_check.assert_called_once()


def test_should_run_report_to_output_file(tmp_path: Path) -> None:
    queries_file = write_single_query_file(tmp_path)
    output_file = tmp_path / "report.json"

    with (
        mock.patch("load_test_report.cli.PrometheusClient", return_value=successful_client()),
        mock.patch("load_test_report.cli.check_endpoint"),
    ):
        exit_code = run(
            [
                "c8-ck-test",
                "--queries",
                str(queries_file),
                "--output",
                str(output_file),
            ]
        )

    assert exit_code == 0
    assert json.loads(output_file.read_text(encoding="utf-8"))["metrics"] == {"throughput": 42.5}


def test_should_report_output_file_error(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    queries_file = write_single_query_file(tmp_path)
    output_file = tmp_path / "missing" / "report.json"

    with (
        mock.patch("load_test_report.cli.PrometheusClient", return_value=successful_client()),
        mock.patch("load_test_report.cli.check_endpoint"),
    ):
        exit_code = run(
            [
                "c8-ck-test",
                "--queries",
                str(queries_file),
                "--output",
                str(output_file),
            ]
        )

    assert exit_code == 1
    assert f"Could not write output file '{output_file}'" in capsys.readouterr().err


def test_should_report_invalid_query_document(tmp_path: Path, capsys: pytest.CaptureFixture[str]) -> None:
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text("{}", encoding="utf-8")

    with mock.patch("load_test_report.cli.check_endpoint"):
        exit_code = run(["c8-ck-test", "--queries", str(queries_file)])

    assert exit_code == 1
    assert "Error: query document is invalid" in capsys.readouterr().err


def test_should_report_unreachable_endpoint(capsys: pytest.CaptureFixture[str]) -> None:
    with mock.patch(
        "load_test_report.cli.check_endpoint",
        side_effect=ReportError("Could not reach Prometheus endpoint"),
    ):
        exit_code = run(["c8-ck-test"])

    assert exit_code == 1
    assert "Error: Could not reach Prometheus endpoint" in capsys.readouterr().err


def test_should_render_tsv_without_header() -> None:
    report = {
        "columns": ["namespace", "throughput", "missing"],
        "headers": ["Namespace", "Throughput", "Missing"],
        "metrics": {"namespace": "c8-ck-test", "throughput": 10, "missing": None},
    }

    rendered = render_report(report, "tsv", include_header=False, missing_value="NaN")

    assert rendered == "c8-ck-test\t10\tNaN"


def test_should_quote_csv_cells() -> None:
    report = {
        "columns": ["namespace", "image"],
        "headers": ["Namespace", "Image"],
        "metrics": {"namespace": "c8-ck-test", "image": "camunda:1, camunda:2"},
    }

    rendered = render_report(report, "csv", include_header=True, missing_value="NaN")

    assert rendered == 'Namespace,Image\nc8-ck-test,"camunda:1, camunda:2"'


def test_should_warn_when_query_fails() -> None:
    options = Options(
        namespace="c8-ck-test",
        duration_seconds=900,
        rate_interval="30s",
        sample_step="15s",
        endpoint="http://prometheus.example",
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
    client = FakePrometheusClient([ReportError("Prometheus returned invalid JSON")])

    report = build_report(options, query_document, client)  # type: ignore

    assert report["metrics"]["throughput"] is None
    assert "missing" not in report


def test_should_build_report_without_network_side_effects() -> None:
    options = Options(
        namespace="c8-ck-test",
        duration_seconds=900,
        rate_interval="30s",
        sample_step="15s",
        endpoint="http://prometheus.example",
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
            {
                "status": "success",
                "data": {
                    "resultType": "vector",
                    "result": [{"metric": {"namespace": "c8-ck-test"}, "value": [123, "1"]}],
                },
            },
            {
                "status": "success",
                "data": {"resultType": "vector", "result": [{"metric": {}, "value": [123, "10"]}]},
            },
            {
                "status": "success",
                "data": {
                    "resultType": "vector",
                    "result": [{"metric": {"image": "camunda:SNAPSHOT"}, "value": [123, "1"]}],
                },
            },
            {"status": "success", "data": {"resultType": "vector", "result": []}},
        ]
    )

    report = build_report(
        options,
        query_document,
        client,  # type: ignore
        generated_at="2026-09-07T18:00:00Z",
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
