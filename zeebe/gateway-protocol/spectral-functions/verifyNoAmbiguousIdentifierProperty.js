// Spectral custom function to flag schema properties whose name is one of
// the ambiguous identifiers `id`, `key`, or `name`.
//
// Entity schemas should use qualified names (e.g. `processDefinitionKey`,
// `tenantName`, `globalListenerId`) so that SDK generators can produce
// ergonomic, unambiguous bindings. A small number of existing schemas are
// grandfathered via the allowlist in `.spectral.yaml` — see the issue
// https://github.com/camunda/camunda/issues/52510 for rationale.
//
// Applied to component schemas ($.components.schemas[*]) and inline schemas
// under request bodies, responses, and parameters.

const BANNED = new Set(['id', 'key', 'name']);

module.exports = (input, opts, context) => {
  if (!input || typeof input !== 'object') {
    return [];
  }

  // Determine if we're on a named component schema (allowlist applies)
  // or an inline schema (no allowlist — inline schemas are never grandfathered).
  const isComponentSchema =
    context.path.length >= 3 &&
    context.path[0] === 'components' &&
    context.path[1] === 'schemas';

  const schemaName = isComponentSchema
    ? context.path[2]
    : deriveInlineLabel(context.path);

  const allowlist = (opts && opts.allowlist) || {};
  const allowedProps = isComponentSchema
    ? new Set(allowlist[schemaName] || [])
    : new Set(); // inline schemas have no allowlist

  const errors = [];
  collectBannedProperties(input, allowedProps, schemaName, context.path, errors);
  return errors;
};

/**
 * Derives a human-readable label for an inline schema from its JSON path.
 * Example path: ['paths', '/things', 'post', 'requestBody', 'content', 'application/json', 'schema']
 * Produces: "POST /things (inline request body)"
 */
function deriveInlineLabel(pathSegments) {
  const pathStr = pathSegments[1] || '?';
  const method = (pathSegments[2] || '?').toUpperCase();
  return `${method} ${pathStr} (inline schema)`;
}

/**
 * Recursively collects banned property names from a schema, traversing
 * allOf / oneOf / anyOf composition members.
 */
function collectBannedProperties(schema, allowedProps, schemaName, basePath, errors) {
  if (!schema || typeof schema !== 'object') return;

  if (schema.properties && typeof schema.properties === 'object') {
    for (const propName of Object.keys(schema.properties)) {
      if (BANNED.has(propName) && !allowedProps.has(propName)) {
        errors.push({
          message: `Schema "${schemaName}" has ambiguous property "${propName}". Use a qualified name like "entityName${propName.charAt(0).toUpperCase() + propName.slice(1)}" instead. The allowlist is reserved for already-published grandfathered contracts — see https://github.com/camunda/camunda/issues/52510.`,
          path: [...basePath, 'properties', propName],
        });
      }
    }
  }

  for (const keyword of ['allOf', 'oneOf', 'anyOf']) {
    if (Array.isArray(schema[keyword])) {
      schema[keyword].forEach((member, index) => {
        collectBannedProperties(member, allowedProps, schemaName, [...basePath, keyword, index], errors);
      });
    }
  }
}
