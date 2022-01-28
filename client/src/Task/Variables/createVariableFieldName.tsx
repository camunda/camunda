/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const createVariableFieldName = (name: string) => {
  return `#${name}`;
};

const createNewVariableFieldName = (prefix: string, suffix: string) => {
  return `${prefix}.${suffix}`;
};

export {createVariableFieldName, createNewVariableFieldName};
