// Spectral custom function to verify that all array properties in response
// schemas are both required and not nullable.
//
// Applied via $.paths[*] in the traversal pass (rest-api.yaml), so $refs are
// fully resolved. The function navigates from each path item into its HTTP
// method operations → responses → content → schema, then recursively checks
// every nested schema for array properties (including allOf-composed schemas,
// nested objects, and array items).
//
// Arrays that are optional or nullable cause SDK generators to produce
// union types (e.g. `string[] | undefined | null`), which complicates
// consumer code. The convention is to use an empty array as the default
// instead of omitting or nulling the field.

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete'];

module.exports = (input, _opts, context) => {
  if (!input || typeof input !== 'object') {
    return [];
  }

  const errors = [];
  const visited = new Set();

  // input is a path item; iterate over HTTP methods.
  for (const method of HTTP_METHODS) {
    const operation = input[method];
    if (!operation || typeof operation !== 'object' || !operation.responses) {
      continue;
    }

    for (const [code, response] of Object.entries(operation.responses)) {
      if (!response || !response.content) continue;

      for (const [mediaType, mediaObj] of Object.entries(response.content)) {
        if (!mediaObj || !mediaObj.schema) continue;

        const schemaPath = [
          ...context.path,
          method,
          'responses',
          code,
          'content',
          mediaType,
          'schema',
        ];

        checkSchema(mediaObj.schema, schemaPath, errors, visited);
      }
    }
  }

  return errors;
};

// Aggregate `required` entries and `properties` from a schema and its allOf
// members into a single view for the current schema level. Tracks the actual
// path to each property so errors point to the right spec location.
function collectRequiredAndProperties(
  schema,
  requiredSet,
  propertyEntries,
  basePath
) {
  if (!schema || typeof schema !== 'object') return;

  if (Array.isArray(schema.required)) {
    schema.required.forEach((name) => requiredSet.add(name));
  }

  if (schema.properties && typeof schema.properties === 'object') {
    for (const [name, prop] of Object.entries(schema.properties)) {
      propertyEntries.push({
        name,
        schema: prop,
        path: [...basePath, 'properties', name],
      });
    }
  }

  if (Array.isArray(schema.allOf)) {
    schema.allOf.forEach((sub, i) =>
      collectRequiredAndProperties(sub, requiredSet, propertyEntries, [
        ...basePath,
        'allOf',
        i,
      ])
    );
  }
}

function checkSchema(schema, basePath, errors, visited) {
  if (!schema || typeof schema !== 'object') return;

  // Prevent infinite loops on circular $refs.
  if (visited.has(schema)) return;
  visited.add(schema);

  // Aggregate required + properties across allOf at this level.
  const requiredSet = new Set();
  const propertyEntries = [];
  collectRequiredAndProperties(schema, requiredSet, propertyEntries, basePath);

  for (const { name, schema: propSchema, path: propPath } of propertyEntries) {
    if (!propSchema || typeof propSchema !== 'object') continue;

    if (propSchema.type === 'array') {
      if (!requiredSet.has(name)) {
        errors.push({
          message: `Array property \`${name}\` must be listed in \`required\`.`,
          path: [...propPath, 'type'],
        });
      }

      if (propSchema.nullable === true) {
        errors.push({
          message: `Array property \`${name}\` must not be \`nullable\` on response schema. \`null\` coerces to \`[]\` in the gateway.`,
          path: [...propPath, 'nullable'],
        });
      }

      // Recurse into array items (e.g. items of a search result list).
      if (propSchema.items && typeof propSchema.items === 'object') {
        checkSchema(propSchema.items, [...propPath, 'items'], errors, visited);
      }
    } else if (propSchema.properties || propSchema.allOf) {
      // Recurse into nested object schemas.
      checkSchema(propSchema, propPath, errors, visited);
    }
  }
}
