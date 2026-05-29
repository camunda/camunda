// Spectral custom function to verify that every operation declares its
// security modes. See https://github.com/camunda/camunda/issues/54268.
//
// The Camunda 8 REST API enforces auth at runtime via `SecurityPathAdapter`
// when started in secured mode (see PR #53708). To keep the spec in sync
// with that enforcement and to let downstream tools (camunda/api-test-generator)
// reason about which endpoints expect 401 responses in which deployment modes,
// every operation must declare a `security:` block. The accepted shapes are:
//
//   1. `security: []`
//      Explicit empty array — overrides the global enforcement, marking the
//      endpoint as publicly unauthenticated (e.g. /status, /setup/user,
//      /license).
//
//   2. A list whose entries each select one of the two conditionally-enforced
//      security schemes (`BearerAuth`, `basicAuth`) with empty scopes:
//
//        security:
//          - BearerAuth: []
//          - basicAuth: []
//
//      Each entry must reference exactly one scheme. Schemes other than the
//      two conditionally-enforced ones are not accepted by this rule. Empty
//      objects (`- {}`), duplicate entries, and non-empty scope arrays are
//      rejected.
//
// Applied to each operation object under paths (get, post, put, patch,
// delete).

const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete']);
const ALLOWED_SCHEMES = new Set(['BearerAuth', 'basicAuth']);

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
  const location = `"${operationId}" (${method.toUpperCase()} ${pathString})`;
  const security = input.security;

  if (security === undefined) {
    errors.push({
      message:
        `Operation ${location} is missing \`security:\`. Every operation must ` +
        `declare its security modes — either \`security: []\` for a publicly ` +
        `unauthenticated endpoint, or a list of conditionally-enforced schemes ` +
        `(\`BearerAuth\` and/or \`basicAuth\`).`,
      path: [...context.path],
    });
    return errors;
  }

  if (!Array.isArray(security)) {
    errors.push({
      message: `Operation ${location} \`security\` must be an array.`,
      path: [...context.path, 'security'],
    });
    return errors;
  }

  // `security: []` (explicit public) is valid; nothing more to check.
  if (security.length === 0) {
    return errors;
  }

  const seenSchemes = new Set();

  for (let i = 0; i < security.length; i++) {
    const entry = security[i];
    const entryPath = [...context.path, 'security', i];

    if (entry === null || typeof entry !== 'object' || Array.isArray(entry)) {
      errors.push({
        message:
          `Operation ${location} \`security[${i}]\` must be an object mapping a ` +
          `scheme name to a scopes array.`,
        path: entryPath,
      });
      continue;
    }

    const schemeNames = Object.keys(entry);

    if (schemeNames.length === 0) {
      errors.push({
        message:
          `Operation ${location} \`security[${i}]\` is an empty object. Each entry ` +
          `must reference exactly one security scheme.`,
        path: entryPath,
      });
      continue;
    }

    if (schemeNames.length > 1) {
      errors.push({
        message:
          `Operation ${location} \`security[${i}]\` declares multiple schemes ` +
          `(${schemeNames.join(', ')}) in a single entry (AND semantics). Use ` +
          `separate list entries to express OR semantics — one scheme per entry.`,
        path: entryPath,
      });
      continue;
    }

    const schemeName = schemeNames[0];

    if (!ALLOWED_SCHEMES.has(schemeName)) {
      errors.push({
        message:
          `Operation ${location} \`security[${i}]\` references unknown scheme ` +
          `\`${schemeName}\`. Only the conditionally-enforced schemes ` +
          `\`BearerAuth\` and \`basicAuth\` are accepted.`,
        path: [...entryPath, schemeName],
      });
      continue;
    }

    const scopes = entry[schemeName];

    if (!Array.isArray(scopes)) {
      errors.push({
        message:
          `Operation ${location} \`security[${i}].${schemeName}\` must be an array ` +
          `(scopes). For \`BearerAuth\`/\`basicAuth\`, this array must be empty.`,
        path: [...entryPath, schemeName],
      });
      continue;
    }

    if (scopes.length !== 0) {
      errors.push({
        message:
          `Operation ${location} \`security[${i}].${schemeName}\` must declare an ` +
          `empty scopes array — \`BearerAuth\` and \`basicAuth\` do not use OAuth ` +
          `scopes.`,
        path: [...entryPath, schemeName],
      });
      continue;
    }

    if (seenSchemes.has(schemeName)) {
      errors.push({
        message:
          `Operation ${location} \`security\` declares scheme \`${schemeName}\` ` +
          `more than once.`,
        path: [...entryPath, schemeName],
      });
      continue;
    }

    seenSchemes.add(schemeName);
  }

  return errors;
};
