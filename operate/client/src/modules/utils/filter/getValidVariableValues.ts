/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const getValidVariableValues = (values: string): Array<JSON> | undefined => {
  // remove leading and trailing commas
  values = values.replace(/,+$/, '').replace(/^,+/, '');
  try {
    return JSON.parse(`[${values}]`);
  } catch {
    return undefined;
  }
};

export {getValidVariableValues};
