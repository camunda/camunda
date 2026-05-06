// Spectral custom function to flag schema properties whose name is one of
// the ambiguous identifiers `id`, `key`, or `name`.
//
// Entity schemas should use qualified names (e.g. `processDefinitionKey`,
// `tenantName`, `globalListenerId`) so that SDK generators can produce
// ergonomic, unambiguous bindings. A small number of existing schemas are
// grandfathered via the allowlist in `.spectral.yaml` — see the issue
// https://github.com/camunda/camunda/issues/52510 for rationale.
//
// Applied to each schema under $.components.schemas[*].

const BANNED = new Set(['id', 'key', 'name']);

module.exports = (input, opts, context) => {
  if (!input || typeof input !== 'object') {
    return [];
  }

  const schemaName = context.path[context.path.length - 1];
  const allowlist = (opts && opts.allowlist) || {};
  const allowedProps = new Set(allowlist[schemaName] || []);

  const errors = [];
  collectBannedProperties(input, allowedProps, schemaName, context.path, errors);
  return errors;
};

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
          message: `Schema "${schemaName}" has ambiguous property "${propName}". Use a qualified name like "entityName${propName.charAt(0).toUpperCase() + propName.slice(1)}" instead. If this is a grandfathered exception, add it to the allowlist in .spectral.yaml.`,
          path: [...basePath, 'properties', propName],
        });
      }
    }
  }

  for (const keyword of ['allOf', 'oneOf', 'anyOf']) {
    if (Array.isArray(schema[keyword])) {
      for (const member of schema[keyword]) {
        collectBannedProperties(member, allowedProps, schemaName, basePath, errors);
      }
    }
  }
}
