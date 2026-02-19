// Spectral custom function to verify that all fields in a schema are in the required array
// or are explicitly marked as nullable.
//
// This enforces a contract where:
// - All response fields must be in the required array
// - If a field is optionally present at runtime, it must be explicitly marked as nullable: true
//
// This helps harden the response contract and makes the API more predictable.

module.exports = (input, _opts, context) => {
  // input is the entire schema object
  const errors = [];
  
  if (!input || typeof input !== 'object') {
    return [];
  }
  
  // Skip if this is not an object schema
  if (input.type !== 'object') {
    return [];
  }
  
  // Skip if there are no properties defined
  if (!input.properties || typeof input.properties !== 'object') {
    return [];
  }
  
  // Get the list of required fields (default to empty array if not present)
  const requiredFields = Array.isArray(input.required) ? input.required : [];
  const requiredSet = new Set(requiredFields);
  
  // Get all property names
  const allProperties = Object.keys(input.properties);
  
  // Check each property
  allProperties.forEach((propertyName) => {
    const property = input.properties[propertyName];
    
    // Skip if property is in the required array
    if (requiredSet.has(propertyName)) {
      return;
    }
    
    // Property is not required - check if it's explicitly nullable
    const isNullable = property.nullable === true;
    
    if (!isNullable) {
      errors.push({
        message: `All fields in this schema must be in the required array of the schema. If any field is optionally present at runtime, explicitly mark that field as nullable.`,
        path: [...context.path, 'properties', propertyName],
      });
    }
  });
  
  return errors;
};
