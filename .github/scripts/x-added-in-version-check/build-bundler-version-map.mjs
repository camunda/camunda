/**
 * build-bundler-version-map.mjs
 *
 * Uses camunda-schema-bundler to fetch and bundle OpenAPI specs for versions
 * 8.5–8.11 directly from the Camunda GitHub repo, then builds a unified
 * version map recording when each operation and property was first introduced.
 *
 * This is the bundler-based equivalent of build-version-map.mjs, which reads
 * pre-extracted YAML specs from disk. Here the bundler handles fetching,
 * bundling, and normalization automatically.
 *
 * Output: output/bundler-version-map.json
 */
import 'dotenv/config';
import { writeFileSync, mkdirSync, readFileSync, readdirSync, rmSync, existsSync } from 'fs';
import { fetchAndBundle, specDirForRef } from 'camunda-schema-bundler';
import YAML from 'yaml';
import { collapseSiblingVariants } from './sibling-variant-collapse.mjs';
import { collapseBackwardsCompat } from './backwards-compat-collapse.mjs';
import { HTTP_METHODS, extractSpecData } from './spec-extraction.mjs';
import { mergeVersion } from './version-map-merge.mjs';

const yaml = { load: (source) => YAML.parse(source) };

// ─── Configuration (env-overridable) ───────────────────────────────────────────────
//
//   VERSIONS                Comma-separated list (default: 8.5,8.6,8.7,8.8,8.9,8.10,8.11)
//   MAIN_BRANCH_VERSIONS    Versions that track the `main` branch instead of
//                           a released version's `<v>.0` tag (default: 8.11)
//   LATEST_BRANCH           Optional ref to try first for every
//                           MAIN_BRANCH_VERSIONS entry. Falls back to `main`
//                           if the fetch fails (e.g. branch doesn't exist).
//                           Stable versions ignore this and always use the
//                           `<version>.0` release tag — NOT the stable/<v>
//                           branch tip, which keeps moving after release via
//                           patch backports. Pinning to the `.0` tag freezes
//                           each released baseline at what actually shipped
//                           in that minor, so a later patch backport can
//                           never retroactively change a property's or
//                           operation's computed introduction version. See
//                           https://github.com/camunda/camunda/issues/58872.
//   BUNDLER_SPECS_DIR       Where fetched/bundled specs are cached
//                           (default: bundler-specs)
//   OUTPUT_PATH             Output directory for generated artefacts
//                           (default: output). Writes `bundler-version-map.json`
//                           and, per MAIN_BRANCH_VERSIONS entry,
//                           `endpoint-map-<version>.json`.
//   REGENERATE_LATEST_SPEC_ONLY  Truthy (1/true/yes/on) → wipe every cache
//                           dir listed in MAIN_BRANCH_VERSIONS before AND after
//                           fetching, forcing camunda-schema-bundler to
//                           re-download. Use after the upstream `main` branch
//                           has changed.
function parseCsv(value, fallback) {
  if (!value) return fallback;
  return value.split(',').map((s) => s.trim()).filter(Boolean);
}
function parseBool(value) {
  if (!value) return false;
  return /^(1|true|yes|on)$/i.test(value);
}
const VERSIONS = parseCsv(process.env.VERSIONS, ['8.5', '8.6', '8.7', '8.8', '8.9', '8.10', '8.11']);
const MAIN_BRANCH_VERSIONS = parseCsv(process.env.MAIN_BRANCH_VERSIONS, ['8.11']);
// 8.11 (or whatever MAIN_BRANCH_VERSIONS lists) tracks `main` until it cuts
// its own release branch. Everything else is pinned to its `<version>.0`
// release tag on camunda/camunda — immutable once cut, unlike the
// stable/<version> branch, which keeps moving via patch backports. When
// LATEST_BRANCH is set, it overrides `main` as the preferred ref for
// MAIN_BRANCH_VERSIONS, with `main` as the fallback.
const LATEST_BRANCH = process.env.LATEST_BRANCH || null;
const VERSION_REFS = Object.fromEntries(
  MAIN_BRANCH_VERSIONS.map((v) => [v, LATEST_BRANCH ?? 'main']),
);
const BUNDLER_SPECS_DIR = process.env.BUNDLER_SPECS_DIR ?? 'bundler-specs';
const OUTPUT_PATH = process.env.OUTPUT_PATH ?? 'output';

