// Spectral custom function to verify that every operation explicitly declares
// x-eventually-consistent as a boolean.
//
// SDK generators read x-eventually-consistent to decide whether to emit
// polling wrappers, so a missing value silently means "no polling". Declaring
// it on every endpoint keeps that decision deliberate (see
// docs/rest-api-endpoint-guidelines.md, sections 2.3 and 2.5). Whether a
// command may be marked `true` is checked separately by
// verifyEventuallyConsistent.
//
// Applied to each operation object under paths (get, post, put, patch, delete).

// $.paths[*][*] matches all keys under a path item, including non-operation
// entries like "parameters", "servers", "summary", "$ref", etc. Only check actual HTTP methods.
const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete']);

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

  if (typeof input['x-eventually-consistent'] !== 'boolean') {
    const operationId = input.operationId || '?';
    errors.push({
      message: `Operation "${operationId}" (${method.toUpperCase()} ${pathString}) is missing a valid x-eventually-consistent. Every endpoint must declare it explicitly as true (reads from eventually-consistent secondary storage) or false (synchronous command or primary-storage read).`,
      path: [...context.path],
    });
  }

  return errors;
};
