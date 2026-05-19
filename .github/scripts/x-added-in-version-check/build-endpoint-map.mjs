#!/usr/bin/env node
/**
 * Generates endpoint-map.json by fetching the upstream Camunda multi-file
 * OpenAPI spec at a configurable git ref and bundling it via
 * camunda-schema-bundler. The bundler's `outputEndpointMap` option writes
 * `{ "METHOD /path": "<source-file>.yaml" }` for every operation.
 *
 * Env:
 *   CAMUNDA_REF        Git ref to fetch (default: "main")
 *   CAMUNDA_REPO_URL   Git repo URL (default: https://github.com/camunda/camunda.git)
 *   ENDPOINT_MAP_PATH  Output path (default: ./artefacts/endpoint-map.json)
 *
 * Usage:
 *   node build-endpoint-map.mjs
 */
import "dotenv/config";
import { existsSync, mkdirSync, mkdtempSync, rmSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fetchAndBundle } from "camunda-schema-bundler";

const ref = process.env.CAMUNDA_REF ?? "main";
const repoUrl = process.env.CAMUNDA_REPO_URL ?? "https://github.com/camunda/camunda.git";
const endpointMapPath = resolve(process.env.ENDPOINT_MAP_PATH ?? "./artefacts/endpoint-map.json");

const workDir = mkdtempSync(join(tmpdir(), "update-openapi-spec-bundle-"));
const fetchedSpecDir = join(workDir, "spec");
const bundledSpecPath = join(workDir, "rest-api.bundle.json");
const bundlerEndpointMapPath = join(workDir, "endpoint-map.json");

try {
  console.log(`Fetching upstream spec (${repoUrl} @ ${ref})…`);
  const result = await fetchAndBundle({
    ref,
    repoUrl,
    outputDir: fetchedSpecDir,
    outputSpec: bundledSpecPath,
    outputEndpointMap: bundlerEndpointMapPath,
  });
  console.log(
    `Bundled ${result.stats.pathCount} paths / ${result.stats.schemaCount} schemas`,
  );

  // Copy bundler output to the configured location.
  const contents = readFileSync(bundlerEndpointMapPath, "utf-8");
  mkdirSync(dirname(endpointMapPath), { recursive: true });
  writeFileSync(endpointMapPath, contents);
  console.log(`Wrote ${endpointMapPath}`);
} finally {
  rmSync(workDir, { recursive: true, force: true });
}