// ─── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  console.log('Building version map from camunda-schema-bundler output...\n');

  // When REGENERATE_LATEST_SPEC_ONLY is truthy, wipe every MAIN_BRANCH_VERSIONS
  // cache dir before fetching so fetchAndBundle re-downloads from the mutable
  // upstream ref (e.g. `main`). Stable refs are immutable so their caches are
  // always reused.
  if (parseBool(process.env.REGENERATE_LATEST_SPEC_ONLY)) {
    for (const version of MAIN_BRANCH_VERSIONS) {
      const dir = `${BUNDLER_SPECS_DIR}/${version}`;
      if (existsSync(dir)) {
        console.log(`REGENERATE_LATEST_SPEC_ONLY=1 — clearing ${dir}`);
        rmSync(dir, { recursive: true, force: true });
      }
    }
  }

  const versionMap = {
    metadata: {
      multiFileVersions: [],
    },
    operations: {},
    properties: {},
    deletedOperations: {},
    deletedProperties: {},
  };

  let previousOps = null;
  let previousProps = null;
  // The bundled spec of the last (newest) version, for the sibling-variant collapse.
  let latestSpec = null;

  for (const version of VERSIONS) {
    const ref = VERSION_REFS[version] ?? `${version}.0`;
    // camunda-schema-bundler's specDirForRef(ref) only recognises the literal
    // branch name pattern `stable/8.<minor>` to pick the pre-8.9 monolithic
    // spec dir; any other ref pointing at the same content (a `<v>.0` tag, a
    // SHA, ...) falls through to its v2-domain-split default and fails
    // outright, since that path doesn't exist before 8.9. Call it with a
    // synthetic `stable/<version>` string instead of the real ref, so the
    // right spec dir is picked regardless of what ref is actually used to
    // fetch this version.
    const specDir = specDirForRef(`stable/${version}`);
    const outputDir = `${BUNDLER_SPECS_DIR}/${version}/upstream`;
    const outputSpec = `${BUNDLER_SPECS_DIR}/${version}/rest-api.bundle.json`;
    // `endpoint-map.json` was deprecated in camunda-schema-bundler 2.2.0 and is
    // scheduled for removal in 3.0.0 — the `sourceFile` it carried now lives
    // on each `OperationSummary` inside `spec-metadata.json`. See
    // https://github.com/camunda/camunda-schema-bundler/issues/21. We emit the
    // metadata file per version here, and derive `endpoint-map.json` from it
    // for MAIN_BRANCH_VERSIONS below.
    const outputMetadata = `${BUNDLER_SPECS_DIR}/${version}/spec-metadata.json`;

    let result;
    // Cache hit: skip the network round-trip when both the bundle JSON and the
    // upstream YAML directory are already present. REGENERATE_LATEST_SPEC_ONLY has
    // already wiped MAIN_BRANCH_VERSIONS cache dirs above, so those always miss
    // the cache and re-fetch.
    const upstreamHasYaml = existsSync(outputDir)
      && readdirSync(outputDir).some((f) => f.endsWith('.yaml'));
    if (existsSync(outputSpec) && upstreamHasYaml && existsSync(outputMetadata)) {
      console.log(`  Using cached bundle for ${ref} (${outputSpec})`);
      try {
        const spec = JSON.parse(readFileSync(outputSpec, 'utf8'));
        result = {
          spec,
          stats: {
            pathCount: spec?.paths ? Object.keys(spec.paths).length : 0,
            schemaCount: spec?.components?.schemas
              ? Object.keys(spec.components.schemas).length : 0,
          },
        };
      } catch (err) {
        console.warn(`  WARN: cached bundle unreadable, re-fetching: ${err.message}`);
        result = null;
      }
    }

    if (!result) {
      console.log(`  Fetching and bundling ${ref}...`);
      try {
        result = await fetchAndBundle({
          ref,
          specDir,
          outputDir,
          outputSpec,
          outputMetadata,
          allowPathLocalLikeRefs: true,
          restoreUpstreamOperationRefs: true
        });
      } catch (err) {
        // Fallback path: when LATEST_BRANCH was preferred for a
        // MAIN_BRANCH_VERSIONS entry and the fetch failed (typically because
        // the branch doesn't exist upstream yet), retry against `main`. Only
        // applies to refs we synthesised from LATEST_BRANCH; stable refs and
        // explicit `main` failures propagate unchanged.
        const shouldFallback =
          LATEST_BRANCH
          && ref === LATEST_BRANCH
          && MAIN_BRANCH_VERSIONS.includes(version);
        if (shouldFallback) {
          console.warn(
            `  WARN: fetching ${ref} for ${version} failed (${err.message}); ` +
            `falling back to \`main\`.`
          );
          try {
            result = await fetchAndBundle({
              ref: 'main',
              specDir,
              outputDir,
              outputSpec,
              outputMetadata,
              allowPathLocalLikeRefs: true,
              restoreUpstreamOperationRefs: true
            });
          } catch (fallbackErr) {
            throw new Error(
              `Failed to bundle ${version}: ref ${ref} failed (${err.message}), and the ` +
              `fallback to \`main\` also failed (${fallbackErr.message}). Refusing to write ` +
              `a version-map computed from an incomplete baseline set.`
            );
          }
        } else {
          throw new Error(
            `Failed to bundle ${version} (ref ${ref}): ${err.message}. Refusing to write a ` +
            `version-map computed from an incomplete baseline set.`
          );
        }
      }
    }

    const spec = result.spec;
    latestSpec = spec;
    console.log(`    Bundled: ${result.stats.pathCount} paths, ${result.stats.schemaCount} schemas`);

    // Build operation → source file map, schema → source file map,
    // and operation → original schema $ref map by scanning upstream YAML files
    const operationFileMap = new Map();
    const schemaFileMap = new Map();
    const operationSchemaRefMap = new Map();
    try {
      const files = readdirSync(outputDir).filter(f => f.endsWith('.yaml')).sort();
      for (const file of files) {
        try {
          const content = readFileSync(`${outputDir}/${file}`, 'utf8');
          const parsed = yaml.load(content);
          // Map operations (paths) to source files
          if (parsed?.paths) {
            for (const [path, pathItem] of Object.entries(parsed.paths)) {
              for (const method of HTTP_METHODS) {
                if (pathItem[method]) {
                  const opKey = `${method.toUpperCase()} ${path}`;
                  if (!operationFileMap.has(opKey)) {
                    operationFileMap.set(opKey, file);
                  }
                  // Record original $ref for request/response schemas.
                  // These may be lost when the bundler inlines schemas.
                  // Normalize cross-file refs like "file.yaml#/..." to "#/..."
                  // since the bundled spec uses local refs.
                  if (!operationSchemaRefMap.has(opKey)) {
                    const op = pathItem[method];
                    const normalizeRef = (r) => r ? '#' + r.split('#').pop() : null;
                    const reqRef = normalizeRef(
                      op.requestBody?.content?.['application/json']?.schema?.$ref
                      || op.requestBody?.content?.['multipart/form-data']?.schema?.$ref
                    );
                    const responses = op.responses || {};
                    const successCode = Object.keys(responses).find(
                      (c) => c === '200' || c === '201' || c === '204'
                    ) || Object.keys(responses).find((c) => c.startsWith('2'));
                    const resRef = successCode
                      ? normalizeRef(responses[successCode]?.content?.['application/json']?.schema?.$ref)
                      : null;
                    if (reqRef || resRef) {
                      operationSchemaRefMap.set(opKey, { requestRef: reqRef, responseRef: resRef });
                    }
                  }
                }
              }
            }
          }
          // Map schemas to source files
          const schemas = parsed?.components?.schemas;
          if (schemas) {
            for (const name of Object.keys(schemas)) {
              if (!schemaFileMap.has(name)) {
                schemaFileMap.set(name, file);
              }
            }
          }
        } catch (error) {
          console.warn(`    WARN: failed to parse upstream YAML ${version}/${file}: ${error?.message || error}`);
        }
      }
    } catch (error) {
      console.warn(`    WARN: failed to read upstream dir for ${version}: ${error?.message || error}`);
    }

    // Track multi-file metadata (monolithic specs have ≤2 schema files;
    // multi-file specs have many domain-specific YAML files).
    const uniqueSchemaFiles = new Set(schemaFileMap.values());
    const isMultiFile = uniqueSchemaFiles.size > 2;
    if (isMultiFile) {
      versionMap.metadata.multiFileVersions.push(version);
    }

    // Always pass the upstream-file maps so paths are prefixed with their
    // source filename whenever attribution is available. When a property was
    // first seen in an earlier (single-file) version, its path is upgraded
    // here using the latest version's split-file attribution while preserving
    // the original `version` field.
    const { operations, properties } = extractSpecData(
      spec,
      operationFileMap,
      schemaFileMap,
      operationSchemaRefMap
    );

    const { newOps, newProps, deletedOps, deletedProps } = mergeVersion(
      versionMap, version, { operations, properties }, { operations: previousOps, properties: previousProps },
    );

    console.log(
      `    ${version}: ${operations.size} ops (${newOps} new), ` +
      `${properties.size} props (${newProps} new)` +
      (deletedOps > 0 ? `, ${deletedOps} deleted ops` : '') +
      (deletedProps > 0 ? `, ${deletedProps} deleted props` : '')
    );

    previousOps = operations;
    previousProps = properties;
  }

  // Derive `endpoint-map.json` for every MAIN_BRANCH_VERSIONS entry from the
  // `spec-metadata.json` the bundler just wrote. The standalone
  // `outputEndpointMap` bundler flag is deprecated (see
  // https://github.com/camunda/camunda-schema-bundler/issues/21); `sourceFile`
  // now lives on each `OperationSummary`. Failing loud here — missing
  // metadata, missing `sourceFile`, or missing method/path — is intentional:
  // a silently-empty endpoint-map would mask upstream bundler regressions.
  for (const version of MAIN_BRANCH_VERSIONS) {
    const metadataPath = `${BUNDLER_SPECS_DIR}/${version}/spec-metadata.json`;
    if (!existsSync(metadataPath)) {
      throw new Error(
        `Cannot derive endpoint-map for ${version}: ${metadataPath} not found. ` +
        `Did camunda-schema-bundler fail for this version?`
      );
    }
    const metadata = JSON.parse(readFileSync(metadataPath, 'utf8'));
    if (!Array.isArray(metadata?.operations)) {
      throw new Error(
        `${metadataPath} is missing an \`operations\` array; expected ` +
        `spec-metadata.json from camunda-schema-bundler >= 2.2.0.`
      );
    }
    const entries = metadata.operations
      .map((op) => {
        if (typeof op?.method !== 'string' || typeof op?.path !== 'string') {
          throw new Error(
            `${metadataPath} operation entry missing method/path: ${JSON.stringify(op)}`
          );
        }
        if (typeof op.sourceFile !== 'string' || op.sourceFile === '') {
          throw new Error(
            `${metadataPath} operation ${op.method.toUpperCase()} ${op.path} ` +
            `has no sourceFile attribution.`
          );
        }
        return [`${op.method.toUpperCase()} ${op.path}`, op.sourceFile];
      })
      // Sort by "METHOD /path" so the output is deterministic regardless of
      // the operation order chosen by the bundler.
      .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0));
    const endpointMap = Object.fromEntries(entries);
    // Unsuffixed, single file: correct as long as MAIN_BRANCH_VERSIONS has
    // exactly one entry, which is the only configuration ever used in this
    // repo today. If MAIN_BRANCH_VERSIONS ever grows to more than one entry,
    // this silently overwrites the file on each loop iteration, keeping only
    // the last version's endpoint-map — revisit then, e.g. by suffixing the
    // filename per version.
    const endpointMapPath =
      `${OUTPUT_PATH}/endpoint-map.json`;
    mkdirSync(OUTPUT_PATH, { recursive: true });
    writeFileSync(endpointMapPath, JSON.stringify(endpointMap, null, 2) + '\n');
    console.log(
      `Derived ${endpointMapPath} (${entries.length} operations from ${version})`
    );
  }

  // When REGENERATE_LATEST_SPEC_ONLY is set, also wipe every MAIN_BRANCH_VERSIONS
  // cache dir after the run. Those refs (e.g. `main`) are mutable, so a stale
  // cache would silently mask upstream changes on the next plain invocation.
  // Stable refs (`stable/<v>`) are immutable and stay cached.
  if (parseBool(process.env.REGENERATE_LATEST_SPEC_ONLY)) {
    for (const version of MAIN_BRANCH_VERSIONS) {
      const dir = `${BUNDLER_SPECS_DIR}/${version}`;
      if (existsSync(dir)) {
        console.log(`REGENERATE_LATEST_SPEC_ONLY=1 — clearing ${dir} (post-run)`);
        rmSync(dir, { recursive: true, force: true });
      }
    }
  }

  // Backwards-compat collapse: see backwards-compat-collapse.mjs. Runs before
  // the sibling-variant collapse; the order is part of the contract tested in
  // test/collapse-pipeline.test.mjs.
  collapseBackwardsCompat(versionMap);

  // Sibling-variant collapse: a property repeated on sibling oneOf/anyOf
  // branches of the same request or response, with an identical schema, gets
  // the earliest version among them, even without a tagless ancestor. See
  // sibling-variant-collapse.mjs and camunda/camunda#67987.
  {
    const { collapsed, skipped } = collapseSiblingVariants(versionMap, latestSpec);
    for (const c of collapsed) {
      console.log(`  sibling-variant collapse: ${c.propKey}  ${c.from} -> ${c.to}`);
    }
    console.log(
      `  sibling-variant collapse: ${collapsed.length} collapsed, ` +
      `${skipped.length} groups kept apart because their schemas differ`,
    );
  }

  // ─── Summary ───────────────────────────────────────────────────────────────

  console.log('');

  const opsByVersion = {};
  for (const val of Object.values(versionMap.operations)) {
    opsByVersion[val.version] = (opsByVersion[val.version] || 0) + 1;
  }
  console.log('Operations by version:');
  for (const [v, count] of Object.entries(opsByVersion).sort()) {
    console.log(`  ${v}: ${count} operations`);
  }

  const propsByVersion = {};
  for (const val of Object.values(versionMap.properties)) {
    propsByVersion[val.version] = (propsByVersion[val.version] || 0) + 1;
  }
  console.log('\nProperties by version:');
  for (const [v, count] of Object.entries(propsByVersion).sort()) {
    console.log(`  ${v}: ${count} properties`);
  }

  // Build `children` arrays: each property gets the list of property keys that
  // are one level deeper in its qualifiedName tree (sharing the same operation
  // and location). E.g. `... > filter` -> [`... > filter.state`, `... > filter.assignee`, ...].
  // A segment delimiter is `.` for object nesting or `[].` for arrays of objects.
  for (const val of Object.values(versionMap.properties)) {
    val.children = [];
  }
  for (const [propKey, val] of Object.entries(versionMap.properties)) {
    const q = val.qualifiedName;
    const lastDot = q.lastIndexOf('.');
    if (lastDot < 0) continue;
    let parentQ = q.slice(lastDot - 2, lastDot) === '[]'
      ? q.slice(0, lastDot - 2)
      : q.slice(0, lastDot);
    // The parent qname may carry a trailing oneOf/anyOf branch tag introduced
    // by extractProperties (e.g. `page|OffsetPagination`, `filter.state|AdvancedStringFilter`).
    // Branch tags are not emitted as their own property entries — the logical
    // parent is the segment before the FIRST `|` after the last `.`.
    const dotInParent = parentQ.lastIndexOf('.');
    const pipeInParent = parentQ.indexOf('|', dotInParent + 1);
    if (pipeInParent !== -1) {
      parentQ = parentQ.slice(0, pipeInParent);
      // After dropping the branch tag we may still be left with a trailing
      // `[]` array marker (e.g. `runtimeInstructions[]|TerminateInstruction`
      // -> `runtimeInstructions[]`). The actual stored parent entry omits the
      // marker, so strip it.
      if (parentQ.endsWith('[]')) parentQ = parentQ.slice(0, -2);
    }
    const parentKey = `${val.endpoint} > ${val.location} > ${parentQ}`;
    if (versionMap.properties[parentKey]) {
      versionMap.properties[parentKey].children.push(propKey);
    }
  }

  // Write output
  mkdirSync(OUTPUT_PATH, { recursive: true });
  const versionMapPath = `${OUTPUT_PATH}/bundler-version-map.json`;
  writeFileSync(versionMapPath, JSON.stringify(versionMap, null, 2));
  console.log(`\nVersion map written to ${versionMapPath}`);
  console.log(`  ${Object.keys(versionMap.operations).length} operations`);
  console.log(`  ${Object.keys(versionMap.properties).length} properties`);
  console.log(`  ${Object.keys(versionMap.deletedOperations).length} deleted operations`);
  console.log(`  ${Object.keys(versionMap.deletedProperties).length} deleted properties`);
}

main().catch((err) => {
  console.error('Fatal error:', err);
  process.exit(1);
});
