/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const validateJSON = (value?: string) => {
  try {
    if (value === undefined || value === '') {
      return 'Value must be JSON';
    }
    JSON.parse(value);
  } catch {
    return 'Value must be JSON';
  }
  return undefined;
};

const validateNonEmpty = (value?: string) => {
  if (value === undefined || value.trim() === '') {
    return 'Value must not be empty';
  }
  return undefined;
};

export {validateJSON, validateNonEmpty};
