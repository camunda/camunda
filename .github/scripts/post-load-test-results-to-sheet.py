#!/usr/bin/env python3
"""Append daily load-test results to the tracking Google Sheet.

Builds one row per protocol (gRPC, REST) from the end-of-soak metric snapshots
and appends them to the "Load test daily statistics" sheet, so results build a
historical trend record instead of only living in Slack or a 90-day artifact.
Invoked by the `notify-results` and `notify` jobs of
`.github/workflows/camunda-daily-load-tests.yml`.

Metric columns are derived from `queries.yaml` (the single source of truth
shared with `loadTestMetrics.sh` and the Slack results script), in file order.
Adding a metric there changes the columns this script writes — the sheet's
header row must be updated to match by hand when that happens.

Required environment variables:
  GRPC_RESULTS_JSON  JSON {metric_name: value} for the gRPC run. Empty/missing
                      values are fine when STATUS_GRPC != success.
  REST_RESULTS_JSON  JSON {metric_name: value} for the REST run.
  STATUS_GRPC        'success' or 'failed'.
  STATUS_REST        'success' or 'failed'.
  BENCHMARK          Benchmark name, e.g. medic-daily-YYYY-MM-DD-<sha>-test.
  REPO               GitHub repo slug, e.g. camunda/camunda.
  RUN_ID             GitHub Actions run id (used to link the workflow run).
  LOAD_TEST_SHEET_ID Target spreadsheet ID.
  GOOGLE_SHEETS_SERVICE_ACCOUNT_JSON
                      Service account key JSON (Vault secret) with Editor
                      access to the sheet.

Optional:
  QUERIES_YAML  Path to queries.yaml. Default: load-tests/docs/scripts/queries.yaml
  SHEET_TAB     Sheet tab to append to. Default: Sheet1
"""

import json
import os

import yaml

Row = list[object]
Query = dict[str, object]


def parse_benchmark(benchmark: str) -> tuple[str, str]:
    """Extract (date, short_sha) from medic-daily-YYYY-MM-DD-<sha>-test."""
    parts = benchmark.split("-")
    if len(parts) < 6:
        return "", ""
    return "-".join(parts[2:5]), parts[5]


def metric_value(status: str, results: dict, name: str) -> object:
    if status != "success":
        return ""
    value = results.get(name)
    if value is None:
        return ""
    try:
        return float(value)
    except (TypeError, ValueError):
        return ""


def build_row(
    protocol: str,
    status: str,
    results: dict,
    queries: list[Query],
    date: str,
    sha: str,
    benchmark: str,
    run_url: str,
) -> Row:
    row: Row = [date, protocol, status, benchmark, sha, run_url]
    row += [metric_value(status, results, q["name"]) for q in queries]
    return row


def build_rows(
    benchmark: str,
    run_url: str,
    grpc_status: str,
    grpc_results: dict,
    rest_status: str,
    rest_results: dict,
    queries: list[Query],
) -> list[Row]:
    date, sha = parse_benchmark(benchmark)
    return [
        build_row("grpc", grpc_status, grpc_results, queries, date, sha, benchmark, run_url),
        build_row("rest", rest_status, rest_results, queries, date, sha, benchmark, run_url),
    ]


def append_rows(sheet_id: str, service_account_info: dict, tab: str, rows: list[Row]) -> None:
    # Imported lazily so unit-testing build_rows()/parse_benchmark() above doesn't
    # require google-api-python-client/google-auth to be installed.
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    creds = service_account.Credentials.from_service_account_info(
        service_account_info, scopes=["https://www.googleapis.com/auth/spreadsheets"]
    )
    service = build("sheets", "v4", credentials=creds)
    service.spreadsheets().values().append(
        spreadsheetId=sheet_id,
        range=f"{tab}!A:A",
        valueInputOption="USER_ENTERED",
        insertDataOption="INSERT_ROWS",
        body={"values": rows},
    ).execute()


def main() -> None:
    grpc_results = json.loads(os.environ.get("GRPC_RESULTS_JSON") or "{}")
    rest_results = json.loads(os.environ.get("REST_RESULTS_JSON") or "{}")
    grpc_status = os.environ["STATUS_GRPC"]
    rest_status = os.environ["STATUS_REST"]
    benchmark = os.environ["BENCHMARK"]
    run_url = f'https://github.com/{os.environ["REPO"]}/actions/runs/{os.environ["RUN_ID"]}'
    sheet_id = os.environ["LOAD_TEST_SHEET_ID"]
    tab = os.environ.get("SHEET_TAB", "Sheet1")
    queries_yaml = os.environ.get("QUERIES_YAML", "load-tests/docs/scripts/queries.yaml")

    with open(queries_yaml) as f:
        queries = yaml.safe_load(f)["queries"]

    rows = build_rows(benchmark, run_url, grpc_status, grpc_results, rest_status, rest_results, queries)

    service_account_info = json.loads(os.environ["GOOGLE_SHEETS_SERVICE_ACCOUNT_JSON"])
    append_rows(sheet_id, service_account_info, tab, rows)
    print(f"Appended {len(rows)} rows to sheet {sheet_id}")


if __name__ == "__main__":
    main()
