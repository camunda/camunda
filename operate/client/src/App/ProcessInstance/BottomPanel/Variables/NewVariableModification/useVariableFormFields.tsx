/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
