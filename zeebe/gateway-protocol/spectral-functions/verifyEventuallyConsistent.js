// Spectral custom function to verify that command (mutating) operations
// are not marked with x-eventually-consistent: true
//
// Query operations (search, statistics, get-style reads) correctly use
// x-eventually-consistent: true because they read from eventually-consistent
// projections. But command operations (create, update, delete, cancel, etc.)
// are synchronous writes — marking them eventually-consistent is incorrect and
// causes SDK generators to emit unnecessary polling wrappers.
//
// This function is applied to each operation in the OpenAPI spec.

module.exports = (input, _opts, context) => {
  // input is the entire operation object (contains method, path, operation details)
  // context.path gives us the JSON path to this operation
  
  const errors = [];
  
  // Extract the HTTP method and path from context.path
  // context.path format: ["paths", "/some/path", "post", ...]
  if (!context.path || context.path.length < 3) {
    return errors;
  }
  
  const pathString = context.path[1]; // e.g., "/process-instances"
  const method = context.path[2];     // e.g., "post"
  
  // Check if operation has x-eventually-consistent: true
  const eventuallyConsistent = input['x-eventually-consistent'];
  if (eventuallyConsistent !== true) {
    return errors;
  }
  
  const operationId = input.operationId || '?';
  
  // GET is always a query — x-eventually-consistent: true is fine
  if (method === 'get') {
    return errors;
  }
  
  // PUT, PATCH, DELETE are always mutations
  const mutationMethods = new Set(['put', 'patch', 'delete']);
  let isMutation = mutationMethods.has(method);
  
  // POST: check if it's a query (search/statistics) or a mutation
  if (method === 'post' && !isMutation) {
    // Check path for query indicators
    const queryPathSegments = ['/search', '/statistics'];
    const isQueryPath = queryPathSegments.some(seg => pathString.includes(seg));
    
    // Check operationId for query indicators
    const operationIdLower = operationId.toLowerCase();
    const isQueryOperation = operationIdLower.startsWith('search') || 
                            operationIdLower.startsWith('get');
    
    const isQuery = isQueryPath || isQueryOperation;
    isMutation = !isQuery;
  }
  
  if (isMutation) {
    errors.push({
      message: `Command operation "${operationId}" (${method.toUpperCase()} ${pathString}) must not have x-eventually-consistent: true. Command operations are synchronous writes. Only query operations (search, statistics, get) should use this annotation. Refer to https://github.com/camunda/camunda/issues/45968 for details. Contact #team-camunda-ex on Slack if this is a false positive.`,
      path: [...context.path, 'x-eventually-consistent'],
    });
  }
  
  return errors;
};
