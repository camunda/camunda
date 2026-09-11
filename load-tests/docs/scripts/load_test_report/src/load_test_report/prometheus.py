"""Prometheus HTTP client and authentication helpers."""

import base64
import json
from typing import Any
from typing import Literal
from urllib.parse import urlencode
from urllib.request import Request
from urllib.request import urlopen

from pydantic import BaseModel
from pydantic import ConfigDict
from pydantic import Field

from .errors import MissingMetric
from .errors import PrometheusError
from .errors import ReportError

MetricValue = str | int | float


class PrometheusInstantVector(BaseModel):
    metric: dict[str, Any]
    promValue: tuple[float, float] = Field(alias="value", min_length=2, max_length=2)

    @property
    def timestamp(self) -> float:
        return self.promValue[0]

    @property
    def value(self) -> float:
        return self.promValue[1]


class PrometheusResponseData(BaseModel):
    model_config = ConfigDict(populate_by_name=True)
    result: list[PrometheusInstantVector]
    result_type: Literal["vector"] = Field(alias="resultType")


class PrometheusResponse(BaseModel):
    status: str
    data: PrometheusResponseData
    error_type: str | None = Field(default=None, alias="errorType")
    error: str | None = None

    def extract_metric_value(self, value_label: str, key: str) -> MetricValue:
        data = self.data
        result = data.result

        if value_label:
            values = sorted({str(series.metric[value_label]) for series in result if value_label in series.metric})
            if values == []:
                raise MissingMetric(f"no label sample for: {key}")
            return ", ".join(values)

        if data.result_type != "vector":
            raise MissingMetric(f"unexpected result type: {data.result_type}")

        if len(result) != 1:
            raise MissingMetric(f"Expected one result but got {len(result)} results for: {key}")

        raw_value = result[0].value
        return float(raw_value)


class PrometheusClient:
    def __init__(
        self,
        endpoint: str,
        basic_auth_user: str,
        basic_auth_password: str,
        time_anchor: str,
    ):
        self.endpoint = endpoint.rstrip("/")
        self.headers = auth_headers(basic_auth_user, basic_auth_password)
        self.time_anchor = time_anchor

    def runtime_info(self) -> Any:
        return self._get_json(f"{self.endpoint}/api/v1/status/runtimeinfo", timeout=15)

    def query(self, query: str) -> PrometheusResponse:
        params = {"query": query}
        if self.time_anchor:
            params["time"] = self.time_anchor
        response = self._get_json(f"{self.endpoint}/api/v1/query?{urlencode(params)}")
        prometheusResponse = PrometheusResponse.model_validate(response)

        if prometheusResponse.status != "success":
            error_type = prometheusResponse.error_type
            error_message = prometheusResponse.error
            reason = "Prometheus returned non-success status"
            if error_type or error_message:
                reason += f": {error_type or 'error'}: {error_message or 'unknown error'}"
            raise PrometheusError(reason)

        return prometheusResponse

    def _get_json(self, url: str, timeout: int = 30) -> Any:
        request = Request(url, headers=self.headers)
        with urlopen(request, timeout=timeout) as response:
            parsed = json.loads(response.read().decode("utf-8"))
        return parsed


def auth_headers(basic_auth_user: str, basic_auth_password: str) -> dict[str, str]:
    if bool(basic_auth_user) != bool(basic_auth_password):
        raise ReportError("--user and --password must be provided together.")
    headers: dict[str, str] = {}
    if basic_auth_user:
        credentials = f"{basic_auth_user}:{basic_auth_password}".encode()
        headers["Authorization"] = f"Basic {base64.b64encode(credentials).decode('ascii')}"
    return headers


def check_endpoint(client: PrometheusClient, endpoint: str) -> None:
    try:
        response = client.runtime_info()
    except PrometheusError as error:
        raise PrometheusError(f"{prometheus_endpoint_help(endpoint)}\n\nReason: {error}") from error
    if response.get("status") != "success":
        raise PrometheusError(
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
