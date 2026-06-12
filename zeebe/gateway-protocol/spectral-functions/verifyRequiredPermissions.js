// Spectral custom function: verifyRequiredPermissions
//
// Enforces the `x-required-permissions` vendor extension that declares the
// canonical endpoint -> required-permission(s) binding (camunda/camunda#54727,
// ADR docs/adr/security/001-endpoint-required-permission-mapping.md).
//
// Two responsibilities:
//   1. Completeness (gap guard): every operation under `paths` MUST declare
//      `x-required-permissions` (an array). An empty array means "explicitly
//      unrestricted" (no specific permission required, though the endpoint is
//      still authenticated) — this distinguishes it from "forgot to annotate".
//   2. Registry validity: every static entry's `{resourceType, permissionType}`
//      must be a member of the AuthorizationResourceType / PermissionType enums
//      AND a pair the resource type actually supports, as recorded in
//      resource-permissions.json (which mirrors
//      AuthorizationResourceType.buildResourcePermissionsMap()).
//   3. Enforcement coherence: an operation marked `x-permission-enforcement:
//      filter` must declare at least one required permission (an unrestricted
//      endpoint has nothing to filter by). The enum itself is checked by the
//      `permission-enforcement-shape` Spectral rule.
//
// Entry shapes (the structural schema is additionally enforced by the
// `required-permissions-shape` Spectral rule):
//   - static  : { resourceType, permissionType }
//   - any-of  : { anyOf: [ {resourceType, permissionType}, ... ] }  (OR)
//   - dynamic : { dynamic: true, note: "<how it is resolved at runtime>" }
//
// Applied to the document root (given: $) so a single pass can read the JSON
// registry once and walk every operation.
//
// Tests override the registry path via the
// `SPECTRAL_RESOURCE_PERMISSIONS_REGISTRY` environment variable.

'use strict';

const fs = require('node:fs');
const path = require('node:path');

const HTTP_METHODS = new Set(['get', 'put', 'post', 'patch', 'delete']);

const REGISTRY_CANDIDATES = [
  // CI invokes spectral from the repo root.
  'zeebe/gateway-protocol/src/main/proto/v2/resource-permissions.json',
  // Local invocations from inside zeebe/gateway-protocol.
  'src/main/proto/v2/resource-permissions.json',
];

function resolveRegistryPath() {
  const override = process.env.SPECTRAL_RESOURCE_PERMISSIONS_REGISTRY;
  if (typeof override === 'string' && override.length > 0) {
    return path.resolve(override);
  }
  for (const candidate of REGISTRY_CANDIDATES) {
    const abs = path.resolve(process.cwd(), candidate);
    if (fs.existsSync(abs)) return abs;
  }
  return path.resolve(process.cwd(), REGISTRY_CANDIDATES[0]);
}

let cachedRegistry = null;
let cachedFor = null;
function loadRegistry() {
  const file = resolveRegistryPath();
  if (cachedRegistry !== null && cachedFor === file) return cachedRegistry;
  const raw = fs.readFileSync(file, 'utf8');
  const doc = JSON.parse(raw);
  const map = doc && doc.resourcePermissions ? doc.resourcePermissions : {};
  const resourcePermissions = new Map();
  for (const [resourceType, permissions] of Object.entries(map)) {
    resourcePermissions.set(resourceType, new Set(permissions));
  }
  cachedRegistry = resourcePermissions;
  cachedFor = file;
  return cachedRegistry;
}

// Validates a single static {resourceType, permissionType} entry against the
// registry. Pushes a descriptive error onto `errors` when invalid.
function validateStatic(entry, registry, location, where, errors) {
  const { resourceType, permissionType } = entry;
  const supported = registry.get(resourceType);
  if (supported === undefined) {
    errors.push({
      message: `${where} declares an unknown resourceType '${resourceType}'. It is not a member of AuthorizationResourceType (see resource-permissions.json).`,
      path: location,
    });
    return;
  }
  if (!supported.has(permissionType)) {
    errors.push({
      message: `${where} declares permissionType '${permissionType}' which is not supported by resourceType '${resourceType}'. Supported: [${[...supported].sort().join(', ')}] (see resource-permissions.json).`,
      path: location,
    });
  }
}

// Returns true iff the entry is a well-formed static pair (both keys present
// as strings). Structural shape is also enforced by the schema rule; here we
// gate which validator to run.
function isStaticEntry(entry) {
  return (
    entry != null &&
    typeof entry === 'object' &&
    typeof entry.resourceType === 'string' &&
    typeof entry.permissionType === 'string'
  );
}

module.exports = (input, _opts, _context) => {
  const errors = [];
  const paths = input && input.paths;
  if (!paths || typeof paths !== 'object') return errors;

  let registry;
  try {
    registry = loadRegistry();
  } catch (e) {
    return [
      {
        message: `Could not load resource-permissions.json registry: ${e.message}`,
        path: ['paths'],
      },
    ];
  }

  for (const [pathKey, pathItem] of Object.entries(paths)) {
    if (!pathItem || typeof pathItem !== 'object') continue;
    for (const [method, op] of Object.entries(pathItem)) {
      if (!HTTP_METHODS.has(method)) continue;
      if (!op || typeof op !== 'object') continue;

      const operationId = op.operationId || '?';
      const where = `Operation "${operationId}" (${method.toUpperCase()} ${pathKey})`;
      const basePath = ['paths', pathKey, method, 'x-required-permissions'];
      const declared = op['x-required-permissions'];

      // 1. Completeness.
      if (declared === undefined) {
        errors.push({
          message: `${where} is missing x-required-permissions. Every operation must declare the permission(s) it enforces, or an empty array [] if it is unrestricted (no specific permission required). See docs/adr/security/001-endpoint-required-permission-mapping.md.`,
          path: ['paths', pathKey, method],
        });
        continue;
      }
      if (!Array.isArray(declared)) {
        errors.push({
          message: `${where} has a non-array x-required-permissions. It must be an array of permission entries (or [] for unrestricted).`,
          path: basePath,
        });
        continue;
      }

      // 2. Enforcement marker coherence: `filter` implies the endpoint scopes
      //    results by permission, so it cannot be unrestricted.
      const enforcement = op['x-permission-enforcement'];
      if (enforcement === 'filter' && declared.length === 0) {
        errors.push({
          message: `${where} declares x-permission-enforcement: filter but has an empty x-required-permissions. An unrestricted endpoint has nothing to filter by; use 'reject' (or remove the marker) or declare the required permission(s).`,
          path: ['paths', pathKey, method, 'x-permission-enforcement'],
        });
      }

      // 3. Registry validity per entry.
      declared.forEach((entry, i) => {
        const loc = [...basePath, i];
        if (entry == null || typeof entry !== 'object') return; // shape rule handles
        if (Array.isArray(entry.anyOf)) {
          entry.anyOf.forEach((sub, j) => {
            if (isStaticEntry(sub)) {
              validateStatic(sub, registry, [...loc, 'anyOf', j], where, errors);
            }
          });
          return;
        }
        if (entry.dynamic === true) {
          return; // no static pair to validate; note presence enforced by shape rule
        }
        if (isStaticEntry(entry)) {
          validateStatic(entry, registry, loc, where, errors);
        }
      });
    }
  }

  return errors;
};
