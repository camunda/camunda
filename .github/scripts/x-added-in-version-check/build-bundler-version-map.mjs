/**
 * build-bundler-version-map.mjs
 *
 * Uses camunda-schema-bundler to fetch and bundle OpenAPI specs for versions
 * 8.5–8.10 directly from the Camunda GitHub repo, then builds a unified
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
import { fetchAndBundle } from 'camunda-schema-bundler';
import YAML from 'yaml';

const yaml = { load: (source) => YAML.parse(source) };

// ─── Configuration (env-overridable) ───────────────────────────────────────────────
//
//   VERSIONS                Comma-separated list (default: 8.5,8.6,8.7,8.8,8.9,8.10)
//   MAIN_BRANCH_VERSIONS    Versions that track the `main` branch instead of
//                           a stable/<v> branch (default: 8.10)
//   LATEST_BRANCH           Optional ref to try first for every
//                           MAIN_BRANCH_VERSIONS entry. Falls back to `main`
//                           if the fetch fails (e.g. branch doesn't exist).
//                           Stable versions ignore this and always use
//                           `stable/<version>`.
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
const VERSIONS = parseCsv(process.env.VERSIONS, ['8.5', '8.6', '8.7', '8.8', '8.9', '8.10']);
const MAIN_BRANCH_VERSIONS = parseCsv(process.env.MAIN_BRANCH_VERSIONS, ['8.10']);
// 8.10 (or whatever MAIN_BRANCH_VERSIONS lists) tracks `main` until it cuts
// its own release branch. Everything else has a stable/<version> branch on
// camunda/camunda. When LATEST_BRANCH is set, it overrides `main` as the
// preferred ref for MAIN_BRANCH_VERSIONS, with `main` as the fallback.
const LATEST_BRANCH = process.env.LATEST_BRANCH || null;
const VERSION_REFS = Object.fromEntries(
  MAIN_BRANCH_VERSIONS.map((v) => [v, LATEST_BRANCH ?? 'main']),
);
const BUNDLER_SPECS_DIR = process.env.BUNDLER_SPECS_DIR ?? 'bundler-specs';
const OUTPUT_PATH = process.env.OUTPUT_PATH ?? 'output';
const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete', 'head', 'options'];

// ─── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Resolve a $ref pointer like "#/components/schemas/Foo" within the spec.
 */
function resolveRef(spec, ref, visited = new Set()) {
  if (!ref || !ref.startsWith('#/')) return null;
  if (visited.has(ref)) return null;
  visited.add(ref);
  const parts = ref.slice(2).split('/').map((p) => p.replace(/~1/g, '/').replace(/~0/g, '~'));
  let current = spec;
  for (const p of parts) {
    if (!current || typeof current !== 'object') return null;
    // Follow intermediate $refs. The bundler produces refs like
    // `#/paths/.../foo/properties/bar` whose traversal passes through a
    // node that is itself `{ $ref: "#/components/schemas/X" }` — without
    // dereferencing those boundaries we'd return null.
    if (current.$ref && typeof current.$ref === 'string') {
      const next = resolveRef(spec, current.$ref, visited);
      if (!next) return null;
      current = next;
    }
    current = current[p];
  }
  return current || null;
}

/**
 * Resolve a schema, following $ref and unwrapping single-element allOf.
 */
function resolveSchema(spec, schema) {
  if (!schema) return null;
  if (schema.$ref) {
    return resolveSchema(spec, resolveRef(spec, schema.$ref));
  }
  if (schema.allOf && schema.allOf.length === 1) {
    return resolveSchema(spec, schema.allOf[0]);
  }
  return schema;
}

/**
 * Convert a $ref string like "#/components/schemas/Foo" to a path array
 * like ["components", "schemas", "Foo"]. Decodes RFC 6901 JSON Pointer
 * escapes (`~1` -> `/`, `~0` -> `~`) which appear in bundler-generated
 * refs into `#/paths/...` segments.
 */
function refToPath(ref) {
  if (!ref || !ref.startsWith('#/')) return null;
  return ref.slice(2).split('/').map((p) => p.replace(/~1/g, '/').replace(/~0/g, '~'));
}

/**
 * Extract property names from a schema along with their depth and schema path.
 * Handles $ref, allOf (any length), and sibling properties correctly.
 *
 * Returns Map<qualifiedName, { name, depth, path }> where `qualifiedName`
 * reflects the access chain from the operation's root schema, e.g.
 *   "tenants[].name"   for a `name` field inside an array of objects
 *   "user.address.zip" for a `zip` field nested two objects deep
 * `name` is the leaf property name. The map key is the qualified name so
 * nested properties with colliding leaf names (e.g. multiple `name` fields)
 * are recorded independently rather than overwriting each other.
 */
