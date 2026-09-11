from pathlib import Path

import pytest

import load_test_report

PROJECT_DIR = Path(load_test_report.__file__).resolve().parent


def test_should_build_parser():
    parser = load_test_report.build_parser()

    assert parser.prog == "load-test-report"


def test_should_run_without_arguments():
    assert load_test_report.run([]) == 0


def test_should_return_argparse_exit_code_for_help():
    assert load_test_report.run(["--help"]) == 0


def test_should_load_yaml_query_file_with_pyyaml(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text("queries:\n- key: namespace\n  value: $NAMESPACE\n", encoding="utf-8")

    document = load_test_report.load_query_document(queries_file, {"$NAMESPACE": "c8-ck-test"})

    assert document["queries"][0]["key"] == "namespace"
    assert document["queries"][0]["value"] == "c8-ck-test"


def test_should_substitute_query_file_before_json_decoding(tmp_path):
    queries_file = tmp_path / "queries.json"
    queries_file.write_text(
        '{"queries":[{"key":"throughput","query":"rate(total{namespace=\\"$NAMESPACE\\"}[$RATE_INTERVAL])"}]}',
        encoding="utf-8",
    )

    document = load_test_report.load_query_document(
        queries_file,
        {"$NAMESPACE": "c8-ck-test", "$RATE_INTERVAL": "30s"},
    )

    assert document["queries"][0]["query"] == 'rate(total{namespace="c8-ck-test"}[30s])'


def test_should_report_invalid_json_query_file(tmp_path):
    queries_file = tmp_path / "queries.json"
    queries_file.write_text('{"queries":[', encoding="utf-8")

    with pytest.raises(load_test_report.ReportError, match="Could not parse queries file"):
        load_test_report.load_query_document(queries_file, {})


def test_should_reject_legacy_load_test_metrics_schema(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text(
        """queries:
- name: throughput-per-second
  description: Client Process Instance Started Rate (avg)
  query: numeric_metric{namespace="$NAMESPACE"}
""",
        encoding="utf-8",
    )

    with pytest.raises(load_test_report.ReportError, match="non-empty string key"):
        load_test_report.load_query_document(queries_file, {})


def test_should_reject_duplicate_query_keys(tmp_path):
    queries_file = tmp_path / "queries.yaml"
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

    with pytest.raises(load_test_report.ReportError, match="duplicate query key: duplicate_metric"):
        load_test_report.load_query_document(queries_file, {})


def test_should_reject_entries_without_exactly_one_value_source(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text(
        """queries:
- key: missing_source
  header: Missing source
""",
        encoding="utf-8",
    )

    with pytest.raises(load_test_report.ReportError, match="must set exactly one of query or value"):
        load_test_report.load_query_document(queries_file, {})


def test_should_reject_value_label_without_query(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text(
        """queries:
- key: invalid_label
  header: Invalid label
  value: static
  valueLabel: image
""",
        encoding="utf-8",
    )

    with pytest.raises(load_test_report.ReportError, match="sets valueLabel without query"):
        load_test_report.load_query_document(queries_file, {})


def test_should_load_builtin_query_file(tmp_path):
    queries_file = tmp_path / "report-queries.yaml"
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

    assert [query["key"] for query in document["queries"]] == ["namespace", "throughput"]
    assert document["queries"][0]["value"] == "c8-ck-test"
    assert document["queries"][1]["query"] == 'rate(total{namespace="c8-ck-test"}[30s])'


def test_should_reject_duplicate_keys_in_query_file(tmp_path):
    queries_file = tmp_path / "report-queries.yaml"
    queries_file.write_text(
        """queries:
- key: duplicate
  value: first

- key: duplicate
  value: second
""",
        encoding="utf-8",
    )

    with pytest.raises(load_test_report.ReportError, match="duplicate query key: duplicate"):
        load_test_report.load_query_document(queries_file, {})


def test_should_load_all_builtin_query_files():
    substitutions = {
        "$NAMESPACE": "c8-ck-test",
        "$DURATION_S": "600s",
        "$RATE_INTERVAL": "5m",
        "$SAMPLE_STEP": "1m",
    }

    for query_file_name in load_test_report.BUILTIN_QUERY_FILES.values():
        document = load_test_report.load_query_document(PROJECT_DIR / query_file_name, substitutions)

        assert len(document["queries"]) > 0


def test_should_use_stable_87_specific_metric_sources():
    document = load_test_report.load_query_document(
        PROJECT_DIR / "report-queries-stable-87.yaml",
        {
            "$NAMESPACE": "c8-ck-test",
            "$DURATION_S": "600s",
            "$RATE_INTERVAL": "5m",
            "$SAMPLE_STEP": "1m",
        },
    )

    queries = {query["key"]: query for query in document["queries"]}

    assert "operate_archived_process_instances_total" in queries["archived_process_instances_per_second"]["query"]
    assert queries["data_availability_p50_seconds"]["value"] == "N/A"
    assert queries["data_availability_p99_seconds"]["value"] == "N/A"


def test_should_keep_builtin_descriptions_before_headers():
    for query_file_name in load_test_report.BUILTIN_QUERY_FILES.values():
        query_text = (PROJECT_DIR / query_file_name).read_text(encoding="utf-8").split("queries:\n", 1)[1]
        entries = [entry for entry in query_text.split("\n\n") if entry.startswith("  - key:")]

        assert len(entries) > 0
        for entry in entries:
            description_index = entry.find("\n    description:")
            header_index = entry.find("\n    header:")
            assert description_index != -1
            assert header_index != -1
            assert description_index < header_index


def test_should_resolve_stable_87_builtin_queries(tmp_path):
    (tmp_path / "report-queries-stable-87.yaml").write_text(
        """queries:
- key: namespace
  value: $NAMESPACE
""",
        encoding="utf-8",
    )

    assert load_test_report.resolve_queries_file(tmp_path, "stable-87") == tmp_path / "report-queries-stable-87.yaml"


def test_should_resolve_custom_queries_file(tmp_path):
    queries_file = tmp_path / "queries.yaml"
    queries_file.write_text(
        """queries:
- key: namespace
  value: $NAMESPACE
""",
        encoding="utf-8",
    )

    assert load_test_report.resolve_queries_file(tmp_path, str(queries_file)) == queries_file
