#!/usr/bin/env node
/**
 * AI triage for the code quality pipeline.
 *
 * Reads a CodeQL SARIF file and classifies each finding into one of
 * three tracks:
 *
 *   pr_eligible    — simple, local fixes safe for an auto-PR
 *   issue_only     — architectural, concurrency, security-sensitive, or
 *                    otherwise context-dependent; needs human triage
 *   false_positive — rule fired but code context shows it does not apply;
 *                    dropped from output (logged to false_positives array)
 *
 * The default classifier is a static rule-ID allowlist. When
 * `ENABLE_AI_TRIAGE=true`, the dispatch in `classify()` routes findings
 * through Claude (via AWS Bedrock) using the rubric prompt. The Bedrock
 * client reads AWS credentials from the standard env vars
 * (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`) and the
 * model from `BEDROCK_INFERENCE_PROFILE_ARN`.
 */
import fs from "node:fs";
import path from "node:path";

import { AnthropicBedrock } from "@anthropic-ai/bedrock-sdk";

const CONTEXT_LINES = 20;

/**
 * Read N lines of code around a finding's line number for use in the prompt.
 * Returns null if the file cannot be read (e.g. path not resolvable).
 */
function buildContextSnippet(repoRoot, filePath, line) {
  const fullPath = path.isAbsolute(filePath)
    ? filePath
    : path.join(repoRoot, filePath);
  try {
    const content = fs.readFileSync(fullPath, "utf8");
    const lines = content.split("\n");
    const start = Math.max(0, line - 1 - CONTEXT_LINES);
    const end = Math.min(lines.length, line + CONTEXT_LINES);
    return lines
      .slice(start, end)
      .map((l, i) => `${start + i + 1}\t${l}`)
      .join("\n");
  } catch {
    return null;
  }
}

const RULE_TRACK = {
  // Pure rename to a non-deprecated successor; safe to auto-fix.
  "java/deprecated-call": "pr_eligible",
};

const ISSUE_REASON_DEFAULT =
  "Rule not on the auto-fix allowlist; routed to issue track for human triage.";

const PR_CONFIDENCE_DEFAULT = 0.9;
const ISSUE_CONFIDENCE_DEFAULT = 0.7;

/**
 * @typedef {Object} Finding
 * @property {string} finding_id
 * @property {string} rule
 * @property {string} file
 * @property {number} line
 * @property {string} message
 */

/**
 * @typedef {Object} Classification
 * @property {"pr_eligible" | "issue_only"} track
 * @property {number} confidence
 * @property {string} [reason]
 */

/**
 * Classify a finding using the static rule-ID allowlist.
 * @param {Finding} finding
 * @returns {Classification}
 */
function classifyWithRules(finding) {
  const track = RULE_TRACK[finding.rule] ?? "issue_only";
  if (track === "pr_eligible") {
    return { track, confidence: PR_CONFIDENCE_DEFAULT };
  }
  return {
    track,
    confidence: ISSUE_CONFIDENCE_DEFAULT,
    reason: ISSUE_REASON_DEFAULT,
  };
}

const CLASSIFICATION_SYSTEM_PROMPT = `You are classifying static analysis findings for an automated code-quality pipeline.

You will be given the finding metadata AND the actual code context around the flagged line.
Use the code to verify whether the finding is genuine before classifying.

Each finding is routed to one of three tracks:

False positive — the rule fired but the code context shows it does not apply.
  Requires positive evidence in the code, not just uncertainty. Examples:
  - A deprecated method that is an @Override with no available non-deprecated successor
  - A null-check finding on a value that is provably non-null by construction
  - A taint finding where the value is fully sanitized before the flagged use

PR-eligible — fix is local, low-risk, safe to apply automatically. ALL of:
  - Local change (single function or file, no cross-module impact)
  - Not concurrency-related (no locks, threads, async, volatile, CompletableFuture)
  - Not security-sensitive (no SAST or taint findings)
  - High-confidence rule (e.g., deprecated API rename, unused import, simple null check, missing @Override)

Issue-only — needs human triage. ANY of:
  - Architectural or cross-cutting
  - Concurrency, threading, async correctness
  - SAST / taint analysis
  - Low-confidence or context-dependent
  - Any finding where the fix could plausibly introduce a new bug

When in doubt, prefer issue_only over pr_eligible or false_positive.

Respond with a single JSON object and no other text:
{"track": "pr_eligible" | "issue_only" | "false_positive", "confidence": <0..1>, "reason": "<brief justification>"}`;

