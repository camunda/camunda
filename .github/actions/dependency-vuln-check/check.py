#!/usr/bin/env python3
"""PR vulnerability gate — GitHub Dependency Review API.

Blocks on fixable vulns (any severity) and high/critical unfixable vulns.
Covers the downgrade scenario: a version downgrade shows the old version as
"removed" and the new vulnerable version as "added" in the diff.
"""
import json
import os
import re
import sys
import urllib.error
import urllib.request

import yaml

_GITHUB_API = "https://api.github.com"
_COMMENT_MARKER = "<!-- dependency-vuln-check -->"


def _api_get(url: str, token: str) -> list:
    """Fetch all pages of a GitHub API GET response."""
    results, next_url = [], url
    while next_url:
        req = urllib.request.Request(
            next_url,
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        with urllib.request.urlopen(req, timeout=30) as resp:
            results.extend(json.loads(resp.read()))
            match = re.search(r'<([^>]+)>;\s*rel="next"', resp.headers.get("Link", ""))
            next_url = match.group(1) if match else None
    return results


def _ghsa_ids(vuln: dict) -> set:
    """Extract all GHSA IDs for a vulnerability, falling back to parsing advisory_url."""
    ids = set(vuln.get("advisory_ghsa_ids") or [])
    url = vuln.get("advisory_url", "")
    if not ids and url:
        m = re.search(r"(GHSA-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4})", url, re.IGNORECASE)
        if m:
            ids.add(m.group(1).upper())
    return ids


def _graphql(query: str, variables: dict, token: str) -> dict:
    req = urllib.request.Request(
        f"{_GITHUB_API}/graphql",
        data=json.dumps({"query": query, "variables": variables}).encode(),
        method="POST",
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())


_PATCH_QUERY = """
query($ghsa: String!) {
  securityAdvisory(ghsaId: $ghsa) {
    vulnerabilities(first: 20) {
      nodes {
        package { ecosystem name }
        firstPatchedVersion { identifier }
      }
    }
  }
}
"""

_patch_cache: dict[tuple[str, str, str], str | None] = {}


def _lookup_patch(ghsa: str, ecosystem: str, package: str, token: str) -> str | None:
    """Query the GitHub Advisory GraphQL API for the first patched version."""
    key = (ghsa, ecosystem.lower(), package.lower())
    if key in _patch_cache:
        return _patch_cache[key]
    try:
        canonical = "GHSA-" + ghsa[5:].lower()  # API requires GHSA- prefix uppercase, hex lowercase
        data = _graphql(_PATCH_QUERY, {"ghsa": canonical}, token)
        nodes = (data.get("data") or {}).get("securityAdvisory") or {}
        nodes = (nodes.get("vulnerabilities") or {}).get("nodes") or []
        for node in nodes:
            pkg = node.get("package") or {}
            if pkg.get("ecosystem", "").lower() == ecosystem.lower() and pkg.get("name", "").lower() == package.lower():
                v = (node.get("firstPatchedVersion") or {}).get("identifier")
                _patch_cache[key] = v
                return v
    except Exception:
        pass
    _patch_cache[key] = None
    return None


def find_blocking(diff: list, allowed_ghsas: set, token: str) -> tuple[list, list, list]:
    """Return (blocking, allowed, dev_excluded).

    blocking     – vulns that block the PR
    allowed      – would have blocked but are in allow-ghsas
    dev_excluded – would have blocked if runtime; found in development-scoped deps
    """
    blocking, allowed, dev_excluded = [], [], []
    for dep in diff:
        if dep.get("change_type") != "added":
            continue
        is_dev = dep.get("scope") == "development"
        for vuln in dep.get("vulnerabilities") or []:
            ids = _ghsa_ids(vuln)
            ghsa = sorted(ids)[0] if ids else "unknown"
            fix = vuln.get("first_patched_version")
            if fix is None and ghsa != "unknown":
                fix = _lookup_patch(ghsa, dep.get("ecosystem", ""), dep["name"], token)
            fixable = fix is not None
            would_block = fixable or vuln.get("severity") in ("high", "critical")
            if not would_block:
                continue
            entry = {
                "package": f"{dep['name']}@{dep['version']}",
                "ecosystem": dep.get("ecosystem", ""),
                "severity": vuln.get("severity", ""),
                "ghsa": ghsa,
                "url": vuln.get("advisory_url", ""),
                "fix": fix,
                "rule": "fixable" if fixable else "no-fix/high-severity",
            }
            if is_dev:
                dev_excluded.append(entry)
            elif ids & allowed_ghsas:
                allowed.append(entry)
            else:
                blocking.append(entry)
    return blocking, allowed, dev_excluded


def _table(entries: list) -> str:
    rows = "\n".join(
        f"| {b['package']} | {b['ecosystem']} | {b['severity']} | {b['rule']}"
        f" | {('`' + b['fix'] + '`') if b['fix'] else 'none'} | [{b['ghsa']}]({b['url']}) |"
        for b in entries
    )
    return "| Package | Ecosystem | Severity | Rule | Fix | Advisory |\n|---------|-----------|----------|------|-----|----------|\n" + rows


def _api_write(url: str, token: str, method: str, body: dict) -> None:
    req = urllib.request.Request(
        url,
        data=json.dumps(body).encode(),
        method=method,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        resp.read()


def post_pr_comment(
    repository: str, pr_number: int, blocking: list, allowed: list, dev_excluded: list, token: str
) -> None:
    sections = [_COMMENT_MARKER]

    if blocking:
        sections += [
            f"## Vulnerability Gate: {len(blocking)} blocking issue(s) found",
            "",
            _table(blocking),
            "",
            "### What to do",
            "- **Fixable**: upgrade the dependency to the fix version shown above.",
            "- **No fix available**: add the GHSA ID to `.github/dependency-review-config.yml`",
            "  with a reason and review-by date, then request `@camunda/monorepo-devops-team` approval.",
        ]
    else:
        sections += ["## Vulnerability Gate: no blocking issues found"]

    if allowed:
        sections += [
            "",
            f"### {len(allowed)} exception(s) active — covered by allow-ghsas (not blocking)",
            "",
            "> These vulnerabilities would have blocked this PR but are listed in `allow-ghsas`. "
            "Consider upgrading if a fix is available.",
            "",
            _table(allowed),
        ]

    if dev_excluded:
        sections += [
            "",
            f"### {len(dev_excluded)} vulnerability(ies) in development/test-scoped deps (not blocking)",
            "",
            "> These are in test-only dependencies and are excluded from blocking. "
            "Consider upgrading if a fix is available.",
            "",
            _table(dev_excluded),
        ]

    body = "\n".join(sections)
    comments_url = f"{_GITHUB_API}/repos/{repository}/issues/{pr_number}/comments"
    existing_id = next(
        (c["id"] for c in _api_get(comments_url, token) if _COMMENT_MARKER in c.get("body", "")),
        None,
    )
    if existing_id:
        _api_write(f"{_GITHUB_API}/repos/{repository}/issues/comments/{existing_id}", token, "PATCH", {"body": body})
    else:
        _api_write(comments_url, token, "POST", {"body": body})


def main() -> None:
    config_file = os.environ.get("CONFIG_FILE", ".github/dependency-review-config.yml")
    token = os.environ["GH_TOKEN"]
    repository = os.environ["GITHUB_REPOSITORY"]
    base_sha = os.environ["BASE_SHA"]
    head_sha = os.environ["HEAD_SHA"]
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")

    with open(config_file) as f:
        allowed_ghsas = set(g.upper() for g in ((yaml.safe_load(f) or {}).get("allow-ghsas") or []))

    try:
        diff = _api_get(
            f"{_GITHUB_API}/repos/{repository}/dependency-graph/compare/{base_sha}...{head_sha}",
            token,
        )
    except (urllib.error.HTTPError, urllib.error.URLError) as e:
        msg = f"Dependency Review API unavailable ({e}) — skipping vulnerability gate"
        print(f"::warning::{msg}")
        if summary_path:
            with open(summary_path, "a") as f:
                f.write(f"Warning: {msg}\n")
        return

    blocking, allowed, dev_excluded = find_blocking(diff, allowed_ghsas, token)

    for a in allowed:
        print(f"::notice::Allowed exception: {a['ghsa']} in {a['package']} (severity: {a['severity']}, rule: {a['rule']}) — covered by allow-ghsas")
    for d in dev_excluded:
        print(f"::notice::Dev/test dep: {d['ghsa']} in {d['package']} (severity: {d['severity']}, rule: {d['rule']}) — excluded (development scope)")

    if summary_path:
        with open(summary_path, "a") as f:
            if blocking:
                f.write("### Blocking\n" + _table(blocking) + "\n")
            else:
                f.write("No blocking vulnerabilities found.\n")
            if allowed:
                f.write("### Allowed exceptions\n" + _table(allowed) + "\n")
            if dev_excluded:
                f.write("### Development/test-scoped (not blocking)\n" + _table(dev_excluded) + "\n")

    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if event_path and (blocking or allowed or dev_excluded):
        with open(event_path) as f:
            pr_number = json.load(f).get("pull_request", {}).get("number")
        if pr_number:
            try:
                post_pr_comment(repository, pr_number, blocking, allowed, dev_excluded, token)
            except urllib.error.HTTPError as e:
                if e.code in (403, 404):
                    print(f"::warning::Cannot post PR comment (HTTP {e.code}) — token may lack pull-requests:write (fork PR?)")
                else:
                    raise
        else:
            print(f"::warning::Could not determine PR number from event payload — skipping PR comment (event_path={event_path!r})")

    if blocking:
        for b in blocking:
            print(f"::error::Blocking: {b['ghsa']} in {b['package']} (severity: {b['severity']}, rule: {b['rule']})")
        sys.exit(1)


if __name__ == "__main__":
    main()
