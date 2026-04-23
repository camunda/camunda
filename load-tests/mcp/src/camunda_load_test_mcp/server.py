import re
import time
from datetime import datetime, timezone

from mcp.server.fastmcp import FastMCP

from camunda_load_test_mcp import github, state
from camunda_load_test_mcp.dashboard import grafana_url, ttl_expiry_str


def _derive_namespace(branch: str) -> str:
    name = branch.lower()
    name = re.sub(r"[^a-z0-9-]", "-", name)
    name = re.sub(r"-+", "-", name).strip("-")[:40]
    date = datetime.now(timezone.utc).strftime("%Y%m%d")
    return f"{name}-{date}"


def _find_run_after_dispatch(
    workflow_file: str, triggered_after: datetime, limit: int = 10
) -> int | None:
    for _ in range(3):
        runs = github.list_recent_runs(workflow_file, limit=limit)
        for run in runs:
            created = datetime.fromisoformat(
                run["created_at"].replace("Z", "+00:00")
            )
            if created > triggered_after:
                return run["id"]
        time.sleep(2)
    return None


def create_server() -> FastMCP:
    mcp = FastMCP("Camunda Load Tests")
    mcp.tool()(start_load_test)
    mcp.tool()(update_load_test)
    mcp.tool()(get_load_test_status)
    mcp.tool()(list_load_tests)
    mcp.tool()(discover_load_tests)
    mcp.tool()(stop_load_test)
    return mcp


def start_load_test(
    branch: str,
    scenario: str = "typical",
    ttl_days: int = 1,
    name: str = "",
    secondary_storage: str = "elasticsearch",
    enable_optimize: bool = False,
    platform_helm_values: str = "",
) -> str:
    """
    Trigger a new Camunda load test via GitHub Actions.

    Args:
        branch: Git ref to test (branch name, tag, or commit SHA).
        scenario: Workload scenario — typical, realistic, latency, max, archiver.
        ttl_days: Days before the namespace is auto-deleted (default: 1).
        name: Namespace suffix override. Derived from branch if not set.
        secondary_storage: elasticsearch, opensearch, postgresql, or none.
        enable_optimize: Deploy Optimize alongside the platform.
        platform_helm_values: Arbitrary --set flags for the Helm chart (escape hatch).
    """
    namespace_name = name if name else _derive_namespace(branch)
    full_namespace = f"c8-{namespace_name}"
    triggered_at = datetime.now(timezone.utc)

    inputs: dict[str, str] = {
        "ref": branch,
        "name": namespace_name,
        "ttl": str(ttl_days),
        "scenario": scenario,
        "secondary-storage-type": secondary_storage,
        "enable-optimize": str(enable_optimize).lower(),
    }
    if platform_helm_values:
        inputs["platform-helm-values"] = platform_helm_values

    github.dispatch_workflow(github.WORKFLOW_LOAD_TEST, inputs)

    run_id = _find_run_after_dispatch(github.WORKFLOW_LOAD_TEST, triggered_at)
    started_at = triggered_at.strftime("%Y-%m-%dT%H:%M:%SZ")

    state.write_entry(
        full_namespace,
        {
            "run_id": run_id,
            "branch": branch,
            "scenario": scenario,
            "image_tag": "",
            "started_at": started_at,
            "ttl_days": ttl_days,
        },
    )

    run_url = (
        f"https://github.com/camunda/camunda/actions/runs/{run_id}"
        if run_id
        else "https://github.com/camunda/camunda/actions/workflows/camunda-load-test.yml"
    )

    return (
        f"Load test triggered.\n\n"
        f"Namespace:  {full_namespace}\n"
        f"Branch:     {branch}\n"
        f"Scenario:   {scenario}\n"
        f"Expires:    {ttl_expiry_str(started_at, ttl_days)}\n"
        f"GHA run:    {run_url}\n"
        f"Dashboard:  {grafana_url(full_namespace)}"
    )


def update_load_test(
    namespace: str,
    reuse_image: bool = True,
    scenario: str = "",
    platform_helm_values: str = "",
    enable_optimize: bool | None = None,
) -> str:
    """
    Redeploy an existing load test namespace with updated config or a fresh image.

    Args:
        namespace: Full namespace name (e.g. c8-my-test-20260416).
        reuse_image: True = skip Docker build, keep current image. False = rebuild from branch.
        scenario: Override the scenario. Defaults to the original scenario if not set.
        platform_helm_values: Override Helm values.
        enable_optimize: Override the Optimize flag.
    """
    entry = state.read_entry(namespace)
    if entry is None:
        raise ValueError(
            f"Namespace '{namespace}' not found in local state. "
            "Use list_load_tests to see known namespaces."
        )

    name_suffix = namespace.removeprefix("c8-")
    triggered_at = datetime.now(timezone.utc)

    inputs: dict[str, str] = {
        "ref": entry["branch"],
        "name": name_suffix,
        "ttl": str(entry["ttl_days"]),
        "scenario": scenario or entry["scenario"],
    }
    if reuse_image:
        tag = entry.get("image_tag") or ""
        if not tag:
            raise ValueError(
                f"Cannot reuse image for '{namespace}': image tag is not tracked in local state. "
                "Run an update with reuse_image=False to build and record a new image."
            )
        inputs["reuse-tag"] = tag
    else:
        inputs["reuse-tag"] = ""

    if platform_helm_values:
        inputs["platform-helm-values"] = platform_helm_values
    if enable_optimize is not None:
        inputs["enable-optimize"] = str(enable_optimize).lower()

    github.dispatch_workflow(github.WORKFLOW_LOAD_TEST, inputs)

    run_id = _find_run_after_dispatch(github.WORKFLOW_LOAD_TEST, triggered_at)
    entry["run_id"] = run_id
    if scenario:
        entry["scenario"] = scenario
    state.write_entry(namespace, entry)

    run_url = (
        f"https://github.com/camunda/camunda/actions/runs/{run_id}"
        if run_id
        else "https://github.com/camunda/camunda/actions/workflows/camunda-load-test.yml"
    )
    mode = "reusing existing image" if reuse_image else "rebuilding image from branch"
    return (
        f"Update triggered ({mode}).\n\n"
        f"Namespace:  {namespace}\n"
        f"GHA run:    {run_url}\n"
        f"Dashboard:  {grafana_url(namespace)}"
    )


