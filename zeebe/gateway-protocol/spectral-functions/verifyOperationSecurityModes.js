const HTTP_METHODS = new Set(['get', 'post', 'put', 'patch', 'delete']);
const BEARER_SCHEME = 'BearerAuth';
const BASIC_SCHEMES = new Set(['Basic', 'basicAuth']);

module.exports = (input) => {
  const errors = [];

  // Only enforce this rule for the aggregated root OpenAPI document.
  // Domain fragments (e.g. users.yaml) are linted independently in the
  // file-level pass and intentionally do not carry root-level defaults.
  if (!input || typeof input !== 'object' || typeof input.openapi !== 'string') {
    return errors;
  }

  const securitySchemes = input.components && input.components.securitySchemes;
  if (!securitySchemes || typeof securitySchemes !== 'object') {
    return errors;
  }

  const hasBearerScheme = securitySchemes[BEARER_SCHEME] !== undefined;
  const hasBasicScheme = [...BASIC_SCHEMES].some(
    (scheme) => securitySchemes[scheme] !== undefined,
  );
  if (!hasBearerScheme || !hasBasicScheme) {
    return errors;
  }

  const defaultSecurity = input.security;
  const paths = input.paths;
  if (!paths || typeof paths !== 'object') {
    return errors;
  }

  for (const [pathKey, pathItem] of Object.entries(paths)) {
    if (!pathItem || typeof pathItem !== 'object') {
      continue;
    }

    for (const [method, operation] of Object.entries(pathItem)) {
      if (!HTTP_METHODS.has(method) || !operation || typeof operation !== 'object') {
        continue;
      }

      const effectiveSecurity =
        operation.security === undefined ? defaultSecurity : operation.security;

      const operationId = operation.operationId || '?';
      const opLabel = `${method.toUpperCase()} ${pathKey} (${operationId})`;

      if (effectiveSecurity === undefined) {
        errors.push({
          message: `Operation ${opLabel} is missing a security declaration. Declare [basicAuth, BearerAuth] or an explicit empty security set [].`,
          path: ['paths', pathKey, method],
        });
        continue;
      }

      if (!Array.isArray(effectiveSecurity)) {
        errors.push({
          message: `Operation ${opLabel} has an invalid security declaration. Security must be an array of security requirement objects or [].`,
          path: ['paths', pathKey, method, 'security'],
        });
        continue;
      }

      if (effectiveSecurity.length === 0) {
        continue;
      }

      const seenSchemes = new Set();
      let hasShapeError = false;

      for (const entry of effectiveSecurity) {
        if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
          hasShapeError = true;
          break;
        }

        const names = Object.keys(entry);
        if (names.length !== 1) {
          hasShapeError = true;
          break;
        }

        const schemeName = names[0];
        const scopes = entry[schemeName];
        if (!Array.isArray(scopes) || scopes.length !== 0) {
          hasShapeError = true;
          break;
        }

        seenSchemes.add(schemeName);
      }

      if (hasShapeError) {
        errors.push({
          message: `Operation ${opLabel} must use OpenAPI security requirements of the form [{basicAuth: []}, {BearerAuth: []}] or [].`,
          path: ['paths', pathKey, method, 'security'],
        });
        continue;
      }

      const hasBearer = seenSchemes.has(BEARER_SCHEME);
      const hasBasic = [...BASIC_SCHEMES].some((scheme) => seenSchemes.has(scheme));
      const hasUnexpected = [...seenSchemes].some(
        (scheme) => scheme !== BEARER_SCHEME && !BASIC_SCHEMES.has(scheme),
      );

      if (!hasBasic || !hasBearer || hasUnexpected) {
        errors.push({
          message: `Operation ${opLabel} must declare either both basicAuth and BearerAuth, or an explicit empty security set [].`,
          path: ['paths', pathKey, method, 'security'],
        });
      }
    }
  }

  return errors;
};
