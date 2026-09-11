from pathlib import Path

import pytest
from pydantic import ValidationError

import load_test_report
from load_test_report.cli import build_parser
from load_test_report.cli import run
from load_test_report.queries import QueriesDocument

PROJECT_DIR = Path(load_test_report.__file__).resolve().parent
PACKAGED_QUERY_FILES = (
    "report-queries.yaml",
    "report-queries-stable-87.yaml",
)


def test_should_build_parser():
    parser = build_parser()

    assert parser.prog == "load-test-report"


def test_should_run_without_arguments():
    assert run([]) == 0


def test_should_return_argparse_exit_code_for_help():
    assert run(["--help"]) == 0


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
    queries_file = tmp_path / "queries.json"
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
