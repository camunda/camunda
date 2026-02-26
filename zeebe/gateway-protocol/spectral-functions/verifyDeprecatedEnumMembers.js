// Spectral custom function to validate the x-deprecated-enum-members vendor extension.
//
// Applied to schemas that have an `enum` property. When `x-deprecated-enum-members`
// is present, it must:
//
// 1. Be a non-empty array.
// 2. Each entry must be an object with exactly `name` (string) and
//    `deprecatedInVersion` (semver string, e.g. "8.9.0").
// 3. Every `name` must reference a value that exists in the schema's `enum` array.
// 4. No duplicate `name` entries are allowed.

const SEMVER_PATTERN = /^\d+\.\d+\.\d+$/;
const EXTENSION_KEY = 'x-deprecated-enum-members';

module.exports = (input, _opts, context) => {
  if (!input || typeof input !== 'object') {
    return [];
  }

  const extension = input[EXTENSION_KEY];

  // Extension is optional — nothing to validate if absent.
  if (extension === undefined) {
    return [];
  }

  const errors = [];
  const basePath = [...context.path, EXTENSION_KEY];

  // 1. Must be a non-empty array.
  if (!Array.isArray(extension)) {
    errors.push({
      message: `\`${EXTENSION_KEY}\` must be an array.`,
      path: basePath,
    });
    return errors;
  }

  if (extension.length === 0) {
    errors.push({
      message: `\`${EXTENSION_KEY}\` must not be empty.`,
      path: basePath,
    });
    return errors;
  }

  const enumValues = Array.isArray(input.enum) ? new Set(input.enum) : new Set();
  const seenNames = new Set();

  extension.forEach((entry, index) => {
    const entryPath = [...basePath, index];

    // 2a. Each entry must be an object.
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
      errors.push({
        message: `\`${EXTENSION_KEY}[${index}]\` must be an object with \`name\` and \`deprecatedInVersion\`.`,
        path: entryPath,
      });
      return;
    }

    // 2b. Validate allowed keys — only `name` and `deprecatedInVersion`.
    const allowedKeys = new Set(['name', 'deprecatedInVersion']);
    const extraKeys = Object.keys(entry).filter((k) => !allowedKeys.has(k));
    if (extraKeys.length > 0) {
      errors.push({
        message: `\`${EXTENSION_KEY}[${index}]\` contains unexpected keys: ${extraKeys.join(', ')}. Only \`name\` and \`deprecatedInVersion\` are allowed.`,
        path: entryPath,
      });
    }

    // 2c. `name` must be a non-empty string.
    if (typeof entry.name !== 'string' || entry.name.length === 0) {
      errors.push({
        message: `\`${EXTENSION_KEY}[${index}].name\` must be a non-empty string.`,
        path: [...entryPath, 'name'],
      });
    } else {
      // 3. `name` must exist in the enum values.
      if (enumValues.size > 0 && !enumValues.has(entry.name)) {
        errors.push({
          message: `\`${EXTENSION_KEY}[${index}].name\` value "${entry.name}" is not listed in \`enum\`.`,
          path: [...entryPath, 'name'],
        });
      }

      // 4. No duplicate names.
      if (seenNames.has(entry.name)) {
        errors.push({
          message: `\`${EXTENSION_KEY}[${index}].name\` value "${entry.name}" is duplicated.`,
          path: [...entryPath, 'name'],
        });
      }
      seenNames.add(entry.name);
    }

    // 2d. `deprecatedInVersion` must be a semver string.
    if (
      typeof entry.deprecatedInVersion !== 'string' ||
      !SEMVER_PATTERN.test(entry.deprecatedInVersion)
    ) {
      errors.push({
        message: `\`${EXTENSION_KEY}[${index}].deprecatedInVersion\` must be a semver string (e.g. "8.9.0").`,
        path: [...entryPath, 'deprecatedInVersion'],
      });
    }
  });

  return errors;
};
