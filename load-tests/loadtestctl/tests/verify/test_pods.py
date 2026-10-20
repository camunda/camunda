from unittest import mock

import pytest

from loadtestctl.verify.context import VerifyError
from loadtestctl.verify.pods import wait_for_pods

from .helpers import completed
from .helpers import make_context

KUBECTL = "loadtestctl.verify.pods.kubectl"


def test_should_return_when_pods_are_ready() -> None:
    # given
    with mock.patch(KUBECTL, return_value=completed("pod/a condition met")) as kubectl:
        # when
        wait_for_pods(make_context(), "app=x", "pods")

    # then
    assert kubectl.call_count == 1
    assert kubectl.call_args.args[:5] == ("wait", "--for=condition=ready", "pod", "-l", "app=x")


def test_should_retry_until_pods_are_ready() -> None:
    # given
    responses = [completed("NotFound", 1), completed("a Running"), completed("ok")]

    with mock.patch(KUBECTL, side_effect=responses) as kubectl:
        # when
        wait_for_pods(make_context(), "app=x", "pods")

    # then
    assert kubectl.call_count == 3


def test_should_fail_when_retries_are_exhausted() -> None:
    # given
    with (
        mock.patch(KUBECTL, return_value=completed("timeout", 1)),
        pytest.raises(VerifyError, match="after 2 attempts"),
    ):
        wait_for_pods(make_context(wait_retries=2), "app=x", "pods")


def test_should_delete_out_of_cpu_pods() -> None:
    # given
    pods = "zeebe-0 0/1 OutOfcpu 0 1m\nzeebe-1 1/1 Running 0 1m\n"
    responses = [completed("fail", 1), completed(pods), completed(""), completed("ok")]

    with mock.patch(KUBECTL, side_effect=responses) as kubectl:
        # when
        wait_for_pods(make_context(), "app=x", "pods")

    # then
    assert kubectl.call_args_list[2].args == ("delete", "pod", "zeebe-0", "-n", "c8-test")


def test_should_only_delete_pods_whose_status_is_out_of_cpu() -> None:
    # given
    pods = "outofcpu-worker 1/1 Running 0 1m node-outofcpu\nzeebe-0 0/1 OutOfcpu 0 1m node-a\n"
    responses = [completed("fail", 1), completed(pods), completed(""), completed("ok")]

    with mock.patch(KUBECTL, side_effect=responses) as kubectl:
        # when
        wait_for_pods(make_context(), "app=x", "pods")

    # then
    assert kubectl.call_args_list[2].args == ("delete", "pod", "zeebe-0", "-n", "c8-test")
