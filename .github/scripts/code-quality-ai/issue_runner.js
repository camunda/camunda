#!/usr/bin/env node
/**
 * Issue-track runner for the code quality AI pipeline.
 *
 * Reads triaged.json (`issue_only` array) and opens a GitHub issue per
 * finding using a fixed template. The triage stage's `reason` field
 * (the AI's "why this is not auto-fixable" analysis) is included
 * verbatim — we do not pay for a second Claude call here. A future
 * iteration can swap the template for an AI-generated body once the
 * hackday demo proves the pipeline.
 *
 * Volume cap: at most `--max-issues N` (default 10) per invocation.
 *
 * Required env:
 *   GITHUB_TOKEN  — must have issues:write
 */
import fs from "node:fs";

import {
  addLabels,
  findExistingByToken,
  findingIdToken,
  lookupCodeowners,
  openIssue,
} from "./github_helpers.js";

const RULE_DOC_BASE = "https://codeql.github.com/codeql-query-help/java/";

function ruleDocUrl(rule) {
  return `${RULE_DOC_BASE}/${rule}/`;
}

function severityLabel(finding) {
  // SARIF level on the finding could be "error" / "warning" / "note".
  // Triage doesn't preserve it today, so default to a generic label.
  return "ai-triage:auto-detected";
}

function buildIssueTitle(finding, token) {
  const summary = (finding.message || finding.rule || "").split("\n")[0];
  const trimmed = summary.length > 80 ? summary.slice(0, 77) + "..." : summary;
  return `[ai-triage] ${finding.rule}: ${trimmed} (${token})`;
}

function buildIssueBody(finding) {
  const docUrl = ruleDocUrl(finding.rule);
  const parts = [
    "### <!-- Component -->",
    "",
    "_To be filled in by the triaging engineer._",
    "",
    "### Description",
    "",
    `Auto-detected by the [Code Quality AI Pipeline](../docs/code-quality-ai-pipeline-plan.md) via CodeQL rule \`${finding.rule}\`${docUrl ? ` ([docs](${docUrl}))` : ""}.`,
    "",
    "### CodeQL Scan Detail",
    "",
    `- **Rule:** \`${finding.rule}\`${docUrl ? ` ([docs](${docUrl}))` : ""}`,
    `- **File:** \`${finding.file}\``,
    `- **Line:** ${finding.line}`,
    `- **Finding ID:** \`${finding.finding_id}\``,
    `- **Confidence (triage):** ${finding.confidence}`,
    "",
    "**Message:**",
    "",
    "> " + (finding.message || "(no message)").replace(/\n/g, "\n> "),
    "",
    "**Triage decision:**",
    "",
    finding.reason || "(no reason supplied)",
  ];

  if (finding.triage_prompt) {
    parts.push(
      "",
      "<details>",
      "<summary>Original triage prompt sent to Claude</summary>",
      "<p>",
      finding.triage_prompt,
      "</p>",
      "</details>",
    );
  }

  if (finding.triage_response) {
    parts.push(
      "",
      "<details>",
      "<summary>Claude's triage response</summary>",
      "",
      "```json",
      finding.triage_response,
      "```",
      "",
      "</details>",
    );
  }

  parts.push(
    "",
    "### Next Steps",
    "",
    "1. Review the rule docs (linked above) to understand the failure mode.",
    "2. Decide whether this is a true positive in this context.",
    "3. If yes, apply the fix manually or add this rule to the PR-eligible allowlist once the fix shape is well-understood.",
    "4. If no, suppress at the call site with a justifying comment.",
  );

  return parts.join("\n");
}

function ownersToAssignees(owners) {
  // GitHub `assignees` only takes user logins, not team handles.
  const users = [];
  for (const owner of owners) {
    if (!owner.startsWith("@")) continue;
    const stripped = owner.slice(1);
    if (!stripped.includes("/")) users.push(stripped);
  }
  return users;
}

// Bot login that triggers the GitHub Copilot Coding Agent when added as
// an assignee. Requires Copilot Coding Agent to be enabled for the repo;
// without it, GitHub silently drops the assignment.
const PR_ELIGIBLE_AGENT = "copilot-swe-agent[bot]";

function buildIssuePayload(finding, repoRoot) {
  const token = findingIdToken(finding.finding_id);
  const assignees = ownersToAssignees(lookupCodeowners(finding.file, repoRoot));
  if (finding.track === "pr_eligible") assignees.push(PR_ELIGIBLE_AGENT);
  return {
    finding_id: finding.finding_id,
    token,
    title: buildIssueTitle(finding, token),
    body: buildIssueBody(finding),
    labels: [severityLabel(finding)],
    assignees,
  };
}

