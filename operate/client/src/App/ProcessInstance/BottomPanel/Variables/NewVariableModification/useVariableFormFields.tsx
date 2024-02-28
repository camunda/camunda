/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useField} from 'react-final-form';

import {createNewVariableFieldName} from '../createVariableFieldName';

const useVariableFormFields = (variableName: string) => {
  const {
    meta: {valid: isNameValid, validating: isNameValidating},
    input: {value: currentName},
  } = useField(createNewVariableFieldName(variableName, 'name'));

  const {
    meta: {valid: isValueValid, validating: isValueValidating},
    input: {value: currentValue},
  } = useField(createNewVariableFieldName(variableName, 'value'));

  const {
    input: {value: currentId},
  } = useField(createNewVariableFieldName(variableName, 'id'));

  const isNameFieldValid = (!isNameValidating && isNameValid) ?? false;
  const isValueFieldValid = (!isValueValidating && isValueValid) ?? false;

  return {
    areFormFieldsValid: isNameFieldValid && isValueFieldValid,
    isNameFieldValid,
    currentName,
    currentValue,
    currentId,
  };
};

export {useVariableFormFields};
