/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createVariableFieldName} from './createVariableFieldName';
import {Variable} from 'modules/types';
import {FormValues} from './types';

const validateJSON = (value?: string) => {
  try {
    if (value === undefined || value === '') {
      return 'Value has to be JSON';
    }
    JSON.parse(value);
  } catch {
    return 'Value has to be JSON';
  }
  return undefined;
};

const validateNonEmpty = (value?: string) => {
  if (value === undefined || value.trim() === '') {
    return 'Name has to be filled';
  }
  return undefined;
};

function validateDuplicateVariableName(
  variable: Pick<Variable, 'value' | 'name'>,
  values: FormValues,
  variableIndex: number,
): string | void {
  const {name} = variable;
  const {newVariables} = values;

  if (values.hasOwnProperty(createVariableFieldName(name))) {
    return 'Name must be unique';
  }

  if (newVariables === undefined) {
    return undefined;
  }

  if (newVariables.filter((variable) => variable?.name === name).length <= 1) {
    return undefined;
  }

  if (
    newVariables.findIndex((variable) => variable?.name === name) ===
    variableIndex
  ) {
    return undefined;
  }

  return 'Name must be unique';
}

export {validateJSON, validateNonEmpty, validateDuplicateVariableName};