function extractProperties(
  spec,
  schema,
  depth = 0,
  maxDepth = 3,
  basePath = [],
  parentChain = '',
  visitedRefs = new Set(),
) {
  const props = new Map();
  if (!schema) return props;

  // Follow $ref — but merge with any sibling properties too. Guard against
  // recursive schemas (e.g. tree-shaped types) by tracking visited refs.
  if (schema.$ref) {
    if (visitedRefs.has(schema.$ref)) return props;
    const nextVisited = new Set(visitedRefs);
    nextVisited.add(schema.$ref);
    const refTarget = resolveRef(spec, schema.$ref);
    const refPath = refToPath(schema.$ref);
    const refProps = extractProperties(
      spec, refTarget, depth, maxDepth, refPath || basePath, parentChain, nextVisited,
    );
    for (const [qname, info] of refProps) {
      if (!props.has(qname)) props.set(qname, info);
    }
  }

  if (schema.properties) {
    for (const [name, propSchema] of Object.entries(schema.properties)) {
      const resolvedProp = resolveSchema(spec, propSchema);
      const type = resolvedProp?.type || (resolvedProp?.enum ? 'enum' : 'object');
      const qname = parentChain ? `${parentChain}.${name}` : name;
      if (!props.has(qname)) {
        // Property is explicitly named here — always use the inline path
        props.set(qname, { name, depth, path: [...basePath, 'properties', name] });
      }

      // Recurse into nested objects (but not too deep)
      if (depth < maxDepth && (type === 'object' || type === 'array')) {
        // The schema in front of us may be a direct $ref OR a single-element
        // `allOf: [{ $ref }]` wrapper (the Camunda convention for attaching
        // a description/x-added-in-version to a referenced schema). Both
        // forms must rebase `innerBasePath` onto the referenced schema's
        // location — otherwise nested properties get recorded under the
        // wrapper's path, which doesn't exist in the upstream YAML.
        const effectiveRef =
          propSchema.$ref
          || (Array.isArray(propSchema.allOf)
              && propSchema.allOf.length === 1
              && typeof propSchema.allOf[0]?.$ref === 'string'
              ? propSchema.allOf[0].$ref
              : null);
        let innerSchema = effectiveRef ? resolveRef(spec, effectiveRef) : resolvedProp;
        let innerBasePath = effectiveRef
          ? (refToPath(effectiveRef) || [...basePath, 'properties', name])
          : [...basePath, 'properties', name];
        let childChain = qname;
        let childVisited = visitedRefs;
        if (effectiveRef) {
          if (visitedRefs.has(effectiveRef)) continue;
          childVisited = new Set(visitedRefs);
          childVisited.add(effectiveRef);
        }
        if (type === 'array') {
          const arraySchema = effectiveRef ? resolveRef(spec, effectiveRef) : resolvedProp;
          innerSchema = arraySchema?.items || resolvedProp?.items;
          // Items may themselves be a $ref or an `allOf: [{ $ref }]` wrapper.
          const itemsRef =
            innerSchema?.$ref
            || (Array.isArray(innerSchema?.allOf)
                && innerSchema.allOf.length === 1
                && typeof innerSchema.allOf[0]?.$ref === 'string'
                ? innerSchema.allOf[0].$ref
                : null);
          if (itemsRef) {
            if (childVisited.has(itemsRef)) continue;
            childVisited = new Set(childVisited);
            childVisited.add(itemsRef);
            innerBasePath = refToPath(itemsRef) || innerBasePath;
            innerSchema = resolveRef(spec, itemsRef) || innerSchema;
          } else {
            innerBasePath = [...innerBasePath, 'items'];
          }
          childChain = `${qname}[]`;
        }
        if (!innerSchema) continue;
        const nested = extractProperties(
          spec, innerSchema, depth + 1, maxDepth, innerBasePath, childChain, childVisited,
        );
        for (const [nestedQname, nestedInfo] of nested) {
          if (!props.has(nestedQname)) {
            props.set(nestedQname, nestedInfo);
          }
        }
      }
    }
  }

  // allOf composition — handle any length (including 1 with sibling properties)
  if (schema.allOf) {
    for (let i = 0; i < schema.allOf.length; i++) {
      const sub = schema.allOf[i];
      const subBasePath = sub.$ref ? (refToPath(sub.$ref) || basePath) : [...basePath, 'allOf', String(i)];
      const subProps = extractProperties(
        spec, sub, depth, maxDepth, subBasePath, parentChain, visitedRefs,
      );
      for (const [qname, info] of subProps) {
        if (!props.has(qname)) {
          props.set(qname, info);
        }
      }
    }
  }

  // oneOf / anyOf — merge properties from all branches.
  //
  // Unlike `allOf` (which is intersection/composition: any property name
  // legitimately lives in exactly one physical schema along the chain),
  // `oneOf`/`anyOf` are independent alternative shapes. Sibling branches
  // routinely declare the same leaf property name (the discriminator
  // pattern in particular), and each branch is its own schema in the YAML
  // that needs its own `x-properties-added-in-version` annotation.
  //
  // Without disambiguation, first-wins on `qualifiedName` would silently
  // drop every branch after the first and the downstream writer would
  // annotate only one variant. Tag the qualified name with the branch's
  // referenced schema name (or the index, for inline branches) so each
  // branch gets its own entry with its own path.
  for (const keyword of ['oneOf', 'anyOf']) {
    if (!schema[keyword]) continue;
    for (let i = 0; i < schema[keyword].length; i++) {
      const sub = schema[keyword][i];
      const subBasePath = sub.$ref
        ? (refToPath(sub.$ref) || basePath)
        : [...basePath, keyword, String(i)];
      const branchTag = sub.$ref
        ? sub.$ref.split('/').pop()
        : `${keyword}[${i}]`;
      const branchChain = parentChain
        ? `${parentChain}|${branchTag}`
        : branchTag;
      const subProps = extractProperties(
        spec, sub, depth, maxDepth, subBasePath, branchChain, visitedRefs,
      );
      for (const [qname, info] of subProps) {
        if (!props.has(qname)) {
          props.set(qname, info);
        }
      }
    }
  }

  return props;
}

