// Spectral custom function to verify that every operation has
// x-added-in-version set. This ensures new endpoints always declare
// the Camunda version in which they were introduced.
//
// Applied to each operation object under paths (get, post, put, patch, delete).

module.exports = (input, _opts, context) => {
  const errors = [];

  if (!context.path || context.path.length < 3) {
    return errors;
  }

  const pathString = context.path[1];
  const method = context.path[2];

  // $.paths[*][*] matches all keys under a path item, including non-operation
  // entries like "parameters", "summary", "$ref", etc. Only check actual HTTP methods.
  const httpMethods = new Set(['get', 'post', 'put', 'patch', 'delete']);
  if (!httpMethods.has(method)) {
    return errors;
  }

  const operationId = input.operationId || '?';
  const addedInVersion = input['x-added-in-version'];

  if (typeof addedInVersion !== 'string' || addedInVersion.trim().length === 0) {
    errors.push({
      message: `Operation "${operationId}" (${method.toUpperCase()} ${pathString}) is missing x-added-in-version. Every endpoint must declare the Camunda version in which it was introduced.`,
      path: [...context.path],
    });
  }

  return errors;
};
