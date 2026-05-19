#!/usr/bin/env node
/**
 * Core OpenAPI annotation verification.
 *
 * This module is the single source of truth for the verification pipeline
 * that checks operation-level `x-added-in-version` and property-level
 * `x-properties-added-in-version` annotations against the version-map.
 *
 * It is consumed by:
 *   - verify-specs-workflow.mjs — daily/dispatch run that writes a Markdown
 *     report.
 *   - verify-specs-ci.mjs       — PR check that emits one
 *     `::warning::` per finding for inline annotations.
 *
 * Both consumers re-emit `printCliReport()` so the standalone CLI log
 * surface is identical regardless of entry point (CI logs, workflow logs,
 * and `node verify-specs.mjs` all show the same summary).
 *
 * Standalone usage:
 *   OCA_SPEC_PATH=... VERSION_MAP_PATH=... ENDPOINT_MAP_PATH=... \
 *     node verify-specs.mjs
 */

import "dotenv/config";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { LineCounter, parseDocument } from "yaml";

// ── Input loading ───────────────────────────────────────────────────────────

export function loadInputs() {
  const specDir = process.env.OCA_SPEC_PATH;
  const versionMapPath = process.env.VERSION_MAP_PATH;
  const endpointMapPath = process.env.ENDPOINT_MAP_PATH;
  if (!specDir || !versionMapPath || !endpointMapPath) {
    console.error(
      "Missing required env variables. Set OCA_SPEC_PATH, VERSION_MAP_PATH, and ENDPOINT_MAP_PATH."
    );
    process.exit(1);
  }
  const versionMap = JSON.parse(readFileSync(versionMapPath, "utf-8"));
  const endpointMap = JSON.parse(readFileSync(endpointMapPath, "utf-8"));
  return { specDir, versionMapPath, endpointMapPath, versionMap, endpointMap };
}

// ── Helpers ─────────────────────────────────────────────────────────────────

