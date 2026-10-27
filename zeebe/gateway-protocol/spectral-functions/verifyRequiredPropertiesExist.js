// Spectral custom function to verify that every entry in a schema's `required`
// array corresponds to a key in its `properties` object or in an `allOf` composed schema.

module.exports = (input, _opts, context) => {
  const errors = [];

  if (!input || typeof input !== 'object') {
    return [];
  }

  const { required, properties, allOf } = input;

  if (!Array.isArray(required)) {
    return [];
  }

  // Collect property names from local properties and allOf composed schemas (recursive)
  const propertyNames = new Set();

  function collectProperties(schema) {
    if (!schema || typeof schema !== 'object') return;
    if (schema.properties && typeof schema.properties === 'object') {
      Object.keys(schema.properties).forEach((name) => propertyNames.add(name));
    }
    if (Array.isArray(schema.allOf)) {
      for (const subSchema of schema.allOf) {
        collectProperties(subSchema);
      }
    }
  }

  collectProperties(input);

  if (propertyNames.size === 0) {
    return [];
  }

  required.forEach((name, index) => {
    if (!propertyNames.has(name)) {
      errors.push({
        message: `\`${name}\` is listed in \`required\` but does not exist in \`properties\` or \`allOf\` compositions.`,
        path: [...context.path, 'required', index],
      });
    }
  });

  return errors;
};
