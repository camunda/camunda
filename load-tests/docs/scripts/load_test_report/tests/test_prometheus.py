from email.message import Message
from json import JSONDecodeError
from types import TracebackType
from typing import Literal
from typing import Self
from unittest import mock
from urllib.error import HTTPError
from urllib.request import Request

import pytest
from pydantic import ValidationError

from load_test_report import prometheus
from load_test_report.errors import MissingMetric
from load_test_report.errors import PrometheusError
from load_test_report.prometheus import PrometheusClient
from load_test_report.prometheus import PrometheusResponse


class FakeHttpResponse:
    def __init__(self, payload: str) -> None:
        self.payload = payload.encode("utf-8")

    def __enter__(self) -> Self:
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> Literal[False]:
        return False

    def read(self) -> bytes:
        return self.payload


def test_should_reject_multiple_numeric_samples() -> None:
    response = PrometheusResponse.model_validate(
        {
            "status": "success",
            "data": {
                "resultType": "vector",
                "result": [
                    {"metric": {"instance": "first"}, "value": [1435781451.781, "42.5"]},
                    {"metric": {"instance": "second"}, "value": [1435781451.781, "24.5"]},
                ],
            },
        }
    )

    with pytest.raises(MissingMetric, match=r"Expected one result but got 2 results for: instance"):
        response.extract_metric_value("", "instance")


def test_should_query_prometheus_with_urlencoded_query_and_headers() -> None:
    seen_requests: list[tuple[Request, int]] = []

    def fake_urlopen(request: Request, timeout: int) -> FakeHttpResponse:
        seen_requests.append((request, timeout))
        return FakeHttpResponse(
            '{"status":"success","data":{"resultType":"vector","result":[{"metric": {"instance": "first"}, "value": [1435781451.781, "42.5"]}]}}'
        )

    with mock.patch.object(prometheus, "urlopen", side_effect=fake_urlopen):
        client = PrometheusClient(
            "https://prometheus.example/",
            "user",
            "pass",
            "2026-09-07T18:00:00Z",
        )

        response = client.query('rate(total{namespace="c8-ck-test"}[5m])')

    assert isinstance(response, PrometheusResponse)
    assert response.status == "success"
    request, timeout = seen_requests[0]
    assert timeout == 30
    assert "query=rate%28total%7Bnamespace%3D%22c8-ck-test%22%7D%5B5m%5D%29" in request.full_url
    assert "time=2026-09-07T18%3A00%3A00Z" in request.full_url
    assert request.get_header("Authorization") == "Basic dXNlcjpwYXNz"


def test_should_report_http_errors_from_prometheus() -> None:
    with mock.patch.object(
        prometheus,
        "urlopen",
        side_effect=HTTPError(
            "https://prometheus.example/api/v1/query",
            401,
            "Unauthorized",
            Message(),
            None,
        ),
    ):
        client = PrometheusClient("https://prometheus.example", "user", "pass", "")

        with pytest.raises(HTTPError, match="401: Unauthorized"):
            client.query("up")


def test_should_report_invalid_prometheus_json() -> None:
    with mock.patch.object(prometheus, "urlopen", return_value=FakeHttpResponse("not json")):
        client = PrometheusClient("https://prometheus.example", "", "", "")

        with pytest.raises(JSONDecodeError):
            client.query("up")


def test_should_report_invalid_prometheus_endpoint() -> None:
    client = PrometheusClient("not a URL", "", "", "")

    with pytest.raises(ValueError, match="unknown url type"):
        prometheus.check_endpoint(client, "not a URL")


def test_should_reject_matrix_response() -> None:
    with pytest.raises(ValidationError, match="resultType"):
        PrometheusResponse.model_validate(
            {
                "status": "success",
                "data": {
                    "resultType": "matrix",
                    "result": [
                        {
                            "metric": {"instance": "prometheus"},
                            "values": [[1435781451.781, "42.5"]],
                        }
                    ],
                },
            }
        )


def test_should_reject_result_shape_mismatched_with_type() -> None:
    with pytest.raises(ValidationError, match="Input should be 'vector'"):
        PrometheusResponse.model_validate(
            {
                "status": "success",
                "data": {
                    "resultType": "scalar",
                    "result": [{"metric": {}, "value": [1435781451.781, "42.5"]}],
                },
            }
        )


def test_should_extract_sorted_unique_label_values() -> None:
    response = PrometheusResponse.model_validate(
        {
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
    )
    assert response.extract_metric_value("image", "image") == "registry/camunda:1, registry/camunda:2"


def test_should_warn_when_numeric_sample_is_missing() -> None:
    response = PrometheusResponse.model_validate({"status": "success", "data": {"resultType": "vector", "result": []}})

    with pytest.raises(MissingMetric, match="Expected one result but got 0 results for: throughput"):
        response.extract_metric_value("", "throughput")


def test_should_warn_when_label_sample_is_missing() -> None:
    response = PrometheusResponse.model_validate({"status": "success", "data": {"resultType": "vector", "result": []}})

    with pytest.raises(MissingMetric, match="no label sample"):
        response.extract_metric_value("image", "image")


def test_should_handle_prometheus_response_containing_invalid_data() -> None:
    seen_requests: list[tuple[Request, int]] = []

    def fake_urlopen(request: Request, timeout: int) -> FakeHttpResponse:
        seen_requests.append((request, timeout))
        return FakeHttpResponse(
            '{"status":"error","errorType":"bad_data","error":"invalid PromQL","data": {"resultType": "vector", "result": []}}'
        )

    with mock.patch.object(prometheus, "urlopen", side_effect=fake_urlopen):
        client = PrometheusClient(
            "https://prometheus.example/",
            "user",
            "pass",
            "2026-09-07T18:00:00Z",
        )
        with pytest.raises(PrometheusError, match="Prometheus returned non-success status: bad_data: invalid PromQL"):
            client.query('rate(total{namespace="c8-ck-test"}[5m])')


def test_should_extract_numeric_sample() -> None:
    response = PrometheusResponse.model_validate(
        {
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
    )

    assert response.extract_metric_value("", "throughput") == 42.5


def test_should_reject_scalar_sample() -> None:
    with pytest.raises(ValidationError, match="Input should be 'vector'"):
        PrometheusResponse.model_validate(
            {
                "status": "success",
                "data": {
                    "resultType": "scalar",
                    "result": [1435781451.781, "42.5"],
                },
            }
        )
