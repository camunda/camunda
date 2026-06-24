#!/usr/bin/env node
/**
 * Generates both version-map.json and endpoint-map.json by cloning the
 * return-of-api-added-in-analysis repo at a configurable git ref into a
 * temporary directory, running `npm ci --omit=dev && npm run build:bundler`
 * inside it, and copying the produced `output/bundler-version-map.json` and
 * `output/endpoint-map.json` to the configured paths.
 *
 * Bundled OpenAPI specs are cached on disk in `bundler-specs/` (next to this
 * script by default) and shared across runs.
 * 
 * The git-clone strategy mirrors the depth-1 / SHA-aware logic from
 * camunda-schema-bundler/src/fetch.ts so this script understands branches,
 * tags, and raw commit SHAs uniformly.
 *
 * Env:
 *   RETURN_OF_API_REF         Git ref to clone (default: "main")
 *   RETURN_OF_API_REPO_URL    Git repo URL
 *                             (default: https://github.com/camunda/return-of-api-added-in-analysis.git)
 *   VERSION_MAP_PATH          Output path (default: ./artefacts/version-map.json)
 *   ENDPOINT_MAP_PATH         Output path (default: ./artefacts/endpoint-map.json)
 *   BUNDLER_SPECS_DIR         Persistent cache for fetched/bundled specs
 *                             (default: ./artefacts/bundler-specs, resolved
 *                             relative to this script's directory)
 *
 * Usage:
 *   node build-artefacts.mjs
 */
import "dotenv/config";
import { execFileSync } from "node:child_process";
import {
  copyFileSync,
  existsSync,
  mkdirSync,
  mkdtempSync,
  rmSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const ref = process.env.RETURN_OF_API_REF ?? "main";
const repoUrl =
  process.env.RETURN_OF_API_REPO_URL ??
  "https://github.com/camunda/return-of-api-added-in-analysis.git";
const versionMapPath = resolve(
  process.env.VERSION_MAP_PATH ?? join(scriptDir, "artefacts", "version-map.json"),
);
const endpointMapPath = resolve(
  process.env.ENDPOINT_MAP_PATH ?? join(scriptDir, "artefacts", "endpoint-map.json"),
);
const bundlerSpecsDir = resolve(
  process.env.BUNDLER_SPECS_DIR ?? join(scriptDir, "artefacts", "bundler-specs"),
);

/** A ref that's 7-40 hex characters is treated as a commit SHA. */
function isCommitSha(r) {
  return /^[0-9a-f]{7,40}$/i.test(r);
}

function run(args, options = {}) {
  execFileSync(args[0], args.slice(1), {
    stdio: "inherit",
    timeout: 600_000,
    ...options,
  });
}

const tmpRoot = mkdtempSync(join(tmpdir(), "return-of-api-added-"));
const cloneDir = join(tmpRoot, "repo");

try {
  console.log(`Cloning ${repoUrl} @ ${ref}…`);
  if (isCommitSha(ref)) {
    // Branch/tag refs work with `git clone --branch`; raw commit SHAs do not.
    // Fall back to init + fetch-by-SHA, which GitHub permits because the repo
    // sets uploadpack.allowReachableSHA1InWant on the server side.
    run(["git", "init", cloneDir]);
    run(["git", "-C", cloneDir, "remote", "add", "origin", repoUrl]);
    run(["git", "-C", cloneDir, "fetch", "--depth", "1", "origin", ref]);
    run(["git", "-C", cloneDir, "checkout", "FETCH_HEAD"]);
  } else {
    run(["git", "clone", "--depth", "1", "--branch", ref, repoUrl, cloneDir]);
  }

  console.log("Installing dependencies (production only)…");
  // `npm ci --omit=dev` is significantly faster than `npm install` and is
  // sufficient here because `npm run build:bundler` in
  // return-of-api-added-in-analysis only invokes runtime dependencies.
  run(["npm", "ci", "--omit=dev"], { cwd: cloneDir });


  mkdirSync(bundlerSpecsDir, { recursive: true });
  console.log(`Using bundler-specs cache at ${bundlerSpecsDir}`);

  console.log("Running `npm run build:bundler`…");
  run(["npm", "run", "build:bundler"], {
    cwd: cloneDir,
    env: {
      ...process.env,
      BUNDLER_SPECS_DIR: bundlerSpecsDir,
    },
  });

  const producedVersionMap = join(cloneDir, "output", "bundler-version-map.json");
  if (!existsSync(producedVersionMap)) {
    throw new Error(
      `Expected version map at ${producedVersionMap} after \`npm run build:bundler\` — not found.`,
    );
  }
  mkdirSync(dirname(versionMapPath), { recursive: true });
  copyFileSync(producedVersionMap, versionMapPath);
  console.log(`Wrote ${versionMapPath}`);

  const producedEndpointMap = join(cloneDir, "output", "endpoint-map.json");
  if (!existsSync(producedEndpointMap)) {
    throw new Error(
      `Expected endpoint map at ${producedEndpointMap} after \`npm run build:bundler\` — not found.`,
    );
  }
  mkdirSync(dirname(endpointMapPath), { recursive: true });
  copyFileSync(producedEndpointMap, endpointMapPath);
  console.log(`Wrote ${endpointMapPath}`);
} finally {
  rmSync(tmpRoot, { recursive: true, force: true });
}
