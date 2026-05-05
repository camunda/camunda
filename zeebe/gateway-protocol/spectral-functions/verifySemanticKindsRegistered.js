// Spectral custom function. Three responsibilities:
//
//   1. Every `kind:` value referenced by `x-semantic-establishes` or
//      `x-semantic-requires` must appear in `semantic-kinds.json`. Catches
//      typos at the point of declaration (the registry is the spell-check
//      dictionary).
//
//   2. Cross-reference: every `x-semantic-requires.kind` must be established
//      by at least one operation in the spec. Catches the case where the
//      consumer was annotated but the producer wasn't (or producer uses a
//      different — possibly typo'd — kind).
//
//   3. Edge endpoint resolution + derived requires. For every operation with
//      `x-semantic-establishes.shape: edge`, each `identifiedBy[].semanticType`
//      must be the identifier of exactly one registered entity kind (the
//      registry's `identifiers: [...]` field). The resolved entity kinds are
//      then treated as implicit `x-semantic-requires` for that operation and
//      fed through the cross-reference check above. Authors get the
//      validation ("you can't link to an entity nobody can produce") without
//      having to redundantly write `x-semantic-requires` next to every edge
//      establishment.
//
// The orphan check in the other direction (`establishes` with no consumer)
// is intentionally not enforced — many producers are legitimately useful
// without a consumer in this spec (e.g. setup/admin operations).
//
// Applied to the document root (`given: $`).
//
// The registry is JSON (not YAML) so it can be read with the built-in
// JSON parser without taking on a runtime dependency on js-yaml.
//
// Tests override the registry path via the
// `SPECTRAL_SEMANTIC_KINDS_REGISTRY` environment variable.

'use strict';

const fs = require('node:fs');
const path = require('node:path');

const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete']);

const REGISTRY_CANDIDATES = [
  // CI invokes spectral from the repo root.
  'zeebe/gateway-protocol/src/main/proto/v2/semantic-kinds.json',
  // Local invocations from inside zeebe/gateway-protocol.
  'src/main/proto/v2/semantic-kinds.json',
];

// Tests override the registry path with this env var so they can drive
// the function with fixture-local kinds without polluting the production
// registry. Spectral's function sandbox doesn't expose __dirname, so we
// can't anchor the lookup to the function file — env var first, then
// cwd-relative candidates.
function resolveRegistryPath() {
  const override = process.env.SPECTRAL_SEMANTIC_KINDS_REGISTRY;
  if (typeof override === 'string' && override.length > 0) {
    return path.resolve(override);
  }
  for (const candidate of REGISTRY_CANDIDATES) {
    const abs = path.resolve(process.cwd(), candidate);
    if (fs.existsSync(abs)) return abs;
  }
  // Last resort — let fs.readFileSync surface a clear ENOENT pointing at
  // the canonical location.
  return path.resolve(process.cwd(), REGISTRY_CANDIDATES[0]);
}

let cachedRegistry = null;
let cachedFor = null;
function loadRegistry() {
  const file = resolveRegistryPath();
  if (cachedRegistry !== null && cachedFor === file) return cachedRegistry;
  const raw = fs.readFileSync(file, 'utf8');
  const doc = JSON.parse(raw);
  const kinds = Array.isArray(doc.kinds) ? doc.kinds : [];
  const names = new Set();
  // semanticType (string) -> array of entity kind names that declare it as an
  // identifier. Single-owner is the well-formed case; multi-owner is a
  // registry config error and surfaces as an error against the first edge
  // operation that triggers the lookup.
  const identifierToKinds = new Map();
  for (const entry of kinds) {
    const name = typeof entry === 'string' ? entry : entry?.name;
    if (typeof name !== 'string' || name.length === 0) continue;
    names.add(name);
    if (entry && typeof entry === 'object' && Array.isArray(entry.identifiers)) {
      for (const id of entry.identifiers) {
        if (typeof id !== 'string' || id.length === 0) continue;
        const existing = identifierToKinds.get(id);
        if (existing) existing.push(name);
        else identifierToKinds.set(id, [name]);
      }
    }
  }
  cachedRegistry = { names, identifierToKinds };
  cachedFor = file;
  return cachedRegistry;
}