/**
 * Extract request body properties for an operation.
 */
function getRequestProperties(spec, operation, operationBasePath, upstreamRequestRef) {
  const body = operation.requestBody;
  if (!body) return new Map();
  const contentType = body.content?.['application/json'] ? 'application/json' : 'multipart/form-data';
  const content = body.content?.[contentType];
  if (!content?.schema) return new Map();
  // Use the bundled spec's $ref first; fall back to the upstream ref when
  // the bundler inlined the schema (losing the original $ref).
  const ref = content.schema.$ref || upstreamRequestRef;
  const schemaBasePath = ref
    ? (refToPath(ref) || [...operationBasePath, 'requestBody', 'content', contentType, 'schema'])
    : [...operationBasePath, 'requestBody', 'content', contentType, 'schema'];
  return extractProperties(spec, content.schema, 0, 3, schemaBasePath);
}

/**
 * Extract response properties for an operation (from the success response).
 */
function getResponseProperties(spec, operation, operationBasePath, upstreamResponseRef) {
  const responses = operation.responses;
  if (!responses) return new Map();

  const successCode = Object.keys(responses).find(
    (code) => code === '200' || code === '201' || code === '204'
  ) || Object.keys(responses).find((code) => code.startsWith('2'));

  if (!successCode) return new Map();
  const response = responses[successCode];
  const content = response?.content?.['application/json'];
  if (!content?.schema) return new Map();
  // Use the bundled spec's $ref first; fall back to the upstream ref when
  // the bundler inlined the schema (losing the original $ref).
  const ref = content.schema.$ref || upstreamResponseRef;
  const schemaBasePath = ref
    ? (refToPath(ref) || [...operationBasePath, 'responses', successCode, 'content', 'application/json', 'schema'])
    : [...operationBasePath, 'responses', successCode, 'content', 'application/json', 'schema'];
  return extractProperties(spec, content.schema, 0, 3, schemaBasePath);
}

/**
 * Given a path like ["components", "schemas", "Foo", ...], look up Foo's
 * source file in schemaFileMap. Returns the filename or null.
 */
function getSchemaFileForPath(pathArr, schemaFileMap) {
  if (!schemaFileMap || !pathArr) return null;
  if (pathArr[0] === 'components' && pathArr[1] === 'schemas' && pathArr[2]) {
    return schemaFileMap.get(pathArr[2]) || null;
  }
  return null;
}

/**
 * Extract all operations and their properties from a spec object.
 * When schemaFileMap and operationFileMap are provided, cross-file property
 * paths are prefixed with the source filename.
 * operationSchemaRefMap: Map<opKey, { requestRef, responseRef }> — original
 * $ref strings from the upstream YAML files, used as fallback when the
 * bundler inlined schemas.
 */
