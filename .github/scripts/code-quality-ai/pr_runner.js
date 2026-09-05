#!/usr/bin/env node
/**
 * PR-track runner for the code quality AI pipeline.
 *
 * Reads triaged.json (`pr_eligible` array), generates a fix for each
 * finding via Claude on AWS Bedrock, applies it as a string replacement,
 * compiles the affected Maven module, formats via spotless, commits to a
 * fresh branch, pushes, and opens a PR with the `ai-fix:auto-pr` label
 * and a CODEOWNERS-derived reviewer.
 *
 * Volume cap: at most `--max-prs N` (default 5) per invocation. Finding
 * IDs that fail any gate (fix generation, compile, format) are skipped
 * and reported in the summary.
 *
 * Required env (Bedrock):
 *   AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
 *   BEDROCK_INFERENCE_PROFILE_ARN
 *
 * Required env (GitHub):
 *   GITHUB_TOKEN  — must have contents:write + pull-requests:write
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

import { AnthropicBedrock } from "@anthropic-ai/bedrock-sdk";

import {
  addLabels,
  findExistingByToken,
  findingIdToken,
  lookupCodeowners,
  openPr,
  requestReviewers,
} from "./github_helpers.js";

const FIX_SYSTEM_PROMPT = `You are fixing a Java code-quality finding for an automated PR pipeline. Your output is applied via string replacement and must be precise.

Respond with a single JSON object and no other text:

If a confident, minimal, local fix is possible:
{
  "old_string": "<exact text to replace; include enough surrounding context that it appears EXACTLY ONCE in the file>",
  "new_string": "<the replacement>",
  "summary": "<one-line conventional-commit summary, e.g. 'refactor: replace deprecated Foo.bar() with Foo.baz()'>"
}

If you cannot confidently fix the finding (ambiguous, requires architectural change, missing context, etc.):
{ "abort": true, "reason": "<short reason>" }

Rules:
- old_string must appear EXACTLY ONCE in the file.
- The change must be minimal — fix only the finding, nothing else.
- Preserve indentation, whitespace, and surrounding code style.
- For java/deprecated-call: replace with the non-deprecated successor named in the @deprecated annotation or javadoc.`;

const CONTEXT_LINES_BEFORE = 30;
const CONTEXT_LINES_AFTER = 30;
const FIX_MAX_TOKENS = 2048;

let bedrockClient = null;
function getBedrockClient() {
  if (bedrockClient) return bedrockClient;
  const region = process.env.AWS_REGION;
  if (!region) throw new Error("AWS_REGION env var is required.");
  bedrockClient = new AnthropicBedrock({ awsRegion: region });
  return bedrockClient;
}

function git(args, options = {}) {
  const result = spawnSync("git", args, {
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
    ...options,
  });
  if (result.status !== 0) {
    throw new Error(`git ${args.join(" ")} failed: ${result.stderr.trim()}`);
  }
  return result.stdout.trim();
}

function mvn(args, cwd) {
  const result = spawnSync("./mvnw", args, {
    cwd,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "pipe"],
  });
  return result;
}

function buildContextSnippet(filePath, line) {
  const content = fs.readFileSync(filePath, "utf8");
  const lines = content.split("\n");
  const start = Math.max(0, line - 1 - CONTEXT_LINES_BEFORE);
  const end = Math.min(lines.length, line + CONTEXT_LINES_AFTER);
  const numbered = lines
    .slice(start, end)
    .map((l, i) => `${start + i + 1}\t${l}`)
    .join("\n");
  return { snippet: numbered, totalLines: lines.length };
}

function extractText(response) {
  return response.content
    .map((b) => (b.type === "text" ? b.text : ""))
    .join("")
    .trim();
}

function parseFixResponse(rawText) {
  const cleaned = rawText
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```\s*$/i, "")
    .trim();
  try {
    return JSON.parse(cleaned);
  } catch {
    throw new Error(`Claude returned non-JSON fix: ${rawText}`);
  }
}

async function generateFix(finding, repoRoot) {
  const model = process.env.BEDROCK_INFERENCE_PROFILE_ARN;
  if (!model) {
    throw new Error("BEDROCK_INFERENCE_PROFILE_ARN env var is required.");
  }
  const filePath = path.join(repoRoot, finding.file);
  const { snippet, totalLines } = buildContextSnippet(filePath, finding.line);

  const userPrompt = [
    `Rule: ${finding.rule}`,
    `File: ${finding.file} (${totalLines} lines total)`,
    `Finding line: ${finding.line}`,
    `Message: ${finding.message}`,
    "",
    "Code context (line numbers prefixed):",
    "```java",
    snippet,
    "```",
    "",
    "Produce the JSON fix object.",
  ].join("\n");

  const response = await getBedrockClient().messages.create({
    model,
    max_tokens: FIX_MAX_TOKENS,
    system: FIX_SYSTEM_PROMPT,
    messages: [{ role: "user", content: userPrompt }],
  });
  return parseFixResponse(extractText(response));
}

function previewFix(filePath, oldString, newString) {
  const content = fs.readFileSync(filePath, "utf8");
  const idx = content.indexOf(oldString);
  if (idx === -1) {
    throw new Error("old_string not found in target file");
  }
  if (content.indexOf(oldString, idx + 1) !== -1) {
    throw new Error("old_string is ambiguous (multiple occurrences)");
  }
  return { original: content, updated: content.replace(oldString, newString) };
}

function applyFix(filePath, oldString, newString) {
  const { updated } = previewFix(filePath, oldString, newString);
  fs.writeFileSync(filePath, updated);
}

function moduleForFile(filePath) {
  // Walk up until we find a pom.xml; the directory containing it is the module.
  let dir = path.dirname(filePath);
  while (dir && dir !== "/" && dir !== ".") {
    if (fs.existsSync(path.join(dir, "pom.xml"))) {
      return dir;
    }
    dir = path.dirname(dir);
  }
  throw new Error(`No pom.xml found above ${filePath}`);
}

function compileModule(repoRoot, moduleDir) {
  const relative = path.relative(repoRoot, moduleDir) || ".";
  const result = mvn(
    ["install", "-pl", relative, "-am", "-Dquickly", "-T1C"],
    repoRoot,
  );
  if (result.status !== 0) {
    return { ok: false, error: result.stderr || result.stdout };
  }
  return { ok: true };
}

function formatSources(repoRoot) {
  const result = mvn(
    ["license:format", "spotless:apply", "-T1C"],
    repoRoot,
  );
  if (result.status !== 0) {
    return { ok: false, error: result.stderr || result.stdout };
  }
  return { ok: true };
}

function safeBranchSegment(s) {
  return s.replace(/[^A-Za-z0-9._-]+/g, "-").replace(/^-+|-+$/g, "");
}

function buildBranchName(finding, runId) {
  const slug = safeBranchSegment(finding.finding_id).slice(0, 80);
  return `ai-fix/${slug}-${runId}`;
}

function buildPrBody(finding, fix) {
  return [
    "## AI-generated fix",
    "",
    "**Mandatory human review required before merge.**",
    "",
    `- **Rule:** \`${finding.rule}\``,
    `- **File:** \`${finding.file}\``,
    `- **Line:** ${finding.line}`,
    `- **Confidence (triage):** ${finding.confidence}`,
    `- **Finding ID:** \`${finding.finding_id}\``,
    "",
    `### Finding message`,
    "",
    "> " + (finding.message || "(no message)").replace(/\n/g, "\n> "),
    "",
    `### Fix summary`,
    "",
    fix.summary || "(no summary)",
    "",
    "### Pipeline",
    "",
    "Generated by the [Code Quality AI Pipeline](../docs/code-quality-ai-pipeline-plan.md).",
    "Classified as `pr_eligible` by the rule-ID classifier or Claude (Bedrock).",
  ].join("\n");
}

function resolveReviewers(filePath, repoRoot) {
  const owners = lookupCodeowners(filePath, repoRoot);
  const teamReviewers = [];
  const userReviewers = [];
  for (const owner of owners) {
    if (!owner.startsWith("@")) continue;
    const stripped = owner.slice(1);
    if (stripped.includes("/")) {
      teamReviewers.push(stripped.split("/")[1]);
    } else {
      userReviewers.push(stripped);
    }
  }
  return { userReviewers, teamReviewers };
}

async function processFinding(finding, opts) {
  const { repoRoot, repo, runId, dryRun } = opts;
  const filePath = path.join(repoRoot, finding.file);
  if (!fs.existsSync(filePath)) {
    return { status: "skipped", reason: "file does not exist" };
  }

  const token = findingIdToken(finding.finding_id);

  // Dedupe: in real-run mode, skip if an issue/PR with this finding's
  // token already exists in any state. Done before the Bedrock call so
  // duplicates don't burn AI tokens. Dry-run skips this check (no
  // GITHUB_TOKEN required) and always shows the proposed content.
  if (!dryRun) {
    const existing = await findExistingByToken(repo, token);
    if (existing) {
      return {
        status: "skipped",
        reason: `existing #${existing.number} (${existing.state}): ${existing.html_url}`,
      };
    }
  }

  let fix;
  try {
    fix = await generateFix(finding, repoRoot);
  } catch (err) {
    return { status: "skipped", reason: `fix generation failed: ${err.message}` };
  }
  if (fix.abort) {
    return { status: "skipped", reason: `AI aborted: ${fix.reason ?? "unspecified"}` };
  }
  if (typeof fix.old_string !== "string" || typeof fix.new_string !== "string") {
    return { status: "skipped", reason: "AI response missing old_string/new_string" };
  }

  const prTitle = `${fix.summary} [${token}]`;

  // Dry-run: validate the apply step (old_string unique) but don't touch
  // the working tree, run Maven, push, or hit GitHub. Print everything
  // a reviewer would need to evaluate the proposed PR.
  if (dryRun) {
    try {
      previewFix(filePath, fix.old_string, fix.new_string);
    } catch (err) {
      return { status: "skipped", reason: `apply check failed: ${err.message}` };
    }
    const branch = buildBranchName(finding, runId);
    const { userReviewers, teamReviewers } = resolveReviewers(
      finding.file,
      repoRoot,
    );
    return {
      status: "dry_run",
      preview: {
        finding_id: finding.finding_id,
        token,
        title: prTitle,
        branch,
        labels: ["ai-fix:auto-pr"],
        reviewers: { users: userReviewers, teams: teamReviewers },
        body: buildPrBody(finding, fix),
        fix: {
          summary: fix.summary,
          old_string: fix.old_string,
          new_string: fix.new_string,
        },
      },
    };
  }

  try {
    applyFix(filePath, fix.old_string, fix.new_string);
  } catch (err) {
    return { status: "skipped", reason: `apply failed: ${err.message}` };
  }

  const moduleDir = moduleForFile(filePath);
  const compile = compileModule(repoRoot, moduleDir);
  if (!compile.ok) {
    git(["checkout", "--", finding.file], { cwd: repoRoot });
    return { status: "skipped", reason: "compile failed after fix" };
  }
  const fmt = formatSources(repoRoot);
  if (!fmt.ok) {
    git(["checkout", "--", "."], { cwd: repoRoot });
    return { status: "skipped", reason: "spotless/license format failed" };
  }

  const branch = buildBranchName(finding, runId);
  git(["checkout", "-b", branch], { cwd: repoRoot });
  git(["add", "-A"], { cwd: repoRoot });
  const commitMessage = `${fix.summary}\n\nFinding: ${finding.finding_id}\nRule: ${finding.rule}\n`;
  git(["commit", "-m", commitMessage], { cwd: repoRoot });
  git(["push", "-u", "origin", branch], { cwd: repoRoot });
  git(["checkout", "-"], { cwd: repoRoot });

  const { userReviewers, teamReviewers } = resolveReviewers(
    finding.file,
    repoRoot,
  );

  const prNumber = await openPr(repo, {
    title: prTitle,
    body: buildPrBody(finding, fix),
    head: branch,
    base: "main",
    draft: false,
  });
  await addLabels(repo, prNumber, ["ai-fix:auto-pr"]);
  await requestReviewers(repo, prNumber, {
    reviewers: userReviewers,
    teamReviewers,
  });

  return { status: "opened", pr: prNumber, branch };
}

function parseArgs(argv) {
  const args = {
    triaged: "triaged.json",
    output: "-",
    maxPrs: 5,
    dryRun: false,
    repo: process.env.GITHUB_REPOSITORY ?? "",
    repoRoot: process.cwd(),
    runId: process.env.GITHUB_RUN_ID ?? `local-${Date.now()}`,
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
      case "--max-prs":
        args.maxPrs = parseInt(argv[++i], 10);
        break;
      case "--dry-run":
        args.dryRun = true;
        break;
      case "--repo":
        args.repo = argv[++i];
        break;
      case "--repo-root":
        args.repoRoot = argv[++i];
        break;
      case "--run-id":
        args.runId = argv[++i];
        break;
      case "-h":
      case "--help":
        console.log(
          [
            "Usage: pr_runner.js [options]",
            "  --triaged PATH    triaged.json input (default: triaged.json)",
            "  --output PATH     summary JSON (default: stdout). Use - for stdout.",
            "  --max-prs N       cap auto-PRs per run (default: 5)",
            "  --dry-run         don't push or open a PR; print what would be created",
            "  --repo OWNER/NAME (default: $GITHUB_REPOSITORY)",
            "  --repo-root PATH  (default: cwd)",
            "  --run-id ID       suffix for branch names (default: $GITHUB_RUN_ID)",
            "",
            "Per-finding progress is written to stderr; only the final summary",
            "JSON is written to stdout (or --output).",
            "",
            "Required env:",
            "  AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY",
            "  BEDROCK_INFERENCE_PROFILE_ARN",
            "  GITHUB_TOKEN  (contents:write + pull-requests:write; not needed for --dry-run)",
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
  console.error(`\n${sep}\nDRY RUN — would open PR for ${preview.finding_id}\n${sep}`);
  console.error(`title:     ${preview.title}`);
  console.error(`branch:    ${preview.branch}`);
  console.error(`labels:    ${preview.labels.join(", ")}`);
  console.error(
    `reviewers: users=[${preview.reviewers.users.join(", ")}] teams=[${preview.reviewers.teams.join(", ")}]`,
  );
  console.error(`---- fix.summary ----\n${preview.fix.summary}`);
  console.error(`---- fix.old_string ----\n${preview.fix.old_string}`);
  console.error(`---- fix.new_string ----\n${preview.fix.new_string}`);
  console.error(`---- PR body ----\n${preview.body}`);
  console.error(sep);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const triagedRaw = fs.readFileSync(args.triaged, "utf8");
  const triaged = JSON.parse(triagedRaw);
  const candidates = (triaged.pr_eligible ?? []).slice(0, args.maxPrs);

  const summary = args.dryRun
    ? { dry_run: true, would_open: [], skipped: [] }
    : { dry_run: false, opened: [], skipped: [] };

  for (const finding of candidates) {
    const result = await processFinding(finding, {
      repoRoot: args.repoRoot,
      repo: args.repo,
      runId: args.runId,
      dryRun: args.dryRun,
    });
    if (result.status === "opened") {
      summary.opened.push({
        finding_id: finding.finding_id,
        pr: result.pr,
        branch: result.branch,
      });
      console.error(
        `OPENED PR #${result.pr} (${result.branch}) for ${finding.finding_id}`,
      );
    } else if (result.status === "dry_run") {
      summary.would_open.push(result.preview);
      logDryRunPreview(result.preview);
    } else {
      summary.skipped.push({
        finding_id: finding.finding_id,
        reason: result.reason,
      });
      console.error(`SKIPPED ${finding.finding_id}: ${result.reason}`);
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
