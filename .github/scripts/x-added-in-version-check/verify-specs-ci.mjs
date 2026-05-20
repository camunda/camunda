#!/usr/bin/env node
/**
 * PR-oriented CI wrapper around verify-specs.mjs.
 *
 * Prints the shared structured report (without the detail trailer) so the
 * console surface matches the standalone and workflow entry points, then
 * surfaces each finding as a GitHub Actions `::error::` workflow command
 * anchored to the relevant file and line for inline PR annotations.
 *
 * Exits non-zero when any finding is reported.
 *
 * Optional env:
 *   ANNOTATION_PATH_PREFIX  prefix prepended to each YAML filename when
 *                           emitting `::warning file=...`. Defaults to
 *                           "zeebe/gateway-protocol/src/main/proto/v2"
 *                           (the canonical location inside camunda/camunda).
 *                           Set to empty string to emit bare filenames.
 *   GITHUB_STEP_SUMMARY     when set, a short Markdown summary is appended.
 */

import { appendFileSync } from "node:fs";
import {
  loadInputs,
  printCliReport,
  verifySpecs,
} from "./verify-specs.mjs";

const annotationPrefix =
  process.env.ANNOTATION_PATH_PREFIX ??
  "zeebe/gateway-protocol/src/main/proto/v2";

const inputs = loadInputs();
const result = verifySpecs(inputs);

const { opErrors, extraOps, propErrors } = result;

// ── GitHub Actions warning annotations ──────────────────────────────────────

function annotationPath(file) {
  if (!file) return "";
  return annotationPrefix ? `${annotationPrefix}/${file}` : file;
}

// Escape per GitHub Actions workflow command rules.
function escapeProp(s) {
  return String(s)
    .replace(/%/g, "%25")
    .replace(/\r/g, "%0D")
    .replace(/\n/g, "%0A")
    .replace(/:/g, "%3A")
    .replace(/,/g, "%2C");
}
function escapeMsg(s) {
  return String(s).replace(/%/g, "%25").replace(/\r/g, "%0D").replace(/\n/g, "%0A");
}

function emitError({ file, line, title, message }) {
  const parts = [];
  if (file) parts.push(`file=${escapeProp(annotationPath(file))}`);
  if (typeof line === "number" && line > 0) parts.push(`line=${line}`);
  if (title) parts.push(`title=${escapeProp(title)}`);
  const head = parts.length ? `::error ${parts.join(",")}::` : "::error::";
  console.log(head + escapeMsg(message));
}

function messageForOperation(e) {
  switch (e.issue) {
    case "MISSING_X_ADDED_IN_VERSION":
      return `Operation \`${e.op}\` is missing \`x-added-in-version\`. Expected: ${e.expected}.`;
    case "VERSION_MISMATCH":
      return `Operation \`${e.op}\` has \`x-added-in-version: ${e.actual}\` but version-map says ${e.expected}.`;
    case "NO_ENDPOINT_MAP_ENTRY":
      return `Operation \`${e.op}\` is in version-map but missing from endpoint-map.json. Regenerate the endpoint map.`;
    case "YAML_FILE_NOT_FOUND":
      return `Operation \`${e.op}\` references YAML file \`${e.file}\` which does not exist in the spec dir.`;
    case "PATH_NOT_IN_YAML":
      return `Path \`${e.path}\` is missing from \`${e.file}\` (required for operation \`${e.op}\`).`;
    case "METHOD_NOT_IN_YAML":
      return `Method \`${e.method?.toUpperCase()}\` is missing for path \`${e.path}\` in \`${e.file}\` (required for operation \`${e.op}\`).`;
    case "UNEXPECTED_ANNOTATION_ON_DELETED_OPERATION":
      return `Operation \`${e.op}\` is marked deleted in the version-map but still carries \`x-added-in-version: ${e.actual}\`. Remove the annotation (and likely the operation).`;
    default:
      return `${e.issue}: ${e.op}`;
  }
}

