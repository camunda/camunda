/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';
import {variablesStore} from 'modules/stores/variables';

const handleVariableNameFieldValidation: FieldValidator<string> = (
  variableName = '',
  all: {name?: string; value?: string}
) => {
  if (all.value !== undefined && all.value.trim() !== '' && !variableName) {
    return 'Name has to be filled';
  }

  if (
    variableName.includes('"') ||
    (variableName.length > 0 && variableName.trim() === '')
  ) {
    return 'Name is invalid';
  }

  const isVariableDuplicate =
    variablesStore.state.items
      .map((variable) => variable.name)
      .filter((name) => name === variableName).length > 0;

  if (isVariableDuplicate) {
    return 'Name should be unique';
  }
};

const handleVariableValueFieldValidation: FieldValidator<string> = (
  value = '',
  all: {name?: string; value?: string}
) => {
  if (
    (value !== '' || (all.name !== '' && all.name !== undefined)) &&
    !isValidJSON(value)
  ) {
    return 'Value has to be JSON';
  } else {
    return undefined;
  }
};

export {handleVariableNameFieldValidation, handleVariableValueFieldValidation};