export function compareVersions(a, b) {
  const pa = String(a).split(".").map(Number);
  const pb = String(b).split(".").map(Number);
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const diff = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

export function resolveFileAndPath(propPath, endpointPath) {
  if (!propPath || propPath.length === 0) return null;
  if (typeof propPath[0] === "string" && propPath[0].endsWith(".yaml")) {
    return { file: propPath[0], inFilePath: propPath.slice(1) };
  }
  if (typeof endpointPath?.[0] === "string" && endpointPath[0].endsWith(".yaml")) {
    return { file: endpointPath[0], inFilePath: propPath };
  }
  return null;
}

export function locationKey(file, inFilePath) {
  return `${file}\x00${inFilePath.join("\x00")}`;
}

export function getAt(obj, path) {
  let node = obj;
  for (const seg of path) {
    if (node == null || typeof node !== "object") return undefined;
    node = node[seg];
  }
  return node;
}

// YAML loader that always parses with a LineCounter so callers that need
// line numbers (CI variant) can use `lineOfPath`, while callers that don't
// (workflow variant) simply ignore the `line` field on errors.
export function createYamlLoader(specDir) {
  const cache = new Map();
  function loadYaml(file) {
    if (!cache.has(file)) {
      try {
        const src = readFileSync(join(specDir, file), "utf-8");
        const lineCounter = new LineCounter();
        const doc = parseDocument(src, { lineCounter });
        cache.set(file, { doc, lineCounter });
      } catch {
        cache.set(file, null);
      }
    }
    return cache.get(file);
  }
  function jsValue(entry) {
    if (!entry) return null;
    return entry.doc ? entry.doc.toJS({ maxAliasCount: -1 }) : null;
  }
  function lineOfPath(entry, path) {
    if (!entry) return 1;
    const { doc, lineCounter } = entry;
    const node = doc.getIn(path, true);
    if (node && typeof node === "object" && Array.isArray(node.range)) {
      const offset = node.range[0];
      if (typeof offset === "number") {
        const pos = lineCounter.linePos(offset);
        if (pos && pos.line) return pos.line;
      }
    }
    for (let i = path.length - 1; i >= 0; i--) {
      const prefix = path.slice(0, i);
      const parent = doc.getIn(prefix, true);
      if (parent && typeof parent === "object" && Array.isArray(parent.range)) {
        const offset = parent.range[0];
        if (typeof offset === "number") {
          const pos = lineCounter.linePos(offset);
          if (pos && pos.line) return pos.line;
        }
      }
    }
    return 1;
  }
  return { loadYaml, jsValue, lineOfPath };
}

// ── Verification pipeline ───────────────────────────────────────────────────

/**
 * Run the full annotation verification.
 *
 * @param {{ specDir: string, versionMap: object, endpointMap: object }} inputs
 * @returns {{
 *   opOk: number,
 *   opSkippedDeleted: number,
 *   opErrors: object[],
 *   extraOps: object[],
 *   propOk: number,
 *   propMissingTarget: number,
 *   propErrors: object[],
 *   expected: Map<string, object>,
 *   locations: Map<string, object>,
 *   allSpecYamlFiles: string[],
 *   loader: ReturnType<typeof createYamlLoader>,
 * }}
 */
export function verifySpecs({ specDir, versionMap, endpointMap }) {
  const endpointToFile = new Map(Object.entries(endpointMap));
  const deletedOps = new Set(Object.keys(versionMap.deletedOperations || {}));
  const loader = createYamlLoader(specDir);
  const { loadYaml, jsValue, lineOfPath } = loader;

  // -- Operation verification (driven by version-map) ------------------------
  let opOk = 0;
  let opSkippedDeleted = 0;
  const opErrors = [];

  for (const [opKey, info] of Object.entries(versionMap.operations)) {
    if (deletedOps.has(opKey)) {
      opSkippedDeleted++;
      continue;
    }

    const expectedVersion = info.version;
    const sourceFile = endpointToFile.get(opKey);

    if (!sourceFile) {
      opErrors.push({ op: opKey, issue: "NO_ENDPOINT_MAP_ENTRY" });
      continue;
    }

    const spaceIdx = opKey.indexOf(" ");
    const method = opKey.slice(0, spaceIdx).toLowerCase();
    const apiPath = opKey.slice(spaceIdx + 1);

    const entry = loadYaml(sourceFile);
    const doc = jsValue(entry);
    if (!doc) {
      opErrors.push({ op: opKey, issue: "YAML_FILE_NOT_FOUND", file: sourceFile });
      continue;
    }
    if (!doc.paths || !doc.paths[apiPath]) {
      opErrors.push({
        op: opKey,
        issue: "PATH_NOT_IN_YAML",
        file: sourceFile,
        path: apiPath,
        line: 1,
      });
      continue;
    }
    if (!doc.paths[apiPath][method]) {
      opErrors.push({
        op: opKey,
        issue: "METHOD_NOT_IN_YAML",
        file: sourceFile,
        path: apiPath,
        method,
        line: lineOfPath(entry, ["paths", apiPath]),
      });
      continue;
    }

    const operation = doc.paths[apiPath][method];
    const actual = operation["x-added-in-version"];
    const opLine = lineOfPath(entry, ["paths", apiPath, method]);
    if (!actual) {
      opErrors.push({
        op: opKey,
        issue: "MISSING_X_ADDED_IN_VERSION",
        file: sourceFile,
        expected: expectedVersion,
        line: opLine,
      });
    } else if (String(actual) !== String(expectedVersion)) {
      opErrors.push({
        op: opKey,
        issue: "VERSION_MISMATCH",
        file: sourceFile,
        expected: expectedVersion,
        actual: String(actual),
        line: lineOfPath(entry, ["paths", apiPath, method, "x-added-in-version"]),
      });
    } else {
      opOk++;
    }
  }

  // -- Operations in YAML but NOT in version-map -----------------------------
  const versionMapOps = new Set(Object.keys(versionMap.operations));
  const allEndpointMapYamlFiles = [...new Set(Object.values(endpointMap))];
  const extraOps = [];

  for (const file of allEndpointMapYamlFiles) {
    const entry = loadYaml(file);
    const doc = jsValue(entry);
    if (!doc || !doc.paths) continue;
    for (const [path, methods] of Object.entries(doc.paths)) {
      for (const method of ["get", "post", "put", "patch", "delete"]) {
        if (!methods[method]) continue;
        const opKey = method.toUpperCase() + " " + path;
        if (!versionMapOps.has(opKey)) {
          const has = methods[method]["x-added-in-version"];
          extraOps.push({
            op: opKey,
            file,
            hasAnnotation: !!has,
            annotationValue: has || null,
            line: lineOfPath(entry, ["paths", path, method]),
          });
        }
      }
    }
  }

  // -- Build expected property locations -------------------------------------
  const parentOf = new Map();
  for (const [propKey, entry] of Object.entries(versionMap.properties || {})) {
    for (const childKey of entry.children || []) parentOf.set(childKey, propKey);
  }

  function locationKeyOfPropEntry(entry) {
    if (!entry) return null;
    const epInfo = versionMap.operations?.[entry.endpoint];
    const r = resolveFileAndPath(entry.path, epInfo?.path);
    return r ? locationKey(r.file, r.inFilePath) : null;
  }

  const locations = new Map();
  for (const [propKey, entry] of Object.entries(versionMap.properties || {})) {
    if (versionMap.deletedProperties?.[propKey]) continue;
    const epInfo = versionMap.operations?.[entry.endpoint];
    const resolved = resolveFileAndPath(entry.path, epInfo?.path);
    if (!resolved) continue;
    const key = locationKey(resolved.file, resolved.inFilePath);
    let loc = locations.get(key);
    if (!loc) {
      loc = {
        file: resolved.file,
        inFilePath: resolved.inFilePath,
        intro: entry.version,
        endpointVersions: new Set(),
        consumerEndpoints: new Set(),
        propKeys: [],
      };
      locations.set(key, loc);
    } else if (compareVersions(entry.version, loc.intro) < 0) {
      loc.intro = entry.version;
    }
    loc.propKeys.push(propKey);
    loc.endpointVersions.add(epInfo?.version ?? null);
    if (entry.endpoint) loc.consumerEndpoints.add(entry.endpoint);
  }

  const expected = new Map();
  for (const [key, loc] of locations) {
    const allEndpointsMatchIntro =
      loc.endpointVersions.size > 0
      && [...loc.endpointVersions].every((v) => v === loc.intro);
    if (allEndpointsMatchIntro) {
      // Rule 1: single consumer endpoint matches its endpoint's intro.
      // Rule 3: shared schema (multiple consumer endpoints) where every
      // consumer endpoint matches the aggregated (earliest) intro.
      const reason = loc.consumerEndpoints.size <= 1 ? "rule1" : "rule3";
      expected.set(key, { ...loc, expectAnnotated: false, reason });
      continue;
    }
    const parentLocKeys = new Set();
    let everyPropHasParent = loc.propKeys.length > 0;
    for (const pk of loc.propKeys) {
      const parentKey = parentOf.get(pk);
      if (!parentKey) { everyPropHasParent = false; break; }
      const parentLocKey = locationKeyOfPropEntry(versionMap.properties[parentKey]);
      if (!parentLocKey) { everyPropHasParent = false; break; }
      parentLocKeys.add(parentLocKey);
    }
    if (everyPropHasParent && parentLocKeys.size > 0) {
      const allParentsMatch = [...parentLocKeys].every((pk) => {
        const pl = locations.get(pk);
        return pl && pl.intro === loc.intro;
      });
      if (allParentsMatch) {
        expected.set(key, { ...loc, expectAnnotated: false, reason: "rule2" });
        continue;
      }
    }
    expected.set(key, { ...loc, expectAnnotated: true, reason: "annotate" });
  }

  function lookupParentLevelAnnotation(doc, inFilePath) {
    if (inFilePath.length < 2 || inFilePath[inFilePath.length - 2] !== "properties") {
      return undefined;
    }
    const propName = inFilePath[inFilePath.length - 1];
    const parent = getAt(doc, inFilePath.slice(0, -2));
    const list = parent && typeof parent === "object" ? parent["x-properties-added-in-version"] : undefined;
    if (!Array.isArray(list)) return undefined;
    for (const item of list) {
      if (item && typeof item === "object" && item.propertyName === propName) {
        return item.addedInVersion;
      }
    }
    return undefined;
  }

  // Walk every YAML in the spec dir (including schema-only files like
  // keys.yaml that aren't endpoint-map members) and collect every
  // `x-properties-added-in-version` entry. Cross-checked below against
  // `expected` so entries unjustified by the version-map can be flagged.
  const allSpecYamlFiles = readdirSync(specDir).filter((f) => f.endsWith(".yaml"));
  for (const f of allSpecYamlFiles) loadYaml(f);

  const yamlPropAnnotations = []; // {file, parentPath, propName, addedInVersion}
  function walkForPropertyAnnotations(node, path, file) {
    if (node == null || typeof node !== "object") return;
    if (Array.isArray(node)) {
      for (let i = 0; i < node.length; i++) {
        walkForPropertyAnnotations(node[i], [...path, String(i)], file);
      }
      return;
    }
    const list = node["x-properties-added-in-version"];
    if (Array.isArray(list)) {
      for (const item of list) {
        if (item && typeof item === "object" && typeof item.propertyName === "string") {
          yamlPropAnnotations.push({
            file,
            parentPath: path,
            propName: item.propertyName,
            addedInVersion: item.addedInVersion,
          });
        }
      }
    }
    for (const k of Object.keys(node)) {
      if (k === "x-properties-added-in-version") continue;
      walkForPropertyAnnotations(node[k], [...path, k], file);
    }
  }
  for (const f of allSpecYamlFiles) {
    const doc = jsValue(loadYaml(f));
    if (doc) walkForPropertyAnnotations(doc, [], f);
  }

  // -- Compare expected vs actual property annotations -----------------------
  let propOk = 0;
  let propMissingTarget = 0;
  const propErrors = [];

  for (const loc of expected.values()) {
    const entry = loadYaml(loc.file);
    const doc = jsValue(entry);
    if (!doc) { propMissingTarget++; continue; }
    const node = getAt(doc, loc.inFilePath);
    if (node == null) { propMissingTarget++; continue; }
    const actual = lookupParentLevelAnnotation(doc, loc.inFilePath);
    const inFilePath = loc.inFilePath;
    const isPropertyChild =
      inFilePath.length >= 2 && inFilePath[inFilePath.length - 2] === "properties";
    const propName = isPropertyChild ? inFilePath[inFilePath.length - 1] : null;
    const parentPath = isPropertyChild ? inFilePath.slice(0, -2) : inFilePath;
    let parentHasList = false;
    if (isPropertyChild) {
      const parent = getAt(doc, parentPath);
      parentHasList =
        parent && typeof parent === "object" && Array.isArray(parent["x-properties-added-in-version"]);
    }

    if (loc.expectAnnotated) {
      if (actual === undefined) {
        propErrors.push({
          issue: "MISSING_X_PROPERTIES_ADDED_IN_VERSION",
          file: loc.file,
          path: inFilePath.join("/"),
          parentPath: parentPath.join("/"),
          propName,
          parentHasList,
          expected: loc.intro,
          line: lineOfPath(entry, inFilePath),
        });
      } else if (String(actual) !== String(loc.intro)) {
        propErrors.push({
          issue: "VERSION_MISMATCH",
          file: loc.file,
          path: inFilePath.join("/"),
          parentPath: parentPath.join("/"),
          propName,
          expected: loc.intro,
          actual: String(actual),
          line: lineOfPath(entry, [...parentPath, "x-properties-added-in-version"]),
        });
      } else {
        propOk++;
      }
    } else {
      // Suppressed: an annotation is allowed only if it agrees with the intro.
      if (actual !== undefined && String(actual) !== String(loc.intro)) {
        propErrors.push({
          issue: "UNEXPECTED_ANNOTATION_ON_SUPPRESSED",
          file: loc.file,
          path: inFilePath.join("/"),
          parentPath: parentPath.join("/"),
          propName,
          suppressedBy: loc.reason,
          intro: loc.intro,
          actual: String(actual),
          line: lineOfPath(entry, [...parentPath, "x-properties-added-in-version"]),
        });
      } else {
        propOk++;
      }
    }
  }

  // -- YAML property annotations not justified by version-map ----------------
  for (const ann of yamlPropAnnotations) {
    const inFilePath = [...ann.parentPath, "properties", ann.propName];
    const key = locationKey(ann.file, inFilePath);
    if (expected.has(key)) continue;
    const entry = loadYaml(ann.file);
    const doc = jsValue(entry);
    const parent = doc ? getAt(doc, ann.parentPath) : null;
    const propsMap =
      parent && typeof parent === "object" && parent.properties && typeof parent.properties === "object"
        ? parent.properties
        : null;
    const hasProp = !!(propsMap && Object.prototype.hasOwnProperty.call(propsMap, ann.propName));
    propErrors.push({
      issue: hasProp ? "UNKNOWN_PROPERTY_ANNOTATION" : "ORPHAN_PROPERTY_ANNOTATION",
      file: ann.file,
      path: inFilePath.join("/"),
      parentPath: ann.parentPath.join("/"),
      propName: ann.propName,
      actual: ann.addedInVersion != null ? String(ann.addedInVersion) : null,
      line: lineOfPath(entry, [...ann.parentPath, "x-properties-added-in-version"]),
    });
  }

  // -- Deleted operations still carrying x-added-in-version ------------------
  for (const f of allSpecYamlFiles) {
    const entry = loadYaml(f);
    const doc = jsValue(entry);
    if (!doc || !doc.paths) continue;
    for (const [apiPath, methods] of Object.entries(doc.paths)) {
      if (!methods || typeof methods !== "object") continue;
      for (const method of ["get", "post", "put", "patch", "delete"]) {
        const op = methods[method];
        if (!op || typeof op !== "object") continue;
        const opKey = method.toUpperCase() + " " + apiPath;
        if (deletedOps.has(opKey) && op["x-added-in-version"] !== undefined) {
          opErrors.push({
            op: opKey,
            issue: "UNEXPECTED_ANNOTATION_ON_DELETED_OPERATION",
            file: f,
            actual: String(op["x-added-in-version"]),
            line: lineOfPath(entry, ["paths", apiPath, method, "x-added-in-version"]),
          });
        }
      }
    }
  }

  return {
    opOk,
    opSkippedDeleted,
    opErrors,
    extraOps,
    propOk,
    propMissingTarget,
    propErrors,
    expected,
    locations,
    allSpecYamlFiles,
    loader,
  };
}

// ── Shared report builder ───────────────────────────────────────────────────
//
// `buildReport()` is the single source of truth for the structured output.
// Both the CLI (printed by `printCliReport`) and the workflow's Markdown
// report file feed off the exact same generator so the three entry points
// stay identical:
//
//   - verify-specs.mjs           (standalone)  → always includes detail
//   - verify-specs-workflow.mjs  (daily/run)   → detail gated by env var
//   - verify-specs-ci.mjs        (PR check)    → never includes detail

const ruleDescriptions = {
  rule1: "Rule 1 — single consumer matches endpoint (annotation should be suppressed)",
  rule2: "Rule 2 — every parent location shares the intro (annotation should be suppressed)",
  rule3: "Rule 3 — every shared consumer matches endpoint (annotation should be suppressed)",
  annotate:
    "Annotation required — property intro differs from its consumer endpoint(s) and no parent annotation covers it",
};

function fixHintForOperation(e) {
  switch (e.issue) {
    case "MISSING_X_ADDED_IN_VERSION":
      return `**To fix this**: add the annotation \`x-added-in-version: ${e.expected}\` to the \`${e.op}\` operation in \`${e.file}\`.`;
    case "VERSION_MISMATCH":
      return `**To fix this**: update the \`x-added-in-version\` of operation \`${e.op}\` in \`${e.file}\` from \`${e.actual}\` to \`${e.expected}\`.`;
    case "NO_ENDPOINT_MAP_ENTRY":
      return `**To fix this**: regenerate \`endpoint-map.json\` (\`npm run build:endpoint-map\`) so it includes \`${e.op}\`.`;
    case "YAML_FILE_NOT_FOUND":
      return `**To fix this**: ensure \`${e.file}\` exists in the upstream spec directory.`;
    case "PATH_NOT_IN_YAML":
      return `**To fix this**: add path \`${e.path}\` to \`${e.file}\` or update \`endpoint-map.json\`.`;
    case "METHOD_NOT_IN_YAML":
      return `**To fix this**: add method \`${e.method.toUpperCase()}\` for path \`${e.path}\` in \`${e.file}\` or update \`endpoint-map.json\`.`;
    case "UNEXPECTED_ANNOTATION_ON_DELETED_OPERATION":
      return `**To fix this**: remove the \`x-added-in-version: ${e.actual}\` annotation from operation \`${e.op}\` in \`${e.file}\` — the version-map flags this operation as deleted.`;
    default:
      return `**To fix this**: investigate \`${e.issue}\` for \`${e.op}\`.`;
  }
}

function fixHintForProperty(e) {
  const quotedName = e.propName ? JSON.stringify(e.propName) : null;
  switch (e.issue) {
    case "MISSING_X_PROPERTIES_ADDED_IN_VERSION": {
      if (!e.propName) {
        return `**To fix this**: add a \`x-properties-added-in-version\` annotation for ${e.path} in \`${e.file}\` (expected version \`${e.expected}\`).`;
      }
      if (e.parentHasList) {
        return [
          `**To fix this**: add the following entry to the existing \`x-properties-added-in-version\` list on \`${e.parentPath}\` in \`${e.file}\`:`,
          "",
          "```yaml",
          `        - propertyName: ${quotedName}`,
          `          addedInVersion: "${e.expected}"`,
          "```",
        ].join("\n");
      }
      return [
        `**To fix this**: add a \`x-properties-added-in-version\` list to \`${e.parentPath}\` in \`${e.file}\`:`,
        "",
        "```yaml",
        "      x-properties-added-in-version:",
        `        - propertyName: ${quotedName}`,
        `          addedInVersion: "${e.expected}"`,
        "```",
      ].join("\n");
    }
    case "VERSION_MISMATCH": {
      if (!e.propName) {
        return `**To fix this**: update the \`x-properties-added-in-version\` of ${e.path} in \`${e.file}\` from \`${e.actual}\` to \`${e.expected}\`.`;
      }
      return [
        `**To fix this**: in the \`x-properties-added-in-version\` list on \`${e.parentPath}\` in \`${e.file}\`, change the \`addedInVersion\` of \`${e.propName}\` from \`${e.actual}\` to \`${e.expected}\`:`,
        "",
        "```yaml",
        `        - propertyName: ${quotedName}`,
        `          addedInVersion: "${e.expected}"`,
        "```",
      ].join("\n");
    }
    case "UNEXPECTED_ANNOTATION_ON_SUPPRESSED":
      return `**To fix this**: remove the \`${e.propName ?? e.path}\` entry (\`addedInVersion: ${e.actual}\`) from the \`x-properties-added-in-version\` list on \`${e.parentPath ?? e.path}\` in \`${e.file}\` (suppressed by ${e.suppressedBy}; aggregated intro is \`${e.intro}\`).`;
    case "ORPHAN_PROPERTY_ANNOTATION":
      return `**To fix this**: remove the \`${e.propName}\` entry from the \`x-properties-added-in-version\` list on \`${e.parentPath}\` in \`${e.file}\` — no such property exists on this schema.`;
    case "UNKNOWN_PROPERTY_ANNOTATION":
      return `**To fix this**: either remove the \`${e.propName}\` entry from the \`x-properties-added-in-version\` list on \`${e.parentPath}\` in \`${e.file}\`, or regenerate \`version-map.json\` so this location is tracked.`;
    default:
      return `**To fix this**: investigate \`${e.issue}\` at ${e.path}.`;
  }
}

function fixHintForMissingGroup(errors) {
  // All entries share file + parentPath + parentHasList by construction.
  const { file, parentPath, parentHasList } = errors[0];
  const entries = errors.flatMap((e) => [
    `        - propertyName: ${JSON.stringify(e.propName)}`,
    `          addedInVersion: "${e.expected}"`,
  ]);
  if (parentHasList) {
    return [
      `**To fix this**: add the following entries to the existing \`x-properties-added-in-version\` list on \`${parentPath}\` in \`${file}\`:`,
      "",
      "```yaml",
      ...entries,
      "```",
    ].join("\n");
  }
  return [
    `**To fix this**: add a \`x-properties-added-in-version\` list to \`${parentPath}\` in \`${file}\`:`,
    "",
    "```yaml",
    "      x-properties-added-in-version:",
    ...entries,
    "```",
  ].join("\n");
}

function appendDetailSection(emit, result, versionMap) {
  const {
    opOk, opSkippedDeleted, opErrors, extraOps,
    propOk, propMissingTarget, propErrors, expected,
  } = result;

  emit("======== Detailed verification results analysis  =================");
  emit("");

  if (propErrors.length) {
    const brokenRules = new Set();
    for (const e of propErrors) brokenRules.add(e.suppressedBy ?? "annotate");
    emit("### Rules broken");
    emit("");
    let ruleNum = 1;
    for (const ruleKey of ["annotate", "rule1", "rule2", "rule3"]) {
      if (brokenRules.has(ruleKey)) {
        emit(`- **#${ruleNum}** — ${ruleDescriptions[ruleKey]}`);
        ruleNum++;
      }
    }
    emit("");
  }

  if (opErrors.length || extraOps.length) {
    emit("### Operations");
    emit("");
    emit(`- Operations in version-map: ${Object.keys(versionMap.operations).length}`);
    emit(`- OK: ${opOk}`);
    emit(`- Deleted (skipped): ${opSkippedDeleted}`);
    emit(`- Errors: ${opErrors.length}`);
    if (extraOps.length) {
      emit(`- Operations in YAML but NOT in version-map: ${extraOps.length}`);
    }
    emit("");
  }

  if (propErrors.length) {
    const byReason = { rule1: 0, rule3: 0, rule2: 0 };
    let expectedAnnotatedCount = 0;
    for (const l of expected.values()) {
      if (l.expectAnnotated) expectedAnnotatedCount++;
      else byReason[l.reason] = (byReason[l.reason] ?? 0) + 1;
    }
    emit("### Properties");
    emit("");
    emit(`- Property locations checked: ${expected.size}`);
    emit(`- Expected annotated: ${expectedAnnotatedCount}`);
    emit(`- Expected suppressed (Rule 1 — single consumer matches endpoint): ${byReason.rule1}`);
    emit(`- Expected suppressed (Rule 3 — every shared consumer matches endpoint): ${byReason.rule3}`);
    emit(`- Expected suppressed (Rule 2 — every parent location shares the intro): ${byReason.rule2}`);
    emit(`- OK: ${propOk}`);
    emit(`- Target node missing in YAML: ${propMissingTarget}`);
    emit(`- Errors: ${propErrors.length}`);
    emit("");
  }
}

/**
 * Build the structured report text. Identical content is used for CLI
 * output and the workflow's Markdown report file.
 *
 * @param {object} result    return value of `verifySpecs()`
 * @param {object} versionMap
 * @param {{ includeDetail?: boolean, summaryOnly?: boolean, skipStats?: boolean }} [opts]
 *   includeDetail — when true, appends the "Detailed verification results
 *   analysis" trailer (rule breakdown + counts).
 *   summaryOnly  — when true, emits only a single status line (no headers,
 *   per-error listings, or fix sections). Intended for the non-blocking CI
 *   wrapper, which renders findings via inline PR annotations instead.
 *   skipStats    — when true, omits the "# x-added-in-version Error Stats"
 *   header and the per-error bullet listings, but keeps the "Operation
 *   errors fix" and "Property errors fix" sections. Used by the CI wrapper
 *   so the fix guidance stays in the log without duplicating the stats
 *   block already covered by the inline annotations.
 */
export function buildReport(
  result,
  versionMap,
  { includeDetail = false, summaryOnly = false, skipStats = false } = {}
) {
  const { opErrors, extraOps, propErrors } = result;
  const total = opErrors.length + extraOps.length + propErrors.length;
  const out = [];
  const emit = (line = "") => out.push(line);

  const affectedFiles = new Set();
  for (const e of opErrors) if (e.file) affectedFiles.add(e.file);
  for (const e of extraOps) if (e.file) affectedFiles.add(e.file);
  for (const e of propErrors) if (e.file) affectedFiles.add(e.file);

  if (summaryOnly) {
    if (total === 0) {
      emit("✅ OpenAPI annotation verification: no incorrect annotations.");
    } else {
      emit(
        `❌ Found ${total} incorrect x-added-in-version/x-properties-added-in-version across ${affectedFiles.size} ${affectedFiles.size === 1 ? "file" : "files"} (non-blocking).`
      );
    }
    return out.join("\n");
  }

  if (total === 0) {
    emit("# x-added-in-version verification");
    emit("");
    emit("All checks passed! No errors across all properties and operations in the spec.");
    return out.join("\n");
  }

  // ── Error stats ───────────────────────────────────────────────────────────
  if (!skipStats) {
    emit("# x-added-in-version Error Stats");
    emit("");
    emit(
      `**Status:** ❌ Found ${total} ${total === 1 ? "error" : "errors"} across ${affectedFiles.size} ${affectedFiles.size === 1 ? "file" : "files"}`
    );
    emit("");

    if (opErrors.length || extraOps.length) {
      emit("--- Operations ERRORS ---");
      for (const e of opErrors) {
        emit("  " + e.issue + ": " + (e.file ? e.file + " :: " : "") + e.op +
          (e.expected ? " expected=" + e.expected : "") +
          (e.actual ? " actual=" + e.actual : ""));
      }
      for (const e of extraOps) {
        emit("  UNKNOWN_OPERATION_IN_YAML: " + e.file + " :: " + e.op +
          (e.hasAnnotation ? " hasAnnotation=" + e.annotationValue : " noAnnotation"));
      }
      emit("");
    }

    if (propErrors.length) {
      emit("--- PROPERTY ERRORS ---");
      for (const e of propErrors) {
        const extras = [
          e.expected ? "expected=" + e.expected : null,
          e.actual ? "actual=" + e.actual : null,
          e.suppressedBy ? "suppressedBy=" + e.suppressedBy : null,
          e.intro && !e.expected ? "intro=" + e.intro : null,
        ].filter(Boolean).join(" ");
        emit("  " + e.issue + ": " + e.file + " :: " + e.path + (extras ? " " + extras : ""));
      }
      emit("");
    }
  }

  // ── Operation errors fix ─────────────────────────────────────────────────
  if (opErrors.length || extraOps.length) {
    emit("");
    emit("# Operation errors fix");
    emit("");
    for (const e of opErrors) {
      const extras = [
        e.expected ? "expected=`" + e.expected + "`" : null,
        e.actual ? "actual=`" + e.actual + "`" : null,
      ].filter(Boolean).join(", ");
      emit(`## \`${e.issue}\` — \`${e.op}\``);
      emit("");
      if (e.file) emit(`- **File:** \`${e.file}\``);
      if (extras) emit(`- ${extras}`);
      emit("");
      emit(fixHintForOperation(e));
      emit("");
    }
    for (const e of extraOps) {
      emit(`## \`UNKNOWN_OPERATION_IN_YAML\` — \`${e.op}\``);
      emit("");
      emit(`- **File:** \`${e.file}\``);
      emit(
        `- ${e.hasAnnotation ? "Has `x-added-in-version`=`" + e.annotationValue + "`" : "No `x-added-in-version`"}`
      );
      emit("");
      emit(
        `**To fix this**: either add \`${e.op}\` to the version-map (regenerate \`version-map.json\`) or remove the operation from \`${e.file}\`.`
      );
      emit("");
    }
  }

  // ── Property errors fix ──────────────────────────────────────────────────
  if (propErrors.length) {
    emit("# Property errors fix");
    emit("");
    const printed = new Set();
    for (let i = 0; i < propErrors.length; i++) {
      if (printed.has(i)) continue;
      const e = propErrors[i];
      if (
        e.issue === "MISSING_X_PROPERTIES_ADDED_IN_VERSION" &&
        e.propName &&
        e.parentPath
      ) {
        const groupKey = `${e.file}::${e.parentPath}`;
        const group = [];
        for (let j = i; j < propErrors.length; j++) {
          const o = propErrors[j];
          if (
            !printed.has(j) &&
            o.issue === "MISSING_X_PROPERTIES_ADDED_IN_VERSION" &&
            o.propName &&
            o.parentPath &&
            `${o.file}::${o.parentPath}` === groupKey
          ) {
            group.push(o);
            printed.add(j);
          }
        }
        emit(`## \`MISSING_X_PROPERTIES_ADDED_IN_VERSION\` — \`${e.parentPath}\` (\`${e.file}\`)`);
        emit("");
        for (const err of group) {
          emit(`- \`${err.path}\` — expected=\`${err.expected}\``);
        }
        emit("");
        emit(fixHintForMissingGroup(group));
        emit("");
        continue;
      }
      const extras = [
        e.expected ? "expected=`" + e.expected + "`" : null,
        e.actual ? "actual=`" + e.actual + "`" : null,
        e.suppressedBy ? "suppressedBy=`" + e.suppressedBy + "`" : null,
        e.intro && !e.expected ? "intro=`" + e.intro + "`" : null,
      ].filter(Boolean).join(", ");
      emit(`## \`${e.issue}\` — \`${e.path}\``);
      emit("");
      emit(`- **File:** \`${e.file}\``);
      if (extras) emit(`- ${extras}`);
      emit("");
      emit(fixHintForProperty(e));
      emit("");
      printed.add(i);
    }
  }

  // ── Optional detail trailer ──────────────────────────────────────────────
  if (includeDetail) {
    appendDetailSection(emit, result, versionMap);
  }

  return out.join("\n");
}

/**
 * Print `buildReport()` to stdout. Used by all three entry points so the
 * console surface stays identical.
 */
export function printCliReport(result, versionMap, opts) {
  console.log(buildReport(result, versionMap, opts));
}

export function hasAnyError(result) {
  return result.opErrors.length > 0 || result.extraOps.length > 0 || result.propErrors.length > 0;
}

// ── CLI entry point ─────────────────────────────────────────────────────────

const isMain = process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1];
if (isMain) {
  const inputs = loadInputs();
  const result = verifySpecs(inputs);
  // Standalone CLI always shows the detail trailer.
  printCliReport(result, inputs.versionMap, { includeDetail: true });
  if (hasAnyError(result)) process.exit(1);
}
