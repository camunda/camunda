/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createVariableFieldName} from './createVariableFieldName';
import {Variable} from 'modules/types';
import {FormValues} from './types';
import {isValidJSON} from 'modules/utils/isValidJSON';

const validateJSON = (value?: string) => {
  if (value !== undefined && isValidJSON(value)) {
    return undefined;
  }

  return 'Value has to be JSON';
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
