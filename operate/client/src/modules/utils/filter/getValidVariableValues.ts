/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const getValidVariableValues = (values: string): Array<JSON> | undefined => {
  // remove leading and trailing commas
  values = values.replace(/,+$/, '').replace(/^,+/, '');
  
  // First, try parsing as-is (handles null, numbers, booleans, quoted strings, objects, arrays)
  try {
    return JSON.parse(`[${values}]`);
  } catch {
    // If parsing fails, try auto-quoting for simple unquoted strings
    // Only if the input doesn't contain JSON structural characters
    const hasJSONStructure = /[{}\[\]"]/.test(values);
    const trimmedValues = values.trim();
    
    if (!hasJSONStructure && trimmedValues !== '') {
      try {
        // Wrap in quotes to treat as a string
        return JSON.parse(`["${trimmedValues}"]`);
      } catch {
        return undefined;
      }
    }
    return undefined;
  }
};

export {getValidVariableValues};