def get_load_test_status(namespace: str) -> str:
    """
    Get the current status of a load test run.

    Args:
        namespace: Full namespace name (e.g. c8-my-test-20260416).
    """
    entry = state.read_entry(namespace)
    if entry is None:
        raise ValueError(
            f"Namespace '{namespace}' not found in local state. "
            "Use list_load_tests to see known namespaces."
        )

    run_id = entry.get("run_id")
    if run_id:
        run = github.get_run_by_id(run_id)
        status = run.get("status", "unknown")
        conclusion = run.get("conclusion") or ""
        run_url = run.get("html_url", "")
        state_str = f"{status}" + (f" ({conclusion})" if conclusion else "")
    else:
        state_str = "unknown (run ID not tracked — check GHA directly)"
        run_url = "https://github.com/camunda/camunda/actions/workflows/camunda-load-test.yml"

    return (
        f"Status:     {state_str}\n"
        f"Namespace:  {namespace}\n"
        f"Branch:     {entry['branch']}\n"
        f"Scenario:   {entry['scenario']}\n"
        f"Expires:    {ttl_expiry_str(entry['started_at'], entry['ttl_days'])}\n"
        f"GHA run:    {run_url}\n"
        f"Dashboard:  {grafana_url(namespace)}"
    )


def list_load_tests(limit: int = 10) -> str:
    """
    List recent load tests tracked in local state.

    Args:
        limit: Maximum number of entries to show (default: 10).
    """
    entries = state.list_entries(limit=limit)
    if not entries:
        return "No load tests found in local state."

    lines = [
        f"{'Namespace':<40} {'Branch':<30} {'Scenario':<12} {'Status':<20} {'Expires'}"
    ]
    lines.append("-" * 120)

    for namespace, entry in reversed(entries):
        run_id = entry.get("run_id")
        if run_id:
            try:
                run = github.get_run_by_id(run_id)
                status = run.get("status", "unknown")
                conclusion = run.get("conclusion") or ""
                status_str = f"{status}" + (f"/{conclusion}" if conclusion else "")
            except Exception:
                status_str = "unknown"
        else:
            status_str = "unknown"

        expiry = ttl_expiry_str(entry["started_at"], entry["ttl_days"])
        branch = entry.get("branch", "")[:28]
        lines.append(
            f"{namespace:<40} {branch:<30} {entry.get('scenario', ''):<12} "
            f"{status_str:<20} {expiry}"
        )

    return "\n".join(lines)


def discover_load_tests(limit: int = 20) -> str:
    """
    Discover load tests started by the current GitHub user, including runs not
    tracked in local state (e.g. started via gh CLI or the GitHub UI).

    Queries GitHub Actions directly and merges with local state where available.

    Args:
        limit: Maximum number of recent GHA runs to inspect (default: 20).
    """
    user = github.get_current_user()
    runs = github.list_runs_by_actor(github.WORKFLOW_LOAD_TEST, user, limit=limit)

    if not runs:
        return f"No recent load test runs found for {user}."

    local = dict(state.list_entries(limit=10000))

    header = f"{'Namespace':<42} {'Branch':<30} {'Scenario':<12} {'Status':<25} Started"
    separator = "-" * 130
    lines = [f"Recent load test runs by {user}:", "", header, separator]

    found = 0
    for run in runs:
        try:
            detail = github.get_run_by_id(run["id"])
            inputs = detail.get("inputs") or {}
        except Exception:
            inputs = {}

        name = inputs.get("name", "")
        if not name:
            continue

        namespace = f"c8-{name}"
        found += 1

        status = run.get("status", "unknown")
        conclusion = run.get("conclusion") or ""
        status_str = status + (f"/{conclusion}" if conclusion else "")

        local_entry = local.get(namespace, {})
        branch = (local_entry.get("branch") or inputs.get("ref", ""))[:28]
        scenario = local_entry.get("scenario") or inputs.get("scenario", "")
        started_at = run.get("created_at", "")[:16].replace("T", " ")

        lines.append(
            f"{namespace:<42} {branch:<30} {scenario:<12} {status_str:<25} {started_at}"
        )

    if found == 0:
        return f"No load test runs with a tracked namespace found for {user}."

    return "\n".join(lines)


def stop_load_test(namespace: str) -> str:
    """
    Trigger early cleanup of a load test namespace.

    NOTE: The cleanup workflow deletes ALL namespaces whose TTL has expired on or
    before today's date — not just the one specified.

    Args:
        namespace: Full namespace name (e.g. c8-my-test-20260416). Used for reference only.
    """
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    github.dispatch_workflow(github.WORKFLOW_CLEANUP, {"date": today})

    return (
        f"Cleanup workflow triggered for date {today}.\n\n"
        f"WARNING: All load test namespaces with a TTL expiring on or before {today} "
        f"will be deleted — not only {namespace}.\n\n"
        f"GHA cleanup run: https://github.com/camunda/camunda/actions/workflows/"
        f"camunda-load-test-clean-up.yml"
    )
