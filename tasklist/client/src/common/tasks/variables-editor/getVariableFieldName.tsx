/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VARIABLE_NAME_DOT_ESCAPE_CHAR} from './constants';

const getVariableFieldName = (variableNameWithPrefix: string) => {
  const nameWithoutPrefix = variableNameWithPrefix.substring(1);
  return nameWithoutPrefix.replaceAll(VARIABLE_NAME_DOT_ESCAPE_CHAR, '.');
};

const getNewVariablePrefix = (variableName: string) => {
  return variableName.replace('.name', '').replace('.value', '');
};

export {getVariableFieldName, getNewVariablePrefix};