let bedrockClient = null;
function getBedrockClient() {
  if (bedrockClient) return bedrockClient;
  const region = process.env.AWS_REGION;
  if (!region) {
    throw new Error("AWS_REGION env var is required for Bedrock dispatch.");
  }
  // Access key, secret, and (optional) session token are read from the
  // standard AWS env vars by the SDK.
  bedrockClient = new AnthropicBedrock({ awsRegion: region });
  return bedrockClient;
}

function extractText(response) {
  return response.content
    .map((b) => (b.type === "text" ? b.text : ""))
    .join("")
    .trim();
}

function parseClassification(rawText) {
  // Claude sometimes wraps the JSON in prose or markdown; extract the first
  // {...} object anywhere in the response.
  const match = rawText.match(/\{"track"[\s\S]*}/);
  const errorResponse = {
    "track": "failed",
    "confidence": 0,
    "reason": rawText,
  }

  if (!match) {
    console.error(`Claude returned non-JSON classification: ${rawText}`);
    return errorResponse;
  }
  let parsed;
  try {
    parsed = JSON.parse(match[0]);
  } catch {
    console.error(`Claude returned non-JSON classification: ${rawText}`);
    return errorResponse;
  }
  if (
    parsed.track !== "pr_eligible" &&
    parsed.track !== "issue_only" &&
    parsed.track !== "false_positive"
  ) {
    console.error(`Claude returned invalid track: ${parsed.track}`);
    return errorResponse;
  }
  const confidence =
    typeof parsed.confidence === "number" ? parsed.confidence : 0.5;
  return {
    track: parsed.track,
    confidence: Math.max(0, Math.min(1, confidence)),
    reason: typeof parsed.reason === "string" ? parsed.reason : undefined,
  };
}

/**
 * Classify a finding via Claude on AWS Bedrock.
 * @param {Finding} finding
 * @param {string} repoRoot
 * @returns {Promise<Classification>}
 */
async function classifyWithClaude(finding, repoRoot) {
  const model = process.env.BEDROCK_INFERENCE_PROFILE_ARN;
  if (!model) {
    throw new Error(
      "BEDROCK_INFERENCE_PROFILE_ARN env var is required for Bedrock dispatch.",
    );
  }
  const snippet = buildContextSnippet(repoRoot, finding.file, finding.line);
  const userPrompt = [
    `Rule: ${finding.rule}`,
    `File: ${finding.file}`,
    `Line: ${finding.line}`,
    `Message: ${finding.message}`,
    "",
    snippet
      ? `Code context (line numbers prefixed):\n\`\`\`java\n${snippet}\n\`\`\``
      : "(code context unavailable — classify based on rule and message only)",
    "",
    "Classify this finding.",
  ].join("\n");

  console.log(`Prompt for ${finding.finding_id}:\n${userPrompt}\n`);
  const response = await getBedrockClient().messages.create({
    model,
    max_tokens: 512,
    system: CLASSIFICATION_SYSTEM_PROMPT,
    messages: [{ role: "user", content: userPrompt }],
  });
  const rawText = extractText(response);
  console.log(`Response for ${finding.finding_id}:\n${rawText}\n`);
  const classification = parseClassification(rawText);
  return { ...classification, triage_prompt: userPrompt, triage_response: rawText };
}

/**
 * Pick a classifier based on environment configuration.
 * @param {Finding} finding
 * @param {string} repoRoot
 * @returns {Promise<Classification>}
 */
async function classify(finding, repoRoot) {
  if (process.env.ENABLE_AI_TRIAGE === "true") {
    return classifyWithClaude(finding, repoRoot);
  }
  return classifyWithRules(finding);
}

