// Spectral custom function to verify that path parameters ending with "Key" are of type string
// Migrated from Vacuum custom rule

module.exports = (input, _opts, context) => {
  // input is a single path parameter
  const { name, schema } = input;
  
  if (!schema || !name) {
    return [];
  }
  
  const type = schema.type;
  
  if (/Key$/.test(name) && type !== "string") {
    return [
      {
        message: `\`${name}\` parameter must be of type \`string\`.`,
        path: [...context.path, 'schema', 'type'],
      },
    ];
  }
  
  return [];
};
