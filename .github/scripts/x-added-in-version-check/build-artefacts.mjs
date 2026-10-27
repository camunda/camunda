#!/usr/bin/env node
/**
 * Generates both version-map.json and endpoint-map.json by running
 * build-bundler-version-map.mjs (vendored from camunda/return-of-api-added-
 * in-analysis) directly in this directory, and copying the produced
 * `output/bundler-version-map.json` and `output/endpoint-map.json` to the
 * configured paths.
 *
 * Bundled OpenAPI specs are cached on disk in `bundler-specs/` (next to this
 * script by default) and shared across runs.
 *
 * Env:
 *   VERSION_MAP_PATH   Output path (default: ./artefacts/version-map.json)
 *   ENDPOINT_MAP_PATH  Output path (default: ./artefacts/endpoint-map.json)
 *   BUNDLER_SPECS_DIR  Persistent cache for fetched/bundled specs
 *                      (default: ./artefacts/bundler-specs, resolved
 *                      relative to this script's directory)
 *   (see build-bundler-version-map.mjs's own header for the rest —
 *   VERSIONS, MAIN_BRANCH_VERSIONS, LATEST_BRANCH, etc. — all forwarded
 *   through unchanged.)
 *
 * Usage:
 *   node build-artefacts.mjs
 */
import "dotenv/config";
import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, rmSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const versionMapPath = resolve(
  process.env.VERSION_MAP_PATH ?? join(scriptDir, "artefacts", "version-map.json"),
);
const endpointMapPath = resolve(
  process.env.ENDPOINT_MAP_PATH ?? join(scriptDir, "artefacts", "endpoint-map.json"),
);
const bundlerSpecsDir = resolve(
  process.env.BUNDLER_SPECS_DIR ?? join(scriptDir, "artefacts", "bundler-specs"),
);
// Scratch output dir for the vendored script's own OUTPUT_PATH, separate from
// the persistent bundler-specs cache above. Wiped before every run so a
// failed previous run's stale files can never be mistaken for fresh output.
const outputDir = join(scriptDir, "artefacts", ".bundler-output");

function run(args, options = {}) {
  execFileSync(args[0], args.slice(1), {
    stdio: "inherit",
    timeout: 600_000,
    ...options,
  });
}

// No `npm ci` here: build-bundler-version-map.mjs is a normal file in this
// directory now, not a separate cloned repo with its own node_modules — its
// one real dependency (camunda-schema-bundler) is declared in this
// directory's own package.json, and already installed by the "Install
// verifier dependencies" step that both calling workflows run before this
// script, via the shared npm-ci-with-retry action.
mkdirSync(bundlerSpecsDir, { recursive: true });
console.log(`Using bundler-specs cache at ${bundlerSpecsDir}`);

rmSync(outputDir, { recursive: true, force: true });

console.log("Running build-bundler-version-map.mjs…");
run(["node", "build-bundler-version-map.mjs"], {
  cwd: scriptDir,
  env: {
    ...process.env,
    BUNDLER_SPECS_DIR: bundlerSpecsDir,
    OUTPUT_PATH: outputDir,
  },
});

const producedVersionMap = join(outputDir, "bundler-version-map.json");
if (!existsSync(producedVersionMap)) {
  throw new Error(
    `Expected version map at ${producedVersionMap} after running build-bundler-version-map.mjs — not found.`,
  );
}
mkdirSync(dirname(versionMapPath), { recursive: true });
copyFileSync(producedVersionMap, versionMapPath);
console.log(`Wrote ${versionMapPath}`);

const producedEndpointMap = join(outputDir, "endpoint-map.json");
if (!existsSync(producedEndpointMap)) {
  throw new Error(
    `Expected endpoint map at ${producedEndpointMap} after running build-bundler-version-map.mjs — not found.`,
  );
}
mkdirSync(dirname(endpointMapPath), { recursive: true });
copyFileSync(producedEndpointMap, endpointMapPath);
console.log(`Wrote ${endpointMapPath}`);
