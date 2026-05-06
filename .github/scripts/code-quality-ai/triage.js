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
 * The hackday MVP uses a static rule-ID allowlist. When an
 * `ANTHROPIC_API_KEY` is available and `ENABLE_AI_TRIAGE=true`, the
 * dispatch in `classify()` will route through Claude with the
 * classification rubric prompt instead.
 */
import fs from "node:fs";

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

/**
 * Placeholder for the Claude-driven classifier. When wired up, this
 * will call the Anthropic API with the rubric prompt and a structured
 * JSON response.
 *
 * @returns {Promise<Classification>}
 */
// eslint-disable-next-line no-unused-vars
async function classifyWithClaude(finding, sourceContext) {
  throw new Error(
    "classifyWithClaude not implemented yet. Add @anthropic-ai/sdk and " +
      "encode the rubric prompt before enabling ENABLE_AI_TRIAGE.",
  );
}

/**
 * Pick a classifier based on environment configuration.
 * @param {Finding} finding
 * @returns {Promise<Classification>}
 */
async function classify(finding) {
  if (
    process.env.ANTHROPIC_API_KEY &&
    process.env.ENABLE_AI_TRIAGE === "true"
  ) {
    // Future: load source context around finding.{file,line} for the prompt.
    return classifyWithClaude(finding, null);
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
            "Env:",
            "  ANTHROPIC_API_KEY + ENABLE_AI_TRIAGE=true",
            "    Routes through the Claude classifier (not implemented yet).",
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
