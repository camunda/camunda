import pytest


@pytest.fixture
def fake_token(monkeypatch):
    monkeypatch.setenv("GITHUB_TOKEN", "test-token-abc")
    return "test-token-abc"
