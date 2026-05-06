#!/usr/bin/env python3
"""GitHub API helpers for the code quality AI pipeline.

Authentication uses the GITHUB_TOKEN environment variable, which is
provided automatically when running inside a GitHub Actions workflow.
"""
from __future__ import annotations

import os
import re
from pathlib import Path

import requests

GITHUB_API = "https://api.github.com"
TIMEOUT_SECONDS = 30


def _auth_headers() -> dict[str, str]:
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        raise RuntimeError("GITHUB_TOKEN environment variable is required.")
    return {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def open_issue(
    repo: str,
    title: str,
    body: str,
    labels: list[str] | None = None,
    assignees: list[str] | None = None,
) -> int:
    """Open a GitHub issue in `<owner>/<name>`. Returns the issue number."""
    payload: dict = {"title": title, "body": body}
    if labels:
        payload["labels"] = labels
    if assignees:
        payload["assignees"] = assignees
    resp = requests.post(
        f"{GITHUB_API}/repos/{repo}/issues",
        headers=_auth_headers(),
        json=payload,
        timeout=TIMEOUT_SECONDS,
    )
    resp.raise_for_status()
    return resp.json()["number"]


def open_pr(
    repo: str,
    title: str,
    body: str,
    head: str,
    base: str = "main",
    draft: bool = False,
) -> int:
    """Open a pull request in `<owner>/<name>`. Returns the PR number."""
    resp = requests.post(
        f"{GITHUB_API}/repos/{repo}/pulls",
        headers=_auth_headers(),
        json={
            "title": title,
            "body": body,
            "head": head,
            "base": base,
            "draft": draft,
        },
        timeout=TIMEOUT_SECONDS,
    )
    resp.raise_for_status()
    return resp.json()["number"]


def add_labels(repo: str, number: int, labels: list[str]) -> None:
    """Add labels to an issue or PR (PRs share the issues endpoint)."""
    resp = requests.post(
        f"{GITHUB_API}/repos/{repo}/issues/{number}/labels",
        headers=_auth_headers(),
        json={"labels": labels},
        timeout=TIMEOUT_SECONDS,
    )
    resp.raise_for_status()


def request_reviewers(
    repo: str,
    pr_number: int,
    reviewers: list[str] | None = None,
    team_reviewers: list[str] | None = None,
) -> None:
    """Request user and/or team reviewers on a PR."""
    payload: dict = {}
    if reviewers:
        payload["reviewers"] = reviewers
    if team_reviewers:
        payload["team_reviewers"] = team_reviewers
    if not payload:
        return
    resp = requests.post(
        f"{GITHUB_API}/repos/{repo}/pulls/{pr_number}/requested_reviewers",
        headers=_auth_headers(),
        json=payload,
        timeout=TIMEOUT_SECONDS,
    )
    resp.raise_for_status()


_CODEOWNERS_LOCATIONS = (
    "CODEOWNERS",
    ".github/CODEOWNERS",
    "docs/CODEOWNERS",
)


def _load_codeowners(repo_root: Path) -> list[tuple[str, list[str]]]:
    """Parse CODEOWNERS into ordered (pattern, owners) tuples."""
    for relative in _CODEOWNERS_LOCATIONS:
        path = repo_root / relative
        if path.is_file():
            entries: list[tuple[str, list[str]]] = []
            for raw_line in path.read_text().splitlines():
                line = raw_line.strip()
                if not line or line.startswith("#"):
                    continue
                pattern, *owners = line.split()
                if owners:
                    entries.append((pattern, owners))
            return entries
    return []


def _pattern_to_regex(pattern: str) -> re.Pattern[str]:
    anchored = pattern.startswith("/")
    if anchored:
        pattern = pattern.lstrip("/")
    body = re.escape(pattern).replace(r"\*\*", ".*").replace(r"\*", "[^/]*")
    if pattern.endswith("/"):
        body += ".*"
    if anchored:
        body = "^" + body
    return re.compile(body)


def lookup_codeowners(path: str, repo_root: Path | None = None) -> list[str]:
    """Return CODEOWNERS for a file path. The last matching rule wins."""
    repo_root = repo_root or Path.cwd()
    matched: list[str] = []
    for pattern, owners in _load_codeowners(repo_root):
        if _pattern_to_regex(pattern).search(path):
            matched = owners
    return matched
