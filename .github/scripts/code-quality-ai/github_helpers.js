#!/usr/bin/env node
/**
 * GitHub API helpers for the code quality AI pipeline.
 *
 * Authentication uses the GITHUB_TOKEN environment variable, which is
 * provided automatically when running inside a GitHub Actions workflow.
 */
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

import { Octokit } from "@octokit/rest";

function octokit() {
  const token = process.env.GITHUB_TOKEN;
  if (!token) {
    throw new Error("GITHUB_TOKEN environment variable is required.");
  }
  return new Octokit({ auth: token });
}

function splitRepo(repo) {
  const [owner, name] = repo.split("/");
  if (!owner || !name) {
    throw new Error(`Invalid repo "${repo}", expected "owner/name".`);
  }
  return { owner, repo: name };
}

/**
 * Stable, search-friendly token derived from a finding_id. Embedded in
 * issue/PR titles so we can detect duplicates without needing to store
 * state across runs. The same finding_id always yields the same token.
 *
 * @param {string} findingId
 * @returns {string} Token of the form `ai-fid-<12 hex chars>`.
 */
export function findingIdToken(findingId) {
  const hash = crypto.createHash("sha256").update(findingId).digest("hex");
  return `ai-fid-${hash.slice(0, 12)}`;
}

/**
 * Search the repo for an existing issue or PR whose title contains the
 * given finding-id token. Returns the first match (any state — open or
 * closed) or null. PRs are issues at the API level, so a single search
 * covers both tracks.
 *
 * @param {string} repo "owner/name"
 * @param {string} token Token from `findingIdToken`.
 * @returns {Promise<{number: number, state: string, html_url: string, pull_request?: object} | null>}
 */
export async function findExistingByToken(repo, token) {
  const { owner, repo: name } = splitRepo(repo);
  const q = `repo:${owner}/${name} in:title "${token}"`;
  const resp = await octokit().search.issuesAndPullRequests({
    q,
    per_page: 5,
  });
  const items = resp.data.items ?? [];
  return items.length > 0 ? items[0] : null;
}

/**
 * Open a GitHub issue. Returns the issue number.
 *
 * @param {string} repo "owner/name"
 * @param {{title: string, body: string, labels?: string[], assignees?: string[]}} args
 * @returns {Promise<number>}
 */
export async function openIssue(repo, { title, body, labels, assignees }) {
  const { owner, repo: name } = splitRepo(repo);
  const resp = await octokit().issues.create({
    owner,
    repo: name,
    title,
    body,
    labels,
    assignees,
  });
  return resp.data.number;
}

/**
 * Open a pull request. Returns the PR number.
 *
 * @param {string} repo "owner/name"
 * @param {{title: string, body: string, head: string, base?: string, draft?: boolean}} args
 * @returns {Promise<number>}
 */
export async function openPr(
  repo,
  { title, body, head, base = "main", draft = false },
) {
  const { owner, repo: name } = splitRepo(repo);
  const resp = await octokit().pulls.create({
    owner,
    repo: name,
    title,
    body,
    head,
    base,
    draft,
  });
  return resp.data.number;
}

/**
 * Add labels to an issue or PR (PRs share the issues endpoint).
 *
 * @param {string} repo "owner/name"
 * @param {number} number Issue or PR number.
 * @param {string[]} labels
 */
export async function addLabels(repo, number, labels) {
  const { owner, repo: name } = splitRepo(repo);
  await octokit().issues.addLabels({
    owner,
    repo: name,
    issue_number: number,
    labels,
  });
}

/**
 * Request user and/or team reviewers on a PR. No-op if both lists are empty.
 *
 * @param {string} repo "owner/name"
 * @param {number} prNumber
 * @param {{reviewers?: string[], teamReviewers?: string[]}} [args]
 */
export async function requestReviewers(
  repo,
  prNumber,
  { reviewers, teamReviewers } = {},
) {
  if (!reviewers?.length && !teamReviewers?.length) return;
  const { owner, repo: name } = splitRepo(repo);
  await octokit().pulls.requestReviewers({
    owner,
    repo: name,
    pull_number: prNumber,
    reviewers,
    team_reviewers: teamReviewers,
  });
}

const CODEOWNERS_LOCATIONS = [
  "CODEOWNERS",
  ".github/CODEOWNERS",
  "docs/CODEOWNERS",
];

function loadCodeowners(repoRoot) {
  for (const rel of CODEOWNERS_LOCATIONS) {
    const p = path.join(repoRoot, rel);
    if (fs.existsSync(p)) {
      const entries = [];
      const lines = fs.readFileSync(p, "utf8").split("\n");
      for (const raw of lines) {
        const line = raw.trim();
        if (!line || line.startsWith("#")) continue;
        const [pattern, ...owners] = line.split(/\s+/);
        if (owners.length) entries.push([pattern, owners]);
      }
      return entries;
    }
  }
  return [];
}

function patternToRegex(pattern) {
  let p = pattern;
  const anchored = p.startsWith("/");
  if (anchored) p = p.replace(/^\/+/, "");
  // Escape regex specials including '*' and '?', then expand the escaped
  // glob forms. Escaping '*' first prevents the second replacement from
  // touching the '*' produced by '**' -> '.*'.
  let body = p.replace(/[.+^${}()|[\]\\*?]/g, "\\$&");
  body = body.replace(/\\\*\\\*/g, ".*").replace(/\\\*/g, "[^/]*");
  if (p.endsWith("/")) body += ".*";
  if (anchored) body = "^" + body;
  return new RegExp(body);
}

/**
 * Return CODEOWNERS for a file path. Last matching rule wins, mirroring
 * GitHub's CODEOWNERS evaluation semantics.
 *
 * @param {string} filePath
 * @param {string} [repoRoot=process.cwd()]
 * @returns {string[]}
 */
export function lookupCodeowners(filePath, repoRoot = process.cwd()) {
  let matched = [];
  for (const [pattern, owners] of loadCodeowners(repoRoot)) {
    if (patternToRegex(pattern).test(filePath)) {
      matched = owners;
    }
  }
  return matched;
}