async function processFinding(repo, finding, repoRoot, dryRun) {
  const payload = buildIssuePayload(finding, repoRoot);

  if (dryRun) {
    return { status: "dry_run", preview: payload };
  }

  // Skip if an issue/PR with this finding's token already exists in any
  // state (open or closed) — someone has already addressed it or chose
  // not to.
  const existing = await findExistingByToken(repo, payload.token);
  if (existing) {
    return {
      status: "skipped",
      reason: `existing #${existing.number} (${existing.state}): ${existing.html_url}`,
    };
  }

  const issueNumber = await openIssue(repo, {
    title: payload.title,
    body: payload.body,
    labels: payload.labels,
    assignees: payload.assignees,
  });
  // openIssue applies labels via the create payload; double-applying is a
  // no-op but lets us add additional labels later if needed.
  await addLabels(repo, issueNumber, payload.labels);
  return { status: "opened", issue: issueNumber };
}

function parseArgs(argv) {
  const args = {
    triaged: "triaged.json",
    output: "-",
    maxIssues: 10,
    dryRun: false,
    repo: process.env.GITHUB_REPOSITORY ?? "",
    repoRoot: process.cwd(),
  };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    switch (a) {
      case "--triaged":
        args.triaged = argv[++i];
        break;
      case "--output":
        args.output = argv[++i];
        break;
      case "--max-issues":
        args.maxIssues = Number.parseInt(argv[++i]);
        break;
      case "--dry-run":
        args.dryRun = argv[++i] !== "false";
        break;
      case "--repo":
        args.repo = argv[++i];
        break;
      case "--repo-root":
        args.repoRoot = argv[++i];
        break;
      case "-h":
      case "--help":
        console.log(
          [
            "Usage: issue_runner.js [options]",
            "  --triaged PATH      triaged.json input (default: triaged.json)",
            "  --output PATH       summary JSON (default: stdout). Use - for stdout.",
            "  --max-issues N      cap auto-issues per run (default: 10)",
            "  --dry-run           don't open issues; print what would be created",
            "  --repo OWNER/NAME   (default: $GITHUB_REPOSITORY)",
            "  --repo-root PATH    (default: cwd)",
            "",
            "Per-finding progress is written to stderr; only the final summary",
            "JSON is written to stdout (or --output).",
            "",
            "Required env:",
            "  GITHUB_TOKEN  (issues:write; not needed for --dry-run)",
          ].join("\n"),
        );
        process.exit(0);
        break;
      default:
        console.error(`Unknown argument: ${a}`);
        process.exit(2);
    }
  }
  if (!args.repo) {
    throw new Error("--repo or $GITHUB_REPOSITORY is required.");
  }
  return args;
}

function logDryRunPreview(preview) {
  const sep = "=".repeat(72);
  console.error(`\n${sep}\nDRY RUN — would open issue for ${preview.finding_id}\n${sep}`);
  console.error(`title:     ${preview.title}`);
  console.error(`labels:    ${preview.labels.join(", ")}`);
  console.error(`assignees: ${preview.assignees.join(", ") || "(none)"}`);
  console.error(`---- body ----\n${preview.body}`);
  console.error(sep);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const triaged = JSON.parse(fs.readFileSync(args.triaged, "utf8"));
  const candidates = [
    ...(triaged.pr_eligible ?? []),
    ...(triaged.issue_only ?? []),
  ].slice(0, args.maxIssues);

  const summary = args.dryRun
    ? { dry_run: true, would_open: [], skipped: [] }
    : { dry_run: false, opened: [], skipped: [] };

  for (const finding of candidates) {
    try {
      const result = await processFinding(
        args.repo,
        finding,
        args.repoRoot,
        args.dryRun,
      );
      if (result.status === "dry_run") {
        summary.would_open.push(result.preview);
        logDryRunPreview(result.preview);
      } else {
        summary.opened.push({
          finding_id: finding.finding_id,
          issue: result.issue,
        });
        console.error(
          `OPENED issue #${result.issue} for ${finding.finding_id}`,
        );
      }
    } catch (err) {
      summary.skipped.push({
        finding_id: finding.finding_id,
        reason: err.message,
      });
      console.error(`SKIPPED ${finding.finding_id}: ${err.message}`);
    }
  }

  const out = JSON.stringify(summary, null, 2) + "\n";
  if (!args.output || args.output === "-") {
    process.stdout.write(out);
  } else {
    fs.writeFileSync(args.output, out);
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error(err.message);
    process.exit(1);
  });
}
