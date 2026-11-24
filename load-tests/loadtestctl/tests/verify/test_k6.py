import json
from unittest import mock
from urllib.error import URLError

import pytest

from loadtestctl.verify.context import VerifyError
from loadtestctl.verify.k6 import reporting_tests
from loadtestctl.verify.k6 import verify_metrics
from loadtestctl.verify.k6 import wait_for_k6_statefulsets
from loadtestctl.verify.k6 import wait_for_testruns

from .helpers import completed
from .helpers import make_context

KUBECTL = "loadtestctl.verify.k6.kubectl"


def make_testruns(**stages: str) -> str:
    return json.dumps({"items": [{"metadata": {"name": n}, "status": {"stage": s}} for n, s in stages.items()]})


def make_statefulsets(*names: str) -> str:
    return json.dumps({"items": [{"metadata": {"name": n}} for n in names]})


def series(*testrun_names: str) -> list[mock.Mock]:
    return [mock.Mock(metric={"testrun_name": name}) for name in testrun_names]


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


def test_should_report_no_test_when_prometheus_is_unreachable() -> None:
    client = mock.Mock()
    client.query.side_effect = URLError("down")

    assert reporting_tests(client, "q") == set()


def test_should_return_the_testrun_name_of_each_series() -> None:
    client = mock.Mock()
    client.query.return_value.data.result = series("k6-default", "k6-data-read")

    assert reporting_tests(client, "q") == {"k6-default", "k6-data-read"}


def test_should_verify_metrics_once_reported() -> None:
    # given
    client = mock.Mock()
    client.query.return_value.data.result = series("k6-a")
    responses = [completed(make_testruns(**{"k6-a": "started"})), completed(make_statefulsets())]

    with mock.patch(KUBECTL, side_effect=responses):
        # when / then
        verify_metrics(make_context(), lambda: client, timeout=10)


def test_should_fail_when_metrics_never_arrive() -> None:
    client = mock.Mock()
    client.query.return_value.data.result = []
    responses = [completed(make_testruns(**{"k6-a": "started"})), completed(make_statefulsets())]

    with (
        mock.patch(KUBECTL, side_effect=responses),
        pytest.raises(VerifyError, match="k6 tests k6-a did not report metrics"),
    ):
        verify_metrics(make_context(), lambda: client, timeout=4)


def test_should_not_query_prometheus_without_k6_tests() -> None:
    client = mock.Mock()

    with mock.patch(KUBECTL, side_effect=[completed(make_testruns()), completed(make_statefulsets())]):
        verify_metrics(make_context(), lambda: client, timeout=4)

    client.query.assert_not_called()


def test_should_not_create_a_prometheus_client_without_k6_tests() -> None:
    new_client = mock.Mock()

    with mock.patch(KUBECTL, side_effect=[completed(make_testruns()), completed(make_statefulsets())]):
        verify_metrics(make_context(), new_client, timeout=4)

    new_client.assert_not_called()


def test_should_not_check_metrics_of_finished_testruns() -> None:
    new_client = mock.Mock()
    responses = [completed(make_testruns(**{"k6-a": "finished"})), completed(make_statefulsets())]

    with mock.patch(KUBECTL, side_effect=responses):
        verify_metrics(make_context(), new_client, timeout=4)

    new_client.assert_not_called()


def test_should_skip_when_there_is_no_statefulset() -> None:
    with (
        mock.patch(KUBECTL, return_value=completed(make_statefulsets())) as kubectl,
        mock.patch("loadtestctl.verify.k6.wait_for_pods") as wait_for_pods,
    ):
        wait_for_k6_statefulsets(make_context())

    assert kubectl.call_count == 1
    wait_for_pods.assert_not_called()


def test_should_fail_when_listing_statefulsets_fails() -> None:
    with (
        mock.patch(KUBECTL, return_value=completed("", 1)),
        pytest.raises(VerifyError, match="Unable to list k6 StatefulSets"),
    ):
        wait_for_k6_statefulsets(make_context())


def test_should_wait_for_the_pods_of_each_statefulset() -> None:
    # given
    statefulsets = make_statefulsets("k6-default", "k6-data-read")

    with (
        mock.patch(KUBECTL, return_value=completed(statefulsets)),
        mock.patch("loadtestctl.verify.k6.wait_for_pods") as wait_for_pods,
    ):
        # when
        wait_for_k6_statefulsets(make_context())

    # then
    assert [call.args[1] for call in wait_for_pods.call_args_list] == [
        "app.kubernetes.io/name=k6-default",
        "app.kubernetes.io/name=k6-data-read",
    ]


def test_should_fail_when_the_pods_of_a_statefulset_are_not_ready() -> None:
    with (
        mock.patch(KUBECTL, return_value=completed(make_statefulsets("k6-default"))),
        mock.patch("loadtestctl.verify.k6.wait_for_pods", side_effect=VerifyError("not ready")),
        pytest.raises(VerifyError, match="not ready"),
    ):
        wait_for_k6_statefulsets(make_context())


def test_should_verify_metrics_of_statefulsets() -> None:
    # given
    client = mock.Mock()
    client.query.return_value.data.result = series("k6-default")
    responses = [completed(make_testruns()), completed(make_statefulsets("k6-default"))]

    with mock.patch(KUBECTL, side_effect=responses):
        # when
        verify_metrics(make_context(), lambda: client, timeout=4)

    # then
    client.query.assert_called_once()


def test_should_fail_when_a_statefulset_reports_no_metrics() -> None:
    # given
    client = mock.Mock()
    client.query.return_value.data.result = series("k6-data-read")
    responses = [
        completed(make_testruns(**{"k6-data-read": "started"})),
        completed(make_statefulsets("k6-default")),
    ]

    with (
        mock.patch(KUBECTL, side_effect=responses),
        # when / then
        pytest.raises(VerifyError, match="k6 tests k6-default did not report metrics"),
    ):
        verify_metrics(make_context(), lambda: client, timeout=4)