function extractSpecData(spec, operationFileMap, schemaFileMap, operationSchemaRefMap) {
  const operations = new Map();
  const properties = new Map();

  if (!spec?.paths) return { operations, properties };

  for (const [path, pathItem] of Object.entries(spec.paths)) {
    for (const method of HTTP_METHODS) {
      const operation = pathItem[method];
      if (!operation) continue;

      const opKey = `${method.toUpperCase()} ${path}`;
      const opSourceFile = operationFileMap?.get(opKey) || null;
      const operationBasePath = opSourceFile
        ? [opSourceFile, 'paths', path, method]
        : ['paths', path, method];
      operations.set(opKey, {
        summary: operation.summary || operation.description || '',
        operationId: operation.operationId || '',
        path: operationBasePath,
      });

      const upstreamRefs = operationSchemaRefMap?.get(opKey);
      const reqProps = getRequestProperties(spec, operation, operationBasePath, upstreamRefs?.requestRef);
      for (const [qualifiedName, propInfo] of reqProps) {
        const propKey = `${opKey} > request > ${qualifiedName}`;
        const schemaFile = getSchemaFileForPath(propInfo.path, schemaFileMap);
        const finalPath = (schemaFile && schemaFile !== opSourceFile)
          ? [schemaFile, ...propInfo.path]
          : propInfo.path;
        properties.set(propKey, {
          location: 'request',
          endpoint: opKey,
          property: propInfo.name,
          qualifiedName,
          depth: propInfo.depth,
          path: finalPath,
        });
      }

      const resProps = getResponseProperties(spec, operation, operationBasePath, upstreamRefs?.responseRef);
      for (const [qualifiedName, propInfo] of resProps) {
        const propKey = `${opKey} > response > ${qualifiedName}`;
        const schemaFile = getSchemaFileForPath(propInfo.path, schemaFileMap);
        const finalPath = (schemaFile && schemaFile !== opSourceFile)
          ? [schemaFile, ...propInfo.path]
          : propInfo.path;
        properties.set(propKey, {
          location: 'response',
          endpoint: opKey,
          property: propInfo.name,
          qualifiedName,
          depth: propInfo.depth,
          path: finalPath,
        });
      }
    }
  }

  return { operations, properties };
}

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

  for (const version of VERSIONS) {
    const ref = VERSION_REFS[version] ?? `stable/${version}`;
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
              outputDir,
              outputSpec,
              outputMetadata,
              allowPathLocalLikeRefs: true,
              restoreUpstreamOperationRefs: true
            });
          } catch (fallbackErr) {
            console.error(`  ERROR bundling ${version} (fallback to main): ${fallbackErr.message}`);
            continue;
          }
        } else {
          console.error(`  ERROR bundling ${version}: ${err.message}`);
          continue;
        }
      }
    }

    const spec = result.spec;
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

    // Record operations first seen in this version (and refresh path attribution)
    let newOps = 0;
    for (const [opKey, opData] of operations) {
      if (!versionMap.operations[opKey]) {
        versionMap.operations[opKey] = {
          version,
          summary: opData.summary,
          path: opData.path,
        };
        newOps++;
      } else {
        // Keep the original first-seen version, but refresh the path to the
        // most recent attribution (later versions split into per-resource files).
        versionMap.operations[opKey].path = opData.path;
        // Backfill summary if it was missing in the version where it was first seen.
        if (!versionMap.operations[opKey].summary && opData.summary) {
          versionMap.operations[opKey].summary = opData.summary;
        }
      }
    }

    // Record properties first seen in this version (and refresh path attribution)
    let newProps = 0;
    for (const [propKey, propData] of properties) {
      if (!versionMap.properties[propKey]) {
        versionMap.properties[propKey] = {
          version,
          ...propData,
        };
        newProps++;
      } else {
        // Preserve the first-seen version; update path/depth to current version's.
        const existing = versionMap.properties[propKey];
        versionMap.properties[propKey] = {
          ...existing,
          location: propData.location,
          endpoint: propData.endpoint,
          property: propData.property,
          qualifiedName: propData.qualifiedName,
          depth: propData.depth,
          path: propData.path,
        };
      }
    }

    // Detect deleted operations
    let deletedOps = 0;
    if (previousOps) {
      for (const [opKey, opData] of previousOps) {
        if (!operations.has(opKey) && !versionMap.deletedOperations[opKey]) {
          versionMap.deletedOperations[opKey] = {
            removedIn: version,
            summary: opData.summary,
          };
          // Strip the stale `path` from the surviving entry in `operations`.
          // Its value was last refreshed in the version where the op still
          // existed, which differs between pipelines depending on whether
          // that version's upstream/ dir was populated. The op stays listed
          // so downstream consumers still see when it was introduced.
          if (versionMap.operations[opKey]) {
            delete versionMap.operations[opKey].path;
          }
          deletedOps++;
        }
      }
    }

    // Detect deleted properties
    let deletedProps = 0;
    if (previousProps) {
      // A property may legitimately migrate from an inline position to inside a
      // deeper *container* across versions (e.g. `processDefinitionVersion` moved
      // from the request body root in 8.7 into `ProcessInstanceCreationInstructionById`
      // in 8.8). Container migrations keep the qname tagless, so treat the field
      // as still present only when the surviving match also lives in a tagless
      // qname.
      //
      // Relocations into a `oneOf`/`anyOf` branch (qname carries a `|BranchTag`
      // segment) are NOT treated as still-present: the parent schema genuinely
      // stopped declaring the property, and downstream consumers need to see the
      // entry in `deletedProperties` so they can suppress annotations on the
      // pre-split shape. The branch-tagged entry remains in `properties` with
      // its own `version` reflecting when the branch first declared it.
      const currentTaglessLeafs = new Set();
      for (const propData of properties.values()) {
        if (propData.qualifiedName && propData.qualifiedName.includes('|')) continue;
        currentTaglessLeafs.add(`${propData.endpoint}|||${propData.location}|||${propData.property}`);
      }
      for (const [propKey, propData] of previousProps) {
        if (properties.has(propKey)) continue;
        if (versionMap.deletedProperties[propKey]) continue;
        const leafKey = `${propData.endpoint}|||${propData.location}|||${propData.property}`;
        if (currentTaglessLeafs.has(leafKey)) continue;
        versionMap.deletedProperties[propKey] = {
          removedIn: version,
          endpoint: propData.endpoint,
        };
        // Strip the stale `path` from the surviving entry in `properties`,
        // mirroring the deletedOperations treatment.
        if (versionMap.properties[propKey]) {
          delete versionMap.properties[propKey].path;
        }
        deletedProps++;
      }
    }

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
    // One file per MAIN_BRANCH_VERSIONS entry so multi-version runs don't
    // silently overwrite each other. The cross-version `bundler-version-map.json`
    // stays unsuffixed because there is exactly one of it.
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

  // Backwards-compat collapse: when a property re-appears in a later version
  // on a different schema branch (oneOf split, sibling reintroduction for
  // backwards-compatibility), branch-tagging gives every occurrence its own
  // entry. Collapse to the MIN version across the (endpoint, location,
  // property) triple ONLY when a fully-tagless ancestor exists — i.e. the
  // property had a pre-polymorphic-split flat form. Without a tagless
  // ancestor (e.g. discriminator-wrapper child re-declaring the discriminator),
  // each branch keeps its own version because it represents a genuinely-new
  // YAML location.
  {
    const cmp = (a, b) => {
      const [aMaj, aMin] = a.split('.').map(Number);
      const [bMaj, bMin] = b.split('.').map(Number);
      return aMaj - bMaj || aMin - bMin;
    };
    const hasLeadingBranch = (qname) => /^[A-Z][^.|]*\./.test(qname);
    const isTagless = (qname) => !qname.includes('|') && !hasLeadingBranch(qname);
    const normalizeQname = (qname) =>
      qname.replace(/\|[^.]+/g, '').replace(/^[A-Z][^.|]*\./, '');
    const byTriple = new Map();
    for (const val of Object.values(versionMap.properties)) {
      const k = `${val.endpoint}|||${val.location}|||${normalizeQname(val.qualifiedName)}`;
      let bucket = byTriple.get(k);
      if (!bucket) {
        bucket = { hasTagless: false, min: val.version, entries: [] };
        byTriple.set(k, bucket);
      }
      bucket.entries.push(val);
      if (isTagless(val.qualifiedName)) bucket.hasTagless = true;
      if (cmp(val.version, bucket.min) < 0) bucket.min = val.version;
    }
    for (const bucket of byTriple.values()) {
      if (!bucket.hasTagless) continue;
      for (const val of bucket.entries) {
        if (cmp(bucket.min, val.version) < 0) val.version = bucket.min;
      }
    }
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
