// Spectral custom function to verify that no field in a request body schema is
// `nullable`.
//
// Applied via $.paths[*] in the traversal pass (rest-api.yaml), so $refs are
// fully resolved. The function navigates from each path item into its HTTP
// method operations → requestBody → content → schema, then recursively checks
// every nested schema (properties, allOf/oneOf/anyOf members, array items and
// additionalProperties values).
//
// Request fields express optionality by being left out of `required`.
// `nullable` adds a third, explicit-null state, which SDK generators turn into
// types such as `T | undefined | null`. Removing `nullable` after release
// changes the generated client types, so it has to be caught before release.
//
// Exceptions come from verifyNoNullableRequestProperties.allowlist.json: groups
// of operationId -> field paths, each group carrying the `reason` it is
// allowed. Field paths are normalized: property names joined by `.`, array
// items as `[]`, additionalProperties values as `{}`. allOf/oneOf/anyOf
// members share the field path of their parent. The request body root itself
// is never reported. Tests override the file via the
// `SPECTRAL_NULLABLE_REQUEST_ALLOWLIST` environment variable.
//
// IMPORTANT: Error paths are anchored at the operation node under `paths`,
// because Spectral's source-location mapping across chained $refs is
// unreliable. The synthetic `requestBody/<fieldPath>` suffix is trimmed in the
// output; distinct violations survive because each message names its field
// path.
//
// The cycle guard is per path (`ancestors` is added to and removed from while
// descending), not a global visited set, so a schema shared by two fields in
// the same body is checked, and reported, under both field paths.

'use strict';

const fs = require('node:fs');
const path = require('node:path');

const HTTP_METHODS = ['get', 'put', 'post', 'delete', 'patch'];

const ALLOWLIST_FILE = 'verifyNoNullableRequestProperties.allowlist.json';
const ALLOWLIST_CANDIDATES = [
  // CI invokes spectral from the repo root.
  `zeebe/gateway-protocol/spectral-functions/${ALLOWLIST_FILE}`,
  // Local invocations from inside zeebe/gateway-protocol.
  `spectral-functions/${ALLOWLIST_FILE}`,
];

function resolveAllowlistPath() {
  const override = process.env.SPECTRAL_NULLABLE_REQUEST_ALLOWLIST;
  if (typeof override === 'string' && override.length > 0) {
    return path.resolve(override);
  }
  for (const candidate of ALLOWLIST_CANDIDATES) {
    const abs = path.resolve(process.cwd(), candidate);
    if (fs.existsSync(abs)) return abs;
  }
  return path.resolve(process.cwd(), ALLOWLIST_CANDIDATES[0]);
}

let cachedAllowlist = null;
let cachedFor = null;
function loadAllowlist() {
  const file = resolveAllowlistPath();
  if (cachedAllowlist !== null && cachedFor === file) {
    return { file, allowlist: cachedAllowlist };
  }
  const doc = JSON.parse(fs.readFileSync(file, 'utf8'));
  const allowlist = new Map();
  for (const group of (doc && doc.exceptions) || []) {
    for (const [operationId, fieldPaths] of Object.entries((group && group.fields) || {})) {
      if (!allowlist.has(operationId)) allowlist.set(operationId, new Set());
      fieldPaths.forEach((fieldPath) => allowlist.get(operationId).add(fieldPath));
    }
  }
  cachedAllowlist = allowlist;
  cachedFor = file;
  return { file, allowlist };
}

module.exports = (input, _opts, context) => {
  if (!input || typeof input !== 'object') {
    return [];
  }

  let allowlist;
  try {
    ({ allowlist } = loadAllowlist());
  } catch (e) {
    return [
      {
        message: `Could not load ${ALLOWLIST_FILE}: ${e.message}`,
        path: context.path,
      },
    ];
  }

  const errors = [];

  for (const method of HTTP_METHODS) {
    const operation = input[method];
    if (!operation || typeof operation !== 'object') {
      continue;
    }

    const content = operation.requestBody && operation.requestBody.content;
    if (!content || typeof content !== 'object') {
      continue;
    }

    const allowed = allowlist.get(operation.operationId) || new Set();

    for (const mediaObj of Object.values(content)) {
      if (!mediaObj || !mediaObj.schema) {
        continue;
      }

      checkSchema(mediaObj.schema, '', new Set(), (fieldPath) => {
        if (allowed.has(fieldPath)) {
          return;
        }
        errors.push({
          message:
            `Request body field \`${fieldPath}\` of \`${operation.operationId}\` must not be \`nullable\`. ` +
            'Leave an optional field out of `required` instead; ' +
            'see "Nullable properties" in docs/rest-api-endpoint-guidelines.md.',
          path: [...context.path, method, `requestBody/${fieldPath}`],
        });
      });
    }
  }

  return errors;
};

function checkSchema(schema, fieldPath, ancestors, report) {
  if (!schema || typeof schema !== 'object' || ancestors.has(schema)) {
    return;
  }
  ancestors.add(schema);

  if (schema.nullable === true && fieldPath) {
    report(fieldPath);
  }

  if (schema.properties && typeof schema.properties === 'object') {
    for (const [name, prop] of Object.entries(schema.properties)) {
      checkSchema(prop, fieldPath ? `${fieldPath}.${name}` : name, ancestors, report);
    }
  }

  for (const key of ['allOf', 'oneOf', 'anyOf']) {
    if (Array.isArray(schema[key])) {
      schema[key].forEach((s) => checkSchema(s, fieldPath, ancestors, report));
    }
  }

  if (schema.items && typeof schema.items === 'object') {
    checkSchema(schema.items, `${fieldPath}[]`, ancestors, report);
  }

  if (schema.additionalProperties && typeof schema.additionalProperties === 'object') {
    checkSchema(schema.additionalProperties, `${fieldPath}{}`, ancestors, report);
  }

  ancestors.delete(schema);
}
