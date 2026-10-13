from pathlib import Path

import pytest

from loadtestctl import github


def test_should_print_the_output_without_github_output(
    monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.delenv("GITHUB_OUTPUT", raising=False)

    github.output("status", "success")

    assert capsys.readouterr().out == "status=success\n"


def test_should_append_the_output_to_github_output(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    github_output = tmp_path / "output"
    github_output.write_text("existing=1\n")
    monkeypatch.setenv("GITHUB_OUTPUT", str(github_output))

    github.output("status", "failure")

    assert github_output.read_text() == "existing=1\nstatus=failure\n"
    assert capsys.readouterr().out == ""
