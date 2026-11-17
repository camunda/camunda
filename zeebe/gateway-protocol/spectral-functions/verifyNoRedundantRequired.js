// Spectral custom function to verify that a schema's own `required` array
// does not redundantly re-list a field that is already required by one of
// its `allOf`-composed schemas.
//
// A field required by an allOf member is already required for the composed
// schema (JSON Schema `allOf` unions the required sets of all members), so
// redeclaring it locally adds nothing and tends to drift — e.g. a rename or
// removal of the field on the inherited schema needs a second edit here that
// is easy to forget. See camunda/camunda#55914 for a case where four
// statistics result schemas redundantly re-listed `page`, already required
// via `SearchQueryResponse`.

module.exports = (input, _opts, context) => {
  if (!input || typeof input !== 'object') {
    return [];
  }

  const { required, allOf } = input;

  if (!Array.isArray(required) || !Array.isArray(allOf)) {
    return [];
  }

  const inheritedRequired = new Set();

  function collectInheritedRequired(schema) {
    if (!schema || typeof schema !== 'object') return;
    if (Array.isArray(schema.required)) {
      schema.required.forEach((name) => inheritedRequired.add(name));
    }
    if (Array.isArray(schema.allOf)) {
      schema.allOf.forEach(collectInheritedRequired);
    }
  }

  allOf.forEach(collectInheritedRequired);

  const errors = [];

  required.forEach((name, index) => {
    if (inheritedRequired.has(name)) {
      errors.push({
        message: `\`${name}\` is already required via an allOf-composed schema; remove the redundant entry from this schema's own \`required\` list.`,
        path: [...context.path, 'required', index],
      });
    }
  });

  return errors;
};
