/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const getVariableFieldName = (variableNameWithPrefix: string) => {
  return variableNameWithPrefix.substring(1);
};

const getNewVariablePrefix = (variableName: string) => {
  return variableName.replace('.name', '').replace('.value', '');
};

export {getVariableFieldName, getNewVariablePrefix};
