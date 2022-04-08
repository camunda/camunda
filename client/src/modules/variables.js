/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

let variables = [];

export async function setVariables(newVariables) {
  variables = newVariables;
}

export function getVariableLabel(name, type) {
  const matchedVariable = variables.find((variable) => {
    const sameName = variable.name === name;
    const sameType = type === undefined ? true : variable.type === type;

    return sameName && sameType;
  });

  return matchedVariable?.label || name;
}
