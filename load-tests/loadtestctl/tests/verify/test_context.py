import pytest

from loadtestctl.verify.context import group
from loadtestctl.verify.context import log
from loadtestctl.verify.context import log_error


def test_should_log_error_as_annotation_on_github_actions(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("GITHUB_ACTIONS", "true")

    log_error("first\nsecond 100%")

    assert capsys.readouterr().err == "::error::first%0Asecond 100%25\n"


def test_should_log_plain_error_outside_github_actions(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.delenv("GITHUB_ACTIONS", raising=False)

    log_error("boom")

    assert capsys.readouterr().err == "Error: boom\n"


def test_should_wrap_output_in_group_on_github_actions(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("GITHUB_ACTIONS", "true")

    with group("title"):
        log("inside")

    assert capsys.readouterr().err == "::group::title\ninside\n::endgroup::\n"


def test_should_end_group_when_the_body_fails(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("GITHUB_ACTIONS", "true")

    with pytest.raises(RuntimeError), group("title"):
        raise RuntimeError

    assert capsys.readouterr().err.endswith("::endgroup::\n")


def test_should_only_log_the_title_outside_github_actions(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.delenv("GITHUB_ACTIONS", raising=False)

    with group("title"):
        pass

    assert capsys.readouterr().err == "title\n"
