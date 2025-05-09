/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const createVariableFieldName = (name: string) => {
  return `#${name}`;
};

const createNewVariableFieldName = (prefix: string, suffix: string) => {
  return `${prefix}.${suffix}`;
};

export {createVariableFieldName, createNewVariableFieldName};
