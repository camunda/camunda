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
//   Special case for `shape: external-entity` registry entries (camunda/camunda#52320):
//   the producer-existence check is skipped, and any operation that tries
//   to `establishes` or `requires` an external entity directly is rejected
//   — external entities are *referenced* through edge `identifiedBy`
//   tuples only.
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
  // Names of kinds whose `shape` is `external-entity`. Such entities are
  // referenced via edge `identifiedBy` tuples only — the cross-reference
  // check skips them, and the operation-walk below rejects any direct
  // `establishes`/`requires` against them.
  const externalNames = new Set();
  // semanticType (string) -> array of entity kind names that declare it as an
  // identifier. Single-owner is the well-formed case; multi-owner is a
  // registry config error and surfaces as an error against the first edge
  // operation that triggers the lookup.
  const identifierToKinds = new Map();
  for (const entry of kinds) {
    const name = typeof entry === 'string' ? entry : entry?.name;
    if (typeof name !== 'string' || name.length === 0) continue;
    names.add(name);
    if (entry && typeof entry === 'object') {
      if (entry.shape === 'external-entity') externalNames.add(name);
      if (Array.isArray(entry.identifiers)) {
        for (const id of entry.identifiers) {
          if (typeof id !== 'string' || id.length === 0) continue;
          const existing = identifierToKinds.get(id);
          if (existing) existing.push(name);
          else identifierToKinds.set(id, [name]);
        }
      }
    }
  }
  cachedRegistry = { names, identifierToKinds, externalNames };
  cachedFor = file;
  return cachedRegistry;
}

module.exports = (input, _opts, _context) => {
  const errors = [];
  const { names: registry, identifierToKinds, externalNames } = loadRegistry();

  // The CI runs spectral twice: once on the bundled entry point
  // (rest-api.yaml) and once per-file across the glob. The cross-reference
  // walk below relies on every producer op being visible in `paths` — true
  // for the bundled pass, false for the per-file pass (e.g. tenants.yaml
  // has edges binding User, but createUser lives in users.yaml). Run only
  // on documents that look like a complete OpenAPI root — fragment files
  // omit the `openapi:` field.
  if (typeof input?.openapi !== 'string') return errors;

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
        } else if (externalNames.has(est.kind)) {
          errors.push({
            message: `Semantic kind '${est.kind}' is registered as 'shape: external-entity' and cannot be established. External entities are minted outside the Camunda REST API and may only be referenced via membership-edge identifiedBy tuples.`,
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
        } else if (externalNames.has(req.kind)) {
          errors.push({
            message: `Semantic kind '${req.kind}' is registered as 'shape: external-entity' and cannot be required directly. External entities have no producer in this API; reference them via a membership edge instead.`,
            path: ['paths', pathKey, method, 'x-semantic-requires', 'kind'],
          });
        }
      }
    }
  }

  // Cross-reference: every required kind must be established somewhere.
  // Skip kinds that already failed the registry check — the message above
  // is the actionable one. Skip external entities — by definition they
  // have no producer in this API, and the direct-reject above already
  // catches anyone trying to require one with x-semantic-requires.
  for (const r of required) {
    if (!registry.has(r.kind)) continue;
    if (externalNames.has(r.kind)) continue;
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
