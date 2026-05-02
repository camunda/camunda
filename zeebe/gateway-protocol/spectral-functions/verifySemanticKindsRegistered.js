// Spectral custom function. Two responsibilities:
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
  cachedRegistry = new Set(
    kinds
      .map((entry) => (typeof entry === 'string' ? entry : entry?.name))
      .filter((name) => typeof name === 'string' && name.length > 0),
  );
  cachedFor = file;
  return cachedRegistry;
}

module.exports = (input, _opts, _context) => {
  const errors = [];
  const registry = loadRegistry();

  const paths = input?.paths;
  if (!paths || typeof paths !== 'object') return errors;

  const established = new Set();
  const required = []; // [{ kind, method, pathKey }]

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
      }

      const req = op['x-semantic-requires'];
      if (req && typeof req.kind === 'string') {
        required.push({ kind: req.kind, method, pathKey });
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
  for (const { kind, method, pathKey } of required) {
    if (!registry.has(kind)) continue;
    if (!established.has(kind)) {
      errors.push({
        message: `Semantic kind '${kind}' is required by ${method.toUpperCase()} ${pathKey} (x-semantic-requires) but no operation establishes it. Add x-semantic-establishes on the producer operation.`,
        path: ['paths', pathKey, method, 'x-semantic-requires', 'kind'],
      });
    }
  }

  return errors;
};
