// Spectral custom function to verify that schema properties ending with "Key" are of type string
// Migrated from Vacuum custom rule

module.exports = (input, _opts, context) => {
  // input is an entire schema object with all properties
  const errors = [];
  
  if (!input || typeof input !== 'object') {
    return [];
  }
  
  Object.entries(input).forEach(([name, schema]) => {
    if (
      /Key$/.test(name) &&
      schema.type !== "string" &&
      !composesBasicStringFilterProperty(schema)
    ) {
      errors.push({
        message: `\`${name}\` property must be of type \`string\` or \`BasicStringFilterProperty\`.`,
        path: [...context.path, name, 'type'],
      });
    }
  });
  
  return errors;
};

function composesBasicStringFilterProperty(schema) {
  // This is what we're looking for, but it catches false negatives!
  //   For some reason, _some_ allOf arrays are expanded inline, while most include the unexpanded `$ref`.
  
  //   if (!Array.isArray(schema.allOf)) {
  //     return false;
  //   }
  
  //   return schema.allOf.some((subSchema) => {
  //     return subSchema.$ref === "#/components/schemas/BasicStringFilterProperty";
  //   });
  
  // This is a simpler alternative, which could catch false positives instead, if we compose a schema other than BasicStringFilterProperty.
  return Array.isArray(schema.allOf);
}
