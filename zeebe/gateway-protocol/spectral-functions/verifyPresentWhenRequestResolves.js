// Spectral custom function enforcing the referential integrity of every
// `x-present-when` marker against the operation it actually belongs to.
//
// The `present-when-shape` rule only validates a marker's *local* shape (a
// `request` string and a scalar `equals`). It cannot tell whether `request`
// names a real field, because the marker lives on a shared component schema
// (e.g. `ActivatedJobResult.leaseToken`) with no back-reference to the
// operation whose request body it constrains. A typo — `request: withLeese` —
// therefore passes the shape rule, and every SDK generator that derives
// dependent typing from the marker silently falls back to base typing: no
// error, no failing build, the compile-time safety just evaporates across all
// SDKs at once.
//
// This function closes that gap. Applied via `$.paths[*]` in the resolved
// traversal pass (rest-api.yaml), so $refs are inlined, it walks from each
// operation into its 2xx response schemas, finds every reachable
// `x-present-when` marker, and asserts:
//
//   1. `request` names a *top-level* property of that operation's request body
//      (SDK generators can only project onto a top-level request field), and
//   2. the annotated response property stays `nullable: true` and `required`,
//      so it remains inert on the wire for consumers that do not derive from
//      the marker.
//
// Error paths are anchored at the operation node under `paths` (staying on the
// `paths` side of any $ref boundary) with a synthetic suffix so each violation
// remains distinct.

const HTTP_METHODS = ['get', 'post', 'put', 'patch', 'delete'];
const MARKER = 'x-present-when';

module.exports = (input, _opts, context) => {
  if (!input || typeof input !== 'object') {
    return [];
  }

  const errors = [];

  // input is a path item; iterate over HTTP methods.
  for (const method of HTTP_METHODS) {
    const operation = input[method];
    if (!operation || typeof operation !== 'object') {
      continue;
    }

    // Collect every marker reachable from success (2xx) response schemas.
    const markers = [];
    if (operation.responses && typeof operation.responses === 'object') {
      for (const [code, response] of Object.entries(operation.responses)) {
        if (!/^2\d\d$/.test(code)) continue;
        if (!response || !response.content) continue;
        for (const mediaObj of Object.values(response.content)) {
          if (!mediaObj || !mediaObj.schema) continue;
          collectMarkers(mediaObj.schema, new Set(), markers);
        }
      }
    }

    if (markers.length === 0) {
      continue;
    }

    const operationPath = [...context.path, method];
    const opId =
      operation.operationId ||
      `${context.path[context.path.length - 1]} ${method}`;

    // Top-level property names of the operation's request body.
    const requestProps = new Set();
    if (operation.requestBody && operation.requestBody.content) {
      for (const mediaObj of Object.values(operation.requestBody.content)) {
        if (mediaObj && mediaObj.schema) {
          collectTopLevelProperties(mediaObj.schema, requestProps, new Set());
        }
      }
    }

    for (const marked of markers) {
      const request = marked.marker && marked.marker.request;
      // A missing/mistyped `request` is the `present-when-shape` rule's job.
      if (typeof request !== 'string' || request.length === 0) {
        continue;
      }

      if (!requestProps.has(request)) {
        errors.push({
          message: `\`x-present-when.request: ${request}\` on response property \`${marked.name}\` does not resolve to a top-level property of the request body of operation \`${opId}\`.`,
          path: [...operationPath, `${MARKER}/${marked.name}/request`],
        });
      }

      // Inert-on-the-wire: the marked property must keep its declared
      // nullable + required shape so non-deriving consumers are unaffected.
      if (marked.propSchema.nullable !== true) {
        errors.push({
          message: `Response property \`${marked.name}\` carries \`${MARKER}\` and must be \`nullable: true\` (inert on the wire) in operation \`${opId}\`.`,
          path: [...operationPath, `${MARKER}/${marked.name}/nullable`],
        });
      }
      if (!marked.required) {
        errors.push({
          message: `Response property \`${marked.name}\` carries \`${MARKER}\` and must be listed in \`required\` (inert on the wire) in operation \`${opId}\`.`,
          path: [...operationPath, `${MARKER}/${marked.name}/required`],
        });
      }
    }
  }

  return errors;
};

// Aggregate the `required` entries and `properties` of a schema and its
// allOf members into a single view for the current schema level.
function aggregate(schema, requiredSet, propertyEntries) {
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
    schema.allOf.forEach((sub) => aggregate(sub, requiredSet, propertyEntries));
  }
}

// Collect the top-level property names of a (possibly allOf-composed) schema.
// Intentionally does not recurse into nested objects: `x-present-when.request`
// must name a *top-level* request-body field.
function collectTopLevelProperties(schema, out, visited) {
  if (!schema || typeof schema !== 'object' || visited.has(schema)) return;
  visited.add(schema);

  const requiredSet = new Set();
  const entries = [];
  aggregate(schema, requiredSet, entries);
  for (const { name } of entries) {
    out.add(name);
  }
}

// Recursively find every property carrying an `x-present-when` marker, along
// with whether its enclosing schema lists it in `required`.
function collectMarkers(schema, visited, found) {
  if (!schema || typeof schema !== 'object' || visited.has(schema)) return;
  visited.add(schema);

  const requiredSet = new Set();
  const entries = [];
  aggregate(schema, requiredSet, entries);

  for (const { name, schema: propSchema } of entries) {
    if (!propSchema || typeof propSchema !== 'object') continue;

    if (propSchema[MARKER] && typeof propSchema[MARKER] === 'object') {
      found.push({
        name,
        propSchema,
        marker: propSchema[MARKER],
        required: requiredSet.has(name),
      });
    }

    // Recurse into nested objects and allOf compositions.
    if (propSchema.properties || propSchema.allOf) {
      collectMarkers(propSchema, visited, found);
    }

    // Recurse into array items.
    if (
      propSchema.type === 'array' &&
      propSchema.items &&
      typeof propSchema.items === 'object'
    ) {
      collectMarkers(propSchema.items, visited, found);
    }
  }
}
