// Spectral custom function to verify that ALL properties in response schemas
// are listed in the `required` array.
//
// Applied via $.paths[*] in the traversal pass (rest-api.yaml), so $refs are
// fully resolved. The function navigates from each path item into its HTTP
// method operations → responses → content → schema, then recursively checks
// every nested schema (including allOf-composed schemas, nested objects, and
// array items).
//
// IMPORTANT: Error paths are anchored at the operation level (method node)
// rather than pointing deep into the resolved schema. This prevents Spectral's
// $ref source-mapping from duplicating errors — once at the reference site
// (under paths) and again at the definition site (under components).
//
// Fields that are not required cause SDK generators to produce optional/union
// types (e.g. `string | undefined`), which complicates consumer code and
// obscures the actual API contract. The convention is to list every response
// field in the `required` array.

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete'];

module.exports = (input, _opts, context) => {
  if (!input || typeof input !== 'object') {
    return [];
  }

  const errors = [];

  // input is a path item; iterate over HTTP methods.
  for (const method of HTTP_METHODS) {
    const operation = input[method];
    if (!operation || typeof operation !== 'object' || !operation.responses) {
      continue;
    }

    // Anchor all error paths at the operation level so they never cross
    // a $ref boundary (response schemas are typically $ref'd to components).
    const operationPath = [...context.path, method];

    for (const [code, response] of Object.entries(operation.responses)) {
      if (!response || !response.content) continue;

      for (const [, mediaObj] of Object.entries(response.content)) {
        if (!mediaObj || !mediaObj.schema) continue;

        checkSchema(mediaObj.schema, operationPath, errors, new Set());
      }
    }
  }

  return errors;
};

// Aggregate `required` entries and `properties` from a schema and its allOf
// members into a single view for the current schema level.
function collectRequiredAndProperties(schema, requiredSet, propertyEntries) {
  if (!schema || typeof schema !== 'object') return;

  if (Array.isArray(schema.required)) {
    schema.required.forEach((name) => requiredSet.add(name));
  }

  if (schema.properties && typeof schema.properties === 'object') {
    for (const [name, prop] of Object.entries(schema.properties)) {
      propertyEntries.push({ name, schema: prop });
    }
  }

  if (Array.isArray(schema.allOf)) {
    schema.allOf.forEach((sub) =>
      collectRequiredAndProperties(sub, requiredSet, propertyEntries)
    );
  }
}

function checkSchema(schema, anchorPath, errors, visited) {
  if (!schema || typeof schema !== 'object') return;

  // Prevent infinite loops on circular $refs.
  if (visited.has(schema)) return;
  visited.add(schema);

  // Aggregate required + properties across allOf at this level.
  const requiredSet = new Set();
  const propertyEntries = [];
  collectRequiredAndProperties(schema, requiredSet, propertyEntries);

  for (const { name, schema: propSchema } of propertyEntries) {
    if (!propSchema || typeof propSchema !== 'object') continue;

    if (!requiredSet.has(name)) {
      errors.push({
        message: `Response property \`${name}\` must be listed in \`required\`.`,
        path: anchorPath,
      });
    }

    // Recurse into nested objects and allOf compositions.
    if (propSchema.properties || propSchema.allOf) {
      checkSchema(propSchema, anchorPath, errors, visited);
    }

    // Recurse into array items.
    if (
      propSchema.type === 'array' &&
      propSchema.items &&
      typeof propSchema.items === 'object'
    ) {
      checkSchema(propSchema.items, anchorPath, errors, visited);
    }
  }
}
