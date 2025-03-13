function runRule(input) {
  // `input` is an entire schema object with all properties.
  //   Identify all properties that end with Key, and are not strings or allOfs.
  return Object.entries(input)
    .filter(([name, schema]) => {
      return (
        /Key$/.test(name) &&
        schema.type !== "string" &&
        !composesBasicStringFilterProperty(schema)
      );
    })
    .map(([name]) => {
      return {
        message: `\`${name}\` property must be of type \`string\` or \`BasicStringFilterProperty\`.`,
      };
    });
}

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
