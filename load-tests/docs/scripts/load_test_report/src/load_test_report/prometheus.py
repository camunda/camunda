"""Prometheus HTTP client and authentication helpers."""

import base64
import json
from collections.abc import Mapping
from typing import Any
from urllib.error import HTTPError
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request
from urllib.request import urlopen

from .errors import ReportError


class PrometheusClient:
    def __init__(
        self,
        endpoint: str,
        bearer_token: str,
        basic_auth_user: str,
        basic_auth_password: str,
        time_anchor: str,
    ):
        self.endpoint = endpoint.rstrip("/")
        self.headers = auth_headers(bearer_token, basic_auth_user, basic_auth_password)
        self.time_anchor = time_anchor

    def runtime_info(self) -> Mapping[str, Any]:
        return self._get_json(f"{self.endpoint}/api/v1/status/runtimeinfo", timeout=15)

    def query(self, query: str) -> Mapping[str, Any]:
        params = {"query": query}
        if self.time_anchor:
            params["time"] = self.time_anchor
        return self._get_json(f"{self.endpoint}/api/v1/query?{urlencode(params)}")

    def _get_json(self, url: str, timeout: int = 30) -> Mapping[str, Any]:
        request = Request(url, headers=self.headers)
        try:
            with urlopen(request, timeout=timeout) as response:
                parsed = json.loads(response.read().decode("utf-8"))
        except (HTTPError, URLError, TimeoutError, OSError) as error:
            raise ReportError("query failed") from error
        except json.JSONDecodeError as error:
            raise ReportError(f"Prometheus returned invalid JSON: {error}") from error
        if not isinstance(parsed, Mapping):
            raise ReportError("Prometheus returned a non-object JSON response.")
        return parsed


def auth_headers(bearer_token: str, basic_auth_user: str, basic_auth_password: str) -> dict[str, str]:
    if bearer_token and (basic_auth_user or basic_auth_password):
        raise ReportError("--token cannot be combined with --user/--password.")
    if bool(basic_auth_user) != bool(basic_auth_password):
        raise ReportError("--user and --password must be provided together.")
    headers: dict[str, str] = {}
    if bearer_token:
        headers["Authorization"] = f"Bearer {bearer_token}"
    if basic_auth_user:
        credentials = f"{basic_auth_user}:{basic_auth_password}".encode()
        headers["Authorization"] = f"Basic {base64.b64encode(credentials).decode('ascii')}"
    return headers


def check_endpoint(client: PrometheusClient, endpoint: str) -> None:
    try:
        response = client.runtime_info()
    except ReportError as error:
        raise ReportError(prometheus_endpoint_help(endpoint)) from error
    if response.get("status") != "success":
        raise ReportError(
            f"""Endpoint '{endpoint}' is reachable, but it did not return a Prometheus API success response.

If you are running locally, make sure the port-forward points at Prometheus:

  kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090"""
        )


def prometheus_endpoint_help(endpoint: str) -> str:
    return f'''Could not reach Prometheus endpoint '{endpoint}'.

If you are running locally, start the port-forward in another terminal:

  kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090

Then rerun this script with:

  --endpoint http://localhost:9090

If you are using the CI monitor ingress, verify the URL and pass credentials with
--user/--password, for example:

  --endpoint https://ci-monitor.benchmark.camunda.cloud --user "$PROM_USER" --password "$PROM_PASS"'''