module.exports = (input, _opts, _context) => {
  const errors = [];
  const { names: registry, identifierToKinds } = loadRegistry();

  const paths = input?.paths;
  if (!paths || typeof paths !== 'object') return errors;

  const established = new Set();
  // { kind, method, pathKey, source: 'requires' | 'edge-endpoint',
  //   semanticType?: string }
  const required = [];

  for (const [pathKey, pathItem] of Object.entries(paths)) {
    if (!pathItem || typeof pathItem !== 'object') continue;
    for (const [method, op] of Object.entries(pathItem)) {
      if (!HTTP_METHODS.has(method) || !op || typeof op !== 'object') continue;

      const est = op['x-semantic-establishes'];
      if (est && typeof est.kind === 'string') {
        established.add(est.kind);
        if (!registry.has(est.kind)) {
          errors.push({
            message: `Unknown semantic kind '${est.kind}' on ${method.toUpperCase()} ${pathKey} (x-semantic-establishes). Add it to zeebe/gateway-protocol/src/main/proto/v2/semantic-kinds.json or fix the typo.`,
            path: ['paths', pathKey, method, 'x-semantic-establishes', 'kind'],
          });
        }

        // Edge endpoint resolution. For shape: edge, every identifiedBy entry's
        // semanticType must resolve to exactly one registered entity. The
        // resolved entity becomes an implicit requires.
        if (est.shape === 'edge' && Array.isArray(est.identifiedBy)) {
          for (let i = 0; i < est.identifiedBy.length; i++) {
            const item = est.identifiedBy[i];
            if (!item || typeof item !== 'object') continue;
            const st = item.semanticType;
            if (typeof st !== 'string' || st.length === 0) continue;
            const owners = identifierToKinds.get(st);
            if (!owners || owners.length === 0) {
              errors.push({
                message: `Edge endpoint semanticType '${st}' on ${method.toUpperCase()} ${pathKey} (x-semantic-establishes.identifiedBy[${i}]) is not declared as an identifier of any registered entity kind. Add it to the entity's 'identifiers' in semantic-kinds.json or fix the typo.`,
                path: [
                  'paths',
                  pathKey,
                  method,
                  'x-semantic-establishes',
                  'identifiedBy',
                  i,
                  'semanticType',
                ],
              });
              continue;
            }
            if (owners.length > 1) {
              errors.push({
                message: `Edge endpoint semanticType '${st}' on ${method.toUpperCase()} ${pathKey} resolves to multiple entity kinds in semantic-kinds.json: ${owners.join(', ')}. Each semantic identifier must be owned by exactly one entity.`,
                path: [
                  'paths',
                  pathKey,
                  method,
                  'x-semantic-establishes',
                  'identifiedBy',
                  i,
                  'semanticType',
                ],
              });
            }
            for (const ownerKind of owners) {
              required.push({
                kind: ownerKind,
                method,
                pathKey,
                source: 'edge-endpoint',
                semanticType: st,
              });
            }
          }
        }
      }

      const req = op['x-semantic-requires'];
      if (req && typeof req.kind === 'string') {
        required.push({
          kind: req.kind,
          method,
          pathKey,
          source: 'requires',
        });
        if (!registry.has(req.kind)) {
          errors.push({
            message: `Unknown semantic kind '${req.kind}' on ${method.toUpperCase()} ${pathKey} (x-semantic-requires). Add it to zeebe/gateway-protocol/src/main/proto/v2/semantic-kinds.json or fix the typo.`,
            path: ['paths', pathKey, method, 'x-semantic-requires', 'kind'],
          });
        }
      }
    }
  }

  // Cross-reference: every required kind must be established somewhere.
  // Skip kinds that already failed the registry check — the message above
  // is the actionable one.
  //
  // Only meaningful against a complete spec (the bundled rest-api.yaml root
  // with `openapi:` at top level). CI runs spectral twice — once on the
  // bundled root and once with a per-file glob (see camunda/camunda#46274).
  // In the per-file pass, sub-files like tenants.yaml only carry their own
  // paths, so an edge whose producer lives in a sibling file (e.g.
  // assignUserToTenant deriving User from Username, with createUser in
  // users.yaml) would falsely fail here. Sub-files have no `openapi` at
  // root — gate the cross-reference walk on its presence so the bundled
  // pass is the single source of truth for this check, while the
  // per-operation registry-membership checks above still fire correctly
  // per-file.
  if (typeof input?.openapi !== 'string') return errors;

  for (const r of required) {
    if (!registry.has(r.kind)) continue;
    if (established.has(r.kind)) continue;
    if (r.source === 'edge-endpoint') {
      errors.push({
        message: `Semantic kind '${r.kind}' is required (derived from x-semantic-establishes.identifiedBy.semanticType '${r.semanticType}' on ${r.method.toUpperCase()} ${r.pathKey}) but no operation establishes it. Add x-semantic-establishes on the producer operation, or remove '${r.semanticType}' from the entity's 'identifiers' if it is not actually an endpoint of this edge.`,
        path: [
          'paths',
          r.pathKey,
          r.method,
          'x-semantic-establishes',
          'identifiedBy',
        ],
      });
    } else {
      errors.push({
        message: `Semantic kind '${r.kind}' is required by ${r.method.toUpperCase()} ${r.pathKey} (x-semantic-requires) but no operation establishes it. Add x-semantic-establishes on the producer operation.`,
        path: ['paths', r.pathKey, r.method, 'x-semantic-requires', 'kind'],
      });
    }
  }

  return errors;
};
