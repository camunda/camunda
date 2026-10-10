import os

import pytest


@pytest.fixture(autouse=True)
def clean_report_environment(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in [name for name in os.environ if name.startswith("LOADTESTCTL_REPORT_")]:
        monkeypatch.delenv(name)