function messageForProperty(e) {
  switch (e.issue) {
    case "MISSING_X_PROPERTIES_ADDED_IN_VERSION":
      return `Property \`${e.propName ?? e.path}\` on \`${e.parentPath ?? "(root)"}\` is missing an \`x-properties-added-in-version\` entry. Expected version: ${e.expected}.`;
    case "VERSION_MISMATCH":
      return `Property \`${e.propName ?? e.path}\` on \`${e.parentPath}\` has \`addedInVersion: ${e.actual}\` but expected ${e.expected}.`;
    case "UNEXPECTED_ANNOTATION_ON_SUPPRESSED":
      return `Property \`${e.propName ?? e.path}\` on \`${e.parentPath}\` carries \`addedInVersion: ${e.actual}\` but should be suppressed by ${e.suppressedBy} (aggregated intro: ${e.intro}). Remove the entry.`;
    case "ORPHAN_PROPERTY_ANNOTATION":
      return `\`x-properties-added-in-version\` on \`${e.parentPath}\` lists \`${e.propName}\`, but no such property exists on this schema. Remove the entry.`;
    case "UNKNOWN_PROPERTY_ANNOTATION":
      return `\`x-properties-added-in-version\` on \`${e.parentPath}\` lists \`${e.propName}\` (addedInVersion: ${e.actual}), but the version-map does not track this location. Either remove the entry or regenerate version-map.json.`;
    default:
      return `${e.issue}: ${e.path}`;
  }
}

const totalErrors = opErrors.length + extraOps.length + propErrors.length;

const affectedFiles = new Set();
for (const e of opErrors) if (e.file) affectedFiles.add(e.file);
for (const e of extraOps) if (e.file) affectedFiles.add(e.file);
for (const e of propErrors) if (e.file) affectedFiles.add(e.file);

// ── Status line (shared core, summary-only) ─────────────────────────────────

printCliReport(result, inputs.versionMap, { summaryOnly: true });
console.log("");

if (totalErrors > 0) {
  console.log("=== Inline PR annotations ===");
  console.log("");

  for (const e of opErrors) {
    emitError({
      file: e.file,
      line: e.line,
      title: `OpenAPI: ${e.issue}`,
      message: messageForOperation(e),
    });
  }
  for (const e of extraOps) {
    emitError({
      file: e.file,
      line: e.line,
      title: "OpenAPI: UNKNOWN_OPERATION_IN_YAML",
      message: `Operation \`${e.op}\` exists in \`${e.file}\` but is not in version-map.json. Add it to the version-map or remove it from the YAML.`,
    });
  }
  for (const e of propErrors) {
    emitError({
      file: e.file,
      line: e.line,
      title: `OpenAPI: ${e.issue}`,
      message: messageForProperty(e),
    });
  }
  console.log("");
}

// ── Fix sections (shared core, stats block suppressed) ──────────────────────

if (totalErrors > 0) {
  printCliReport(result, inputs.versionMap, { skipStats: true });
}

if (process.env.GITHUB_STEP_SUMMARY) {
  const lines = [];
  lines.push("# OpenAPI annotation verification");
  lines.push("");
  if (totalErrors === 0) {
    lines.push("**Status:** ✅ No errors.");
  } else {
    lines.push(
      `**Status:** ❌ ${totalErrors} ${totalErrors === 1 ? "error" : "errors"} across ${affectedFiles.size} ${affectedFiles.size === 1 ? "file" : "files"}.`
    );
    lines.push("");
    lines.push("See the **Files changed** tab for inline annotations.");
    lines.push("");
    if (opErrors.length || extraOps.length) {
      lines.push("## Operation errors");
      lines.push("");
      for (const e of opErrors) {
        lines.push(`- \`${e.issue}\` — \`${e.op}\`${e.file ? ` (\`${e.file}\`${e.line ? `:${e.line}` : ""})` : ""}`);
      }
      for (const e of extraOps) {
        lines.push(`- \`UNKNOWN_OPERATION_IN_YAML\` — \`${e.op}\` (\`${e.file}\`${e.line ? `:${e.line}` : ""})`);
      }
      lines.push("");
    }
    if (propErrors.length) {
      lines.push("## Property errors");
      lines.push("");
      for (const e of propErrors) {
        lines.push(`- \`${e.issue}\` — \`${e.path}\` (\`${e.file}\`${e.line ? `:${e.line}` : ""})`);
      }
      lines.push("");
    }
  }
  try {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, lines.join("\n") + "\n");
  } catch (err) {
    console.error(`Failed to append job summary: ${err.message}`);
  }
}

// Exit non-zero when any finding is reported so the PR check fails.
process.exit(totalErrors > 0 ? 1 : 0);
