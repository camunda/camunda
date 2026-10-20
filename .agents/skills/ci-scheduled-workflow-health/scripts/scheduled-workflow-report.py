#!/usr/bin/env python3
"""
Generate an HTML report of failing scheduled GitHub Actions workflows.

Extracts scheduled workflow filenames from git main branch, queries the
GitHub API for recent runs, and outputs an HTML report of failures.
"""

import json
import re
import subprocess
import sys
from datetime import datetime, timezone


OWNER = "camunda"
REPO = "camunda"
# How many recent runs per workflow to check
RUNS_TO_CHECK = 5


def git_list_workflow_files():
    """List workflow files from the main branch via git ls-tree (plumbing)."""
    result = subprocess.run(
        ["git", "ls-tree", "--name-only", "main", ".github/workflows/"],
        capture_output=True, text=True, check=True,
    )
    # Each line is ".github/workflows/<filename>"; extract just the filename
    return [
        line.rsplit("/", 1)[-1]
        for line in result.stdout.splitlines()
        if line.strip()
    ]


def git_read_workflow(filename):
    """Read a workflow file from the main branch."""
    result = subprocess.run(
        ["git", "show", f"main:.github/workflows/{filename}"],
        capture_output=True, text=True, check=True,
    )
    return result.stdout


def git_file_has_schedule(content):
    """Check if workflow content contains a schedule trigger."""
    return "schedule:" in content


def extract_owner(content):
    """Extract owner from '# owner: ...' comment in the first 20 lines."""
    for line in content.splitlines()[:20]:
        m = re.match(r'^\s*#\s*owner:?\s*(.+)', line, re.IGNORECASE)
        if m:
            owner = m.group(1).strip()
            # Some lines have "# owner @team" (without colon after owner)
            owner = re.sub(r'^#\s*owner\s*', '', owner, flags=re.IGNORECASE).strip()
            if owner:
                return owner
    return None


