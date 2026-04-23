import os
import subprocess

import httpx

REPO = "camunda/camunda"
WORKFLOW_LOAD_TEST = "camunda-load-test.yml"
WORKFLOW_CLEANUP = "camunda-load-test-clean-up.yml"
_API_BASE = "https://api.github.com"
_HEADERS_BASE = {
    "Accept": "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
}
_TIMEOUT = 30.0


def get_token() -> str:
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        return token
    try:
        result = subprocess.run(["gh", "auth", "token"], capture_output=True, text=True)
    except FileNotFoundError:
        raise RuntimeError(
            "GitHub CLI (gh) not found. Set GITHUB_TOKEN env var or install gh CLI."
        ) from None
    if result.returncode == 0 and result.stdout.strip():
        return result.stdout.strip()
    raise RuntimeError(
        "No GitHub token found. Run 'gh auth login' or set the GITHUB_TOKEN env var. "
        "Token needs scopes: repo, workflow."
    )


def _auth_headers() -> dict:
    return {**_HEADERS_BASE, "Authorization": f"Bearer {get_token()}"}


def _check_response(response: httpx.Response) -> None:
    if not response.is_success:
        error_text = response.text or response.reason_phrase or "Unknown error"
        raise RuntimeError(
            f"GitHub API error {response.status_code}: {error_text}"
        )


def _request(method: str, url: str, **kwargs) -> httpx.Response:
    try:
        response = getattr(httpx, method)(
            url, headers=_auth_headers(), timeout=_TIMEOUT, **kwargs
        )
    except httpx.RequestError as e:
        raise RuntimeError(f"GitHub API request failed (network/timeout): {e}") from e
    _check_response(response)
    return response


def dispatch_workflow(workflow_file: str, inputs: dict) -> None:
    """Trigger a workflow_dispatch event. inputs values must be strings."""
    url = f"{_API_BASE}/repos/{REPO}/actions/workflows/{workflow_file}/dispatches"
    str_inputs = {k: str(v) for k, v in inputs.items() if v != "" and v is not None}
    # ref here is the branch the workflow YAML is read from, not the branch under test.
    # Always using main ensures we run the stable workflow definition.
    _request("post", url, json={"ref": "main", "inputs": str_inputs})


def get_run_by_id(run_id: int) -> dict:
    url = f"{_API_BASE}/repos/{REPO}/actions/runs/{run_id}"
    return _request("get", url).json()


def get_current_user() -> str:
    """Return the authenticated GitHub user's login."""
    return _request("get", f"{_API_BASE}/user").json()["login"]


def list_recent_runs(workflow_file: str, limit: int = 20) -> list[dict]:
    url = f"{_API_BASE}/repos/{REPO}/actions/workflows/{workflow_file}/runs"
    return _request("get", url, params={"per_page": limit}).json().get("workflow_runs", [])


def list_runs_by_actor(workflow_file: str, actor: str, limit: int = 20) -> list[dict]:
    """List workflow runs triggered by a specific GitHub user."""
    url = f"{_API_BASE}/repos/{REPO}/actions/workflows/{workflow_file}/runs"
    return (
        _request("get", url, params={"actor": actor, "per_page": limit})
        .json()
        .get("workflow_runs", [])
    )
