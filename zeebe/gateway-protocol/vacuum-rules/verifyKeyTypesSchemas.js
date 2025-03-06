function runRule(input) {
  // `input` is an entire schema object with all properties.
  //   Identify all properties that end with Key, and are not strings or allOfs.
  return Object.entries(input)
    .filter(([name, schema]) => {
      return (
        /Key$/.test(name) &&
        schema.type !== "string" &&
        !Array.isArray(schema.allOf)
      );
    })
    .map(([name]) => {
      return {
        message: `\`${name}\` property must be of type \`string\` or \`BasicStringFilterProperty\`.`,
      };
    });
}