def gh_api(endpoint):
    """Call GitHub API via gh CLI."""
    result = subprocess.run(
        ["gh", "api", endpoint],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(f"  WARNING: API call failed for {endpoint}: {result.stderr.strip()}", file=sys.stderr)
        return None
    return json.loads(result.stdout)


def get_recent_runs(workflow_filename):
    """Get recent workflow runs for a given workflow file."""
    endpoint = (
        f"/repos/{OWNER}/{REPO}/actions/workflows/{workflow_filename}/runs"
        f"?per_page={RUNS_TO_CHECK}&branch=main&event=schedule"
    )
    data = gh_api(endpoint)
    if data is None:
        return []
    return data.get("workflow_runs", [])


def classify_workflow(runs):
    """Classify workflow status based on recent runs.
    Returns (status, details) where status is 'failing', 'flaky', 'ok', or 'no_runs'.
    """
    if not runs:
        return "no_runs", []

    details = []
    for run in runs:
        details.append({
            "id": run["id"],
            "conclusion": run.get("conclusion"),
            "status": run["status"],
            "created_at": run["created_at"],
            "html_url": run["html_url"],
            "run_number": run["run_number"],
        })

    conclusions = [r["conclusion"] for r in details if r["conclusion"] is not None]
    if not conclusions:
        return "in_progress", details

    if conclusions[0] == "failure":
        if all(c == "failure" for c in conclusions):
            return "failing", details
        return "flaky", details

    return "ok", details


def generate_html(workflows):
    """Generate an HTML report from classified workflow data."""
    now = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")

    # Separate into categories
    failing = [(w, o, s, d) for w, o, s, d in workflows if s == "failing"]
    flaky = [(w, o, s, d) for w, o, s, d in workflows if s == "flaky"]
    ok = [(w, o, s, d) for w, o, s, d in workflows if s == "ok"]
    no_runs = [(w, o, s, d) for w, o, s, d in workflows if s in ("no_runs", "in_progress")]

    def status_badge(status):
        colors = {
            "failing": "#d73a49",
            "flaky": "#e36209",
            "ok": "#28a745",
            "no_runs": "#6a737d",
            "in_progress": "#0366d6",
        }
        color = colors.get(status, "#6a737d")
        return f'<span style="background:{color};color:#fff;padding:2px 8px;border-radius:4px;font-size:0.85em;">{status}</span>'

    def conclusion_icon(conclusion):
        icons = {
            "success": "&#x2705;",
            "failure": "&#x274C;",
            "cancelled": "&#x23F9;",
            "skipped": "&#x23ED;",
            "timed_out": "&#x23F0;",
            None: "&#x23F3;",
        }
        return icons.get(conclusion, f"&#x2753; {conclusion}")

    def render_section(title, items, open_by_default=False):
        if not items:
            return ""
        open_attr = " open" if open_by_default else ""
        rows = []
        for wf_name, wf_owner, status, details in items:
            run_cells = ""
            for d in details[:RUNS_TO_CHECK]:
                created = d["created_at"][:10]
                url = d["html_url"]
                icon = conclusion_icon(d["conclusion"])
                run_cells += f'<a href="{url}" title="{d["conclusion"] or "in_progress"} on {created}" style="text-decoration:none;margin-right:4px;">{icon}</a>'
            wf_url = f"https://github.com/{OWNER}/{REPO}/actions/workflows/{wf_name}?query=event%3Aschedule"
            owner_cell = wf_owner or '<span style="color:#6a737d;">unknown</span>'
            rows.append(
                f"<tr>"
                f'<td><a href="{wf_url}">{wf_name}</a></td>'
                f"<td>{owner_cell}</td>"
                f"<td>{status_badge(status)}</td>"
                f"<td>{run_cells or 'N/A'}</td>"
                f"</tr>"
            )
        return f"""
        <details{open_attr}>
          <summary><strong>{title}</strong> ({len(items)})</summary>
          <table>
            <thead><tr><th>Workflow</th><th>Owner</th><th>Status</th><th>Recent Runs (newest first)</th></tr></thead>
            <tbody>{"".join(rows)}</tbody>
          </table>
        </details>
        """

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Scheduled Workflow Report — {OWNER}/{REPO}</title>
<style>
  body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif; margin: 2em; color: #24292e; }}
  h1 {{ border-bottom: 1px solid #e1e4e8; padding-bottom: 0.3em; }}
  table {{ border-collapse: collapse; width: 100%; margin: 0.5em 0 1.5em; }}
  th, td {{ border: 1px solid #e1e4e8; padding: 6px 12px; text-align: left; }}
  th {{ background: #f6f8fa; }}
  tr:hover {{ background: #f6f8fa; }}
  details {{ margin: 1em 0; }}
  summary {{ cursor: pointer; font-size: 1.1em; padding: 0.3em 0; }}
  a {{ color: #0366d6; text-decoration: none; }}
  a:hover {{ text-decoration: underline; }}
  .stats {{ display: flex; gap: 1.5em; margin: 1em 0; }}
  .stat {{ padding: 0.8em 1.2em; border-radius: 6px; }}
  .stat-failing {{ background: #ffeef0; border: 1px solid #d73a49; }}
  .stat-flaky {{ background: #fff8e1; border: 1px solid #e36209; }}
  .stat-ok {{ background: #e6ffed; border: 1px solid #28a745; }}
  .stat-other {{ background: #f1f8ff; border: 1px solid #0366d6; }}
  .legend {{ border-collapse: collapse; width: auto; margin: 0.3em 0; }}
  .legend th, .legend td {{ border: 1px solid #e1e4e8; padding: 4px 12px; }}
  .legend th {{ background: #f6f8fa; }}
</style>
</head>
<body>
<h1>Scheduled Workflow Report</h1>
<p>Repository: <a href="https://github.com/{OWNER}/{REPO}">{OWNER}/{REPO}</a> &mdash; Generated: {now}</p>
<p>Checked last {RUNS_TO_CHECK} scheduled runs on <code>main</code> for each workflow.</p>
<div class="stats">
  <div class="stat stat-failing"><strong>{len(failing)}</strong> Failing</div>
  <div class="stat stat-flaky"><strong>{len(flaky)}</strong> Flaky</div>
  <div class="stat stat-ok"><strong>{len(ok)}</strong> OK</div>
  <div class="stat stat-other"><strong>{len(no_runs)}</strong> No runs (yet, could be new) / In progress</div>
</div>
<details>
<summary><strong>Legend</strong></summary>
<table class="legend">
  <tr><th>Symbol</th><th>Meaning</th></tr>
  <tr><td>&#x2705;</td><td>Run succeeded</td></tr>
  <tr><td>&#x274C;</td><td>Run failed</td></tr>
  <tr><td>&#x23F9;</td><td>Run was cancelled</td></tr>
  <tr><td>&#x23ED;</td><td>Run was skipped</td></tr>
  <tr><td>&#x23F0;</td><td>Run timed out</td></tr>
  <tr><td>&#x23F3;</td><td>Run is in progress</td></tr>
  <tr><td>&#x2753;</td><td>Unknown / other conclusion</td></tr>
</table>
<table class="legend" style="margin-top:0.5em;">
  <tr><th>Badge</th><th>Meaning</th></tr>
  <tr><td><span style="background:#d73a49;color:#fff;padding:2px 8px;border-radius:4px;font-size:0.85em;">failing</span></td><td>Most recent run failed and all checked runs failed</td></tr>
  <tr><td><span style="background:#e36209;color:#fff;padding:2px 8px;border-radius:4px;font-size:0.85em;">flaky</span></td><td>Most recent run failed but some checked runs passed</td></tr>
  <tr><td><span style="background:#28a745;color:#fff;padding:2px 8px;border-radius:4px;font-size:0.85em;">ok</span></td><td>Most recent run passed</td></tr>
  <tr><td><span style="background:#6a737d;color:#fff;padding:2px 8px;border-radius:4px;font-size:0.85em;">no_runs</span></td><td>No scheduled runs found on main</td></tr>
  <tr><td><span style="background:#0366d6;color:#fff;padding:2px 8px;border-radius:4px;font-size:0.85em;">in_progress</span></td><td>All recent runs still in progress</td></tr>
</table>
</details>

{render_section("&#x274C; Failing (most recent run failed, all recent runs failed)", failing, open_by_default=True)}
{render_section("&#x26A0;&#xFE0F; Flaky (most recent run failed, but some recent runs passed)", flaky, open_by_default=True)}
{render_section("&#x2705; OK (most recent run passed)", ok, open_by_default=False)}
{render_section("&#x2754; No Runs / In Progress", no_runs, open_by_default=False)}
</body>
</html>"""
    return html


def main():
    print("Discovering scheduled workflows from git main branch...", file=sys.stderr)
    all_files = git_list_workflow_files()

    # Read all files and filter for scheduled ones, extracting owners
    scheduled = []  # list of (filename, owner)
    for f in all_files:
        content = git_read_workflow(f)
        if git_file_has_schedule(content):
            owner = extract_owner(content)
            scheduled.append((f, owner))
    print(f"Found {len(scheduled)} scheduled workflows.", file=sys.stderr)

    results = []
    for i, (wf, owner) in enumerate(scheduled, 1):
        print(f"  [{i}/{len(scheduled)}] Checking {wf}...", file=sys.stderr)
        runs = get_recent_runs(wf)
        status, details = classify_workflow(runs)
        results.append((wf, owner, status, details))

    # Sort: failing first, then flaky, then the rest
    order = {"failing": 0, "flaky": 1, "in_progress": 2, "no_runs": 3, "ok": 4}
    results.sort(key=lambda x: (order.get(x[2], 99), x[0]))

    html = generate_html(results)

    outfile = "scheduled-workflow-report.html"
    with open(outfile, "w") as f:
        f.write(html)
    print(f"\nReport written to {outfile}", file=sys.stderr)

    # Also print summary to stderr
    failing = [(w, o) for w, o, s, _ in results if s == "failing"]
    flaky = [(w, o) for w, o, s, _ in results if s == "flaky"]
    if failing:
        print(f"\nFAILING ({len(failing)}):", file=sys.stderr)
        for w, o in failing:
            print(f"  - {w}  [{o or 'unknown'}]", file=sys.stderr)
    if flaky:
        print(f"\nFLAKY ({len(flaky)}):", file=sys.stderr)
        for w, o in flaky:
            print(f"  - {w}  [{o or 'unknown'}]", file=sys.stderr)
    if not failing and not flaky:
        print("\nAll scheduled workflows are passing!", file=sys.stderr)


if __name__ == "__main__":
    main()
