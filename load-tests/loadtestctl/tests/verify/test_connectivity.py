import io
from http.client import RemoteDisconnected
from unittest import mock

import pytest

from loadtestctl.verify.connectivity import free_local_port
from loadtestctl.verify.connectivity import verify_connected
from loadtestctl.verify.context import VerifyError

from .helpers import make_context

POPEN = "loadtestctl.verify.connectivity.subprocess.Popen"
URLOPEN = "loadtestctl.verify.connectivity.urlopen"


def metrics(body: str) -> mock.MagicMock:
    response = mock.MagicMock()
    response.__enter__.return_value = io.BytesIO(body.encode())
    return response


def test_should_succeed_when_client_is_connected() -> None:
    with mock.patch(POPEN) as popen, mock.patch(URLOPEN, return_value=metrics("# HELP\napp_connected 1.0\n")):
        verify_connected(make_context(), 9600, timeout=4)

    popen.return_value.terminate.assert_called_once()


def test_should_retry_until_client_is_connected() -> None:
    responses = [metrics("app_connected 0.0\n"), metrics("app_connected 1.0\n")]

    with mock.patch(POPEN), mock.patch(URLOPEN, side_effect=responses) as urlopen:
        verify_connected(make_context(), 9600, timeout=10)

    assert urlopen.call_count == 2


def test_should_fail_when_client_never_connects() -> None:
    with (
        mock.patch(POPEN),
        mock.patch(URLOPEN, side_effect=OSError("refused")),
        pytest.raises(VerifyError, match="did not connect"),
    ):
        verify_connected(make_context(), 9600, timeout=4)


def test_should_retry_on_dropped_connections() -> None:
    responses = [RemoteDisconnected("closed"), metrics("app_connected 1.0\n")]

    with mock.patch(POPEN), mock.patch(URLOPEN, side_effect=responses) as urlopen:
        verify_connected(make_context(), 9600, timeout=10)

    assert urlopen.call_count == 2


def test_should_stop_at_the_deadline_including_the_time_spent_per_attempt() -> None:
    # given
    ctx = make_context()

    with (
        mock.patch(POPEN),
        mock.patch(URLOPEN, side_effect=OSError("refused")) as urlopen,
        pytest.raises(VerifyError, match="within 20s"),
    ):
        # when
        verify_connected(ctx, 9600, timeout=20)

    # then
    assert urlopen.call_count == 5
    assert ctx.clock() == 20


def test_should_pick_a_valid_local_port_independent_of_the_metrics_port() -> None:
    assert 1024 <= free_local_port() <= 65535

    with mock.patch(POPEN) as popen, mock.patch(URLOPEN, return_value=metrics("app_connected 1.0\n")):
        verify_connected(make_context(), 65535, timeout=4)

    local_port = int(popen.call_args.args[0][3].split(":")[0])
    assert 1024 <= local_port <= 65535