/**
 * Flatten a SARIF result into a Finding. Returns null if the result
 * lacks a usable physical location.
 */
function toFinding(result) {
  const loc = result.locations?.[0]?.physicalLocation;
  const file = loc?.artifactLocation?.uri;
  if (!file) return null;
  const line = loc.region?.startLine ?? 0;
  const rule = result.ruleId ?? "unknown";
  const message = result.message?.text ?? "";
  return {
    finding_id: `${rule}:${file}:${line}`,
    rule,
    file,
    line,
    message,
  };
}

/**
 * Triage a parsed SARIF object into pr_eligible / issue_only / false_positives.
 * At most `maxTriage` results are processed to keep runtime bounded.
 * @param {object} sarif
 * @param {number} maxTriage
 * @param {string} [repoRoot=process.cwd()]
 */
export async function triage(sarif, maxTriage, repoRoot = process.cwd()) {
  const findings = (sarif.runs ?? [])
    .flatMap((run) => run.results ?? [])
    .map(toFinding)
    .filter(Boolean)
    .slice(0, maxTriage);

  const classified = [];
  for (const finding of findings) {
    const c = await classify(finding, repoRoot);
    classified.push({
    ...finding,
    track: c.track,
    confidence: c.confidence,
    reason: c.reason,
    triage_prompt: c.triage_prompt,
    triage_response: c.triage_response,
  });
  }

  const pr_eligible = classified
    .filter((f) => f.track === "pr_eligible")
    .map(({ track, ...f }) => ({ ...f, reason: f.reason ?? ISSUE_REASON_DEFAULT }));

  const issue_only = classified
    .filter((f) => f.track === "issue_only")
    .map(({ track, ...f }) => ({ ...f, reason: f.reason ?? ISSUE_REASON_DEFAULT }));

  const false_positives = classified
    .filter((f) => f.track === "false_positive")
    .map(({ track, ...f }) => f);

  return { pr_eligible, issue_only, false_positives };
}

function readSarif(inputPath) {
  const raw =
    !inputPath || inputPath === "-"
      ? fs.readFileSync(0, "utf8")
      : fs.readFileSync(inputPath, "utf8");
  return JSON.parse(raw);
}

function writeJson(outputPath, payload) {
  const json = JSON.stringify(payload, null, 2) + "\n";
  if (!outputPath || outputPath === "-") {
    process.stdout.write(json);
  } else {
    fs.writeFileSync(outputPath, json);
  }
}

function parseArgs(argv) {
  const args = { input: "-", output: "-", maxTriage: 20, repoRoot: process.cwd() };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    switch (a) {
      case "--input":
        args.input = argv[++i];
        break;
      case "--output":
        args.output = argv[++i];
        break;
      case "--max-triage":
        args.maxTriage = Number.parseInt(argv[++i]);
        break;
      case "--repo-root":
        args.repoRoot = argv[++i];
        break;
      case "-h":
      case "--help":
        console.log(
          [
            "Usage: triage.js [options]",
            "  --input PATH       SARIF file (default: stdin)",
            "  --output PATH      classified JSON (default: stdout)",
            "  --max-triage N     max findings to triage (default: 20)",
            "  --repo-root PATH   repository root for reading source files (default: cwd)",
            "",
            "Env (Claude/Bedrock dispatch — set ENABLE_AI_TRIAGE=true):",
            "  AWS_REGION                       (required)",
            "  AWS_ACCESS_KEY_ID                (required)",
            "  AWS_SECRET_ACCESS_KEY            (required)",
            "  AWS_SESSION_TOKEN                (optional, STS)",
            "  BEDROCK_INFERENCE_PROFILE_ARN    (required, used as model)",
          ].join("\n"),
        );
        process.exit(0);
        break;
      default:
        console.error(`Unknown argument: ${a}`);
        process.exit(2);
    }
  }
  return args;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const sarif = readSarif(args.input);
  const out = await triage(sarif, args.maxTriage, args.repoRoot);
  writeJson(args.output, out);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  await main();
}
