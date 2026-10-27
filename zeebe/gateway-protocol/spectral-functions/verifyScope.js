// Spectral custom function to verify that every operation declares a valid
// x-scope, marking whether the endpoint is cluster-wide or scoped to a
// single physical tenant. Consumed by the docs API reference to render a
// scope badge on each operation (camunda/camunda#61157).
//
// Applied to each operation object under paths (get, post, put, patch, delete).

// $.paths[*][*] matches all keys under a path item, including non-operation
// entries like "parameters", "summary", "$ref", etc. Only check actual HTTP methods.
const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete']);
const VALID_SCOPES = new Set(['cluster-wide', 'physical-tenant']);

module.exports = (input, _opts, context) => {
  const errors = [];

  if (!context.path || context.path.length < 3) {
    return errors;
  }

  const pathString = context.path[1];
  const method = context.path[2];

  if (!HTTP_METHODS.has(method)) {
    return errors;
  }

  const operationId = input.operationId || '?';
  const scope = input['x-scope'];

  if (typeof scope !== 'string' || !VALID_SCOPES.has(scope)) {
    errors.push({
      message: `Operation "${operationId}" (${method.toUpperCase()} ${pathString}) is missing a valid x-scope. Every endpoint must declare whether it is "cluster-wide" or "physical-tenant" scoped.`,
      path: [...context.path],
    });
    return errors;
  }

  // /cluster/v2/... path items override `servers` to drop the document's /v2
  // base, and PhysicalTenantRequestMappingHandlerMapping never builds a
  // physical-tenant-prefixed sibling for them, so they are structurally the
  // only cluster-wide operations.
  const expectedScope = pathString.startsWith('/cluster/v2/') ? 'cluster-wide' : 'physical-tenant';
  if (scope !== expectedScope) {
    errors.push({
      message: `Operation "${operationId}" (${method.toUpperCase()} ${pathString}) declares x-scope: ${scope}, but its path implies x-scope: ${expectedScope}.`,
      path: [...context.path],
    });
  }

  return errors;
};
