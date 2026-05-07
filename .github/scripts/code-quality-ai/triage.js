#!/usr/bin/env node
/**
 * AI triage for the code quality pipeline.
 *
 * Reads a CodeQL SARIF file and classifies each finding into one of
 * two tracks:
 *
 *   pr_eligible — simple, local fixes safe for an auto-PR
 *   issue_only  — architectural, concurrency, security-sensitive, or
 *                 otherwise context-dependent; needs human triage
 *
 * The default classifier is a static rule-ID allowlist. When
 * `ENABLE_AI_TRIAGE=true`, the dispatch in `classify()` routes findings
 * through Claude (via AWS Bedrock) using the rubric prompt. The Bedrock
 * client reads AWS credentials from the standard env vars
 * (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION`) and the
 * model from `BEDROCK_INFERENCE_PROFILE_ARN`.
 */
import fs from "node:fs";

import { AnthropicBedrock } from "@anthropic-ai/bedrock-sdk";

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

Each finding is routed to one of two tracks:

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

When in doubt, choose issue_only.

Respond with a single JSON object and no other text:
{"track": "pr_eligible" | "issue_only", "confidence": <0..1>, "reason": "<brief justification>"}`;

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
  const match = rawText.match(/{[\s\S]*}/);
  if (!match) {
    throw new Error(`Claude returned non-JSON classification: ${rawText}`);
  }
  let parsed;
  try {
    parsed = JSON.parse(match[0]);
  } catch {
    throw new Error(`Claude returned non-JSON classification: ${rawText}`);
  }
  if (parsed.track !== "pr_eligible" && parsed.track !== "issue_only") {
    throw new Error(`Claude returned invalid track: ${parsed.track}`);
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
 * @returns {Promise<Classification>}
 */
async function classifyWithClaude(finding) {
  const model = process.env.BEDROCK_INFERENCE_PROFILE_ARN;
  if (!model) {
    throw new Error(
      "BEDROCK_INFERENCE_PROFILE_ARN env var is required for Bedrock dispatch.",
    );
  }
  const userPrompt = [
    `Rule: ${finding.rule}`,
    `File: ${finding.file}`,
    `Line: ${finding.line}`,
    `Message: ${finding.message}`,
    "",
    "Classify this finding.",
  ].join("\n");

  const response = await getBedrockClient().messages.create({
    model,
    max_tokens: 512,
    system: CLASSIFICATION_SYSTEM_PROMPT,
    messages: [{ role: "user", content: userPrompt }],
  });
  return parseClassification(extractText(response));
}

/**
 * Pick a classifier based on environment configuration.
 * @param {Finding} finding
 * @returns {Promise<Classification>}
 */
async function classify(finding) {
  if (process.env.ENABLE_AI_TRIAGE === "true") {
    return classifyWithClaude(finding);
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
 * Triage a parsed SARIF object into pr_eligible / issue_only lists.
 */
export async function triage(sarif) {
  const findings = [];
  for (const run of sarif.runs ?? []) {
    for (const result of run.results ?? []) {
      const f = toFinding(result);
      if (f) findings.push(f);
    }
  }

  const pr_eligible = [];
  const issue_only = [];
  for (const finding of findings) {
    const c = await classify(finding);
    const entry = { ...finding, confidence: c.confidence };
    if (c.track === "pr_eligible") {
      pr_eligible.push(entry);
    } else {
      issue_only.push({
        ...entry,
        reason: c.reason ?? ISSUE_REASON_DEFAULT,
      });
    }
  }
  return { pr_eligible, issue_only };
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
  const args = { input: "-", output: "-" };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    switch (a) {
      case "--input":
        args.input = argv[++i];
        break;
      case "--output":
        args.output = argv[++i];
        break;
      case "-h":
      case "--help":
        console.log(
          [
            "Usage: triage.js [options]",
            "  --input PATH    SARIF file (default: stdin)",
            "  --output PATH   classified JSON (default: stdout)",
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
  const out = await triage(sarif);
  writeJson(args.output, out);
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((err) => {
    console.error(err.message);
    process.exit(1);
  });
}
