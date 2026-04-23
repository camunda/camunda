import pytest
import httpx
from pytest_mock import MockerFixture
from camunda_load_test_mcp import github as github_module


def test_get_token_from_env(fake_token):
    assert github_module.get_token() == "test-token-abc"


def test_get_token_falls_back_to_gh_cli(monkeypatch, mocker: MockerFixture):
    monkeypatch.delenv("GITHUB_TOKEN", raising=False)
    mock_run = mocker.patch("subprocess.run")
    mock_run.return_value.returncode = 0
    mock_run.return_value.stdout = "cli-token\n"
    token = github_module.get_token()
    assert token == "cli-token"
    mock_run.assert_called_once_with(
        ["gh", "auth", "token"], capture_output=True, text=True
    )


def test_get_token_raises_when_no_auth(monkeypatch, mocker: MockerFixture):
    monkeypatch.delenv("GITHUB_TOKEN", raising=False)
    mock_run = mocker.patch("subprocess.run")
    mock_run.return_value.returncode = 1
    mock_run.return_value.stdout = ""
    with pytest.raises(RuntimeError, match="gh auth login"):
        github_module.get_token()


def test_dispatch_workflow_sends_correct_request(fake_token, mocker: MockerFixture):
    mock_post = mocker.patch("httpx.post")
    mock_post.return_value.status_code = 204
    mock_post.return_value.is_success = True

    github_module.dispatch_workflow(
        "camunda-load-test.yml",
        inputs={"name": "my-test", "ref": "feature/x", "ttl": "1"},
    )

    mock_post.assert_called_once()
    call_kwargs = mock_post.call_args
    assert "camunda-load-test.yml" in call_kwargs.args[0]
    assert call_kwargs.kwargs["json"]["inputs"]["name"] == "my-test"
    assert "Bearer test-token-abc" in call_kwargs.kwargs["headers"]["Authorization"]


def test_dispatch_workflow_raises_on_error(fake_token, mocker: MockerFixture):
    mock_post = mocker.patch("httpx.post")
    mock_post.return_value.status_code = 422
    mock_post.return_value.is_success = False
    mock_post.return_value.text = "Workflow not found"
    mock_post.return_value.reason_phrase = "Unprocessable Entity"

    with pytest.raises(RuntimeError, match="422"):
        github_module.dispatch_workflow("bad-workflow.yml", inputs={})


def test_get_run_by_id_returns_run(fake_token, mocker: MockerFixture):
    mock_get = mocker.patch("httpx.get")
    mock_get.return_value.status_code = 200
    mock_get.return_value.is_success = True
    mock_get.return_value.json.return_value = {
        "id": 999,
        "status": "in_progress",
        "html_url": "https://github.com/camunda/camunda/actions/runs/999",
    }

    run = github_module.get_run_by_id(999)

    assert run["id"] == 999
    assert run["status"] == "in_progress"


def test_list_recent_runs_returns_list(fake_token, mocker: MockerFixture):
    mock_get = mocker.patch("httpx.get")
    mock_get.return_value.status_code = 200
    mock_get.return_value.is_success = True
    mock_get.return_value.json.return_value = {
        "workflow_runs": [
            {"id": 1, "status": "completed"},
            {"id": 2, "status": "in_progress"},
        ]
    }

    runs = github_module.list_recent_runs("camunda-load-test.yml", limit=10)

    assert len(runs) == 2
    assert runs[0]["id"] == 1


def test_dispatch_workflow_raises_on_network_error(fake_token, mocker: MockerFixture):
    mock_post = mocker.patch("httpx.post")
    mock_post.side_effect = httpx.ConnectError("Connection refused")

    with pytest.raises(RuntimeError, match="network/timeout"):
        github_module.dispatch_workflow("camunda-load-test.yml", inputs={})


def test_get_current_user_returns_login(fake_token, mocker: MockerFixture):
    mock_get = mocker.patch("httpx.get")
    mock_get.return_value.status_code = 200
    mock_get.return_value.is_success = True
    mock_get.return_value.json.return_value = {"login": "ChrisKujawa", "id": 123}

    user = github_module.get_current_user()

    assert user == "ChrisKujawa"
    mock_get.assert_called_once()
    assert mock_get.call_args.args[0].endswith("/user")


def test_list_runs_by_actor_passes_actor_param(fake_token, mocker: MockerFixture):
    mock_get = mocker.patch("httpx.get")
    mock_get.return_value.status_code = 200
    mock_get.return_value.is_success = True
    mock_get.return_value.json.return_value = {"workflow_runs": []}

    github_module.list_runs_by_actor("camunda-load-test.yml", "ChrisKujawa", limit=10)

    mock_get.assert_called_once()
    assert mock_get.call_args.kwargs["params"]["actor"] == "ChrisKujawa"
    assert mock_get.call_args.kwargs["params"]["per_page"] == 10


def test_list_runs_by_actor_returns_runs(fake_token, mocker: MockerFixture):
    mock_get = mocker.patch("httpx.get")
    mock_get.return_value.status_code = 200
    mock_get.return_value.is_success = True
    mock_get.return_value.json.return_value = {
        "workflow_runs": [
            {"id": 10, "status": "completed", "actor": {"login": "ChrisKujawa"}},
            {"id": 11, "status": "in_progress", "actor": {"login": "ChrisKujawa"}},
        ]
    }

    runs = github_module.list_runs_by_actor("camunda-load-test.yml", "ChrisKujawa")

    assert len(runs) == 2
    assert runs[0]["id"] == 10
