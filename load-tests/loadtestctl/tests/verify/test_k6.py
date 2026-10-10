import json
from unittest import mock
from urllib.error import URLError

import pytest

from loadtestctl.verify.context import VerifyError
from loadtestctl.verify.k6 import query_value
from loadtestctl.verify.k6 import verify_metrics
from loadtestctl.verify.k6 import wait_for_testruns

from .helpers import completed
from .helpers import make_context

KUBECTL = "loadtestctl.verify.k6.kubectl"


def make_testruns(**stages: str) -> str:
    return json.dumps({"items": [{"metadata": {"name": n}, "status": {"stage": s}} for n, s in stages.items()]})


def test_should_skip_when_there_is_no_testrun() -> None:
    with mock.patch(KUBECTL, return_value=completed(make_testruns())) as kubectl:
        wait_for_testruns(make_context())

    assert kubectl.call_count == 1


def test_should_fail_when_listing_testruns_fails() -> None:
    with (
        mock.patch(KUBECTL, return_value=completed("", 1)),
        pytest.raises(VerifyError, match="Unable to list k6 TestRuns"),
    ):
        wait_for_testruns(make_context())


def test_should_fail_when_a_testrun_is_in_error() -> None:
    with (
        mock.patch(KUBECTL, return_value=completed(make_testruns(a="error"))),
        pytest.raises(VerifyError, match="unhealthy"),
    ):
        wait_for_testruns(make_context())


def test_should_fail_when_a_k6_pod_failed() -> None:
    responses = [completed(make_testruns(a="started")), completed(make_testruns(a="started")), completed("pod/k6-1\n")]

    with mock.patch(KUBECTL, side_effect=responses), pytest.raises(VerifyError, match="stopped unexpectedly.*pod/k6-1"):
        wait_for_testruns(make_context())


def test_should_wait_for_runner_pods_of_started_testruns_only() -> None:
    # given
    stages = make_testruns(a="started", b="finished")
    responses = [completed(stages), completed(stages), completed("")]

    with (
        mock.patch(KUBECTL, side_effect=responses),
        mock.patch("loadtestctl.verify.k6.wait_for_pods") as wait_for_pods,
    ):
        # when
        wait_for_testruns(make_context())

    # then
    wait_for_pods.assert_called_once()
    assert wait_for_pods.call_args.args[1] == "app=k6,runner=true,k6_cr=a"


def test_should_retry_while_testruns_are_pending() -> None:
    responses = [
        completed(make_testruns(a="initialization")),
        completed(make_testruns(a="initialization")),
        completed(""),
        completed(make_testruns(a="finished")),
        completed(""),
    ]

    with mock.patch(KUBECTL, side_effect=responses) as kubectl:
        wait_for_testruns(make_context())

    assert kubectl.call_count == 5


def test_should_return_zero_when_prometheus_is_unreachable() -> None:
    client = mock.Mock()
    client.query.side_effect = URLError("down")

    assert query_value(client, "q") == 0


def test_should_return_the_sample_value() -> None:
    client = mock.Mock()
    client.query.return_value.data.result = [mock.Mock(value=3.0)]

    assert query_value(client, "q") == 3


def test_should_verify_metrics_once_reported() -> None:
    # given
    client = mock.Mock()
    client.query.return_value.data.result = [mock.Mock(value=1.0)]

    with mock.patch(KUBECTL, return_value=completed(make_testruns(a="started"))):
        # when / then
        verify_metrics(make_context(), lambda: client, timeout=10)


def test_should_fail_when_metrics_never_arrive() -> None:
    client = mock.Mock()
    client.query.return_value.data.result = []

    with (
        mock.patch(KUBECTL, return_value=completed(make_testruns(a="started"))),
        pytest.raises(VerifyError, match="did not report metrics"),
    ):
        verify_metrics(make_context(), lambda: client, timeout=4)


def test_should_not_query_prometheus_without_testruns() -> None:
    client = mock.Mock()

    with mock.patch(KUBECTL, return_value=completed(make_testruns())):
        verify_metrics(make_context(), lambda: client, timeout=4)

    client.query.assert_not_called()


def test_should_not_create_a_prometheus_client_without_testruns() -> None:
    new_client = mock.Mock()

    with mock.patch(KUBECTL, return_value=completed(make_testruns())):
        verify_metrics(make_context(), new_client, timeout=4)

    new_client.assert_not_called()
