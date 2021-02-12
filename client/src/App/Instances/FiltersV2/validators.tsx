/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';

import {FiltersType, Errors, VariablePair} from './types';

const ERRORS = {
  ids: 'Id has to be 16 to 19 digit numbers, separated by space or comma',
  startDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
  endDate: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
  operationId: 'Id has to be a UUID',
  variables: {
    nameUnfilled: 'Variable has to be filled',
    valueInvalid: 'Value has to be JSON',
    bothInvalid: 'Variable has to be filled and Value has to be JSON',
  },
} as const;

function submissionValidator(filters: FiltersType): Errors | null {
  const {
    ids,
    startDate,
    endDate,
    operationId,
    variableName,
    variableValue,
  } = filters;
  const errors: Errors = Object.fromEntries(
    Object.entries({
      ids: validateIds(ids),
      startDate: validateStartDate(startDate),
      endDate: validateEndDate(endDate),
      operationId: validateOperation(operationId),
      variableName: validateVariableName({
        variableName,
        variableValue,
      }),
      variableValue: validateVariableValue({
        variableName,
        variableValue,
      }),
    }).filter(([, value]) => value !== undefined)
  );

  return Object.keys(errors).length === 0 ? null : errors;
}

function validateIds(value: FiltersType['ids'] = '') {
  const ID_PATTERN = /^[0-9]{16,19}$/;
  const isValid =
    value === '' ||
    value
      .trim()
      .replace(/,\s/g, '|')
      .replace(/\s{1,}/g, '|')
      .replace(/,{1,}/g, '|')
      .split('|')
      .every((id) => ID_PATTERN.test(id));

  return isValid ? undefined : ERRORS.ids;
}

function validateStartDate(value: FiltersType['startDate'] = '') {
  return value === '' || isDateComplete(value.trim())
    ? undefined
    : ERRORS.startDate;
}

function validateEndDate(value: FiltersType['endDate'] = '') {
  return value === '' || isDateComplete(value.trim())
    ? undefined
    : ERRORS.endDate;
}

function isDateComplete(date: string) {
  // Possible patterns: yyyy-mm-dd, yyyy-mm-dd hh, yyyy-mm-dd hh:mm or yyyy-mm-dd hh:mm:ss
  const DATE_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}(\s[0-9]{2}(:[0-9]{2}(:[0-9]{2})?)?)?$/;

  return DATE_PATTERN.test(date);
}

function validateOperation(value: FiltersType['operationId'] = '') {
  const UUID_PATTERN = /^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}$/;

  return value === '' || UUID_PATTERN.test(value)
    ? undefined
    : ERRORS.operationId;
}

function validateVariableName(
  {variableName = '', variableValue = ''}: VariablePair = {
    variableName: '',
    variableValue: '',
  }
) {
  if (variableName === '' && variableValue === '') {
    return undefined;
  }

  if (isValidJSON(variableValue)) {
    return variableName === '' ? ERRORS.variables.nameUnfilled : undefined;
  }

  return variableName === '' ? ERRORS.variables.bothInvalid : undefined;
}

function validateVariableValue(
  {variableName = '', variableValue = ''}: VariablePair = {
    variableName: '',
    variableValue: '',
  }
) {
  if (variableName === '' && variableValue === '') {
    return undefined;
  }

  if (variableName === '') {
    return variableValue === '' || isValidJSON(variableValue)
      ? undefined
      : ERRORS.variables.bothInvalid;
  }

  return isValidJSON(variableValue) ? undefined : ERRORS.variables.valueInvalid;
}

const handleIdsFieldValidation: FieldValidator<FiltersType['ids']> = (
  value = ''
) => {
  const isValid =
    value === '' ||
    /^[0-9]+$/g.test(value.replace(/,/g, '').replace(/\s/g, ''));

  return isValid ? undefined : ERRORS.ids;
};

function removeValidDateCharacters(value: string) {
  return value.replace(/[0-9]|\s|:|-/g, '');
}

const handleStartDateFieldValidation: FieldValidator<
  FiltersType['startDate']
> = (value = '') => {
  return removeValidDateCharacters(value) === '' ? undefined : ERRORS.startDate;
};

const handleEndDateFieldValidation: FieldValidator<FiltersType['endDate']> = (
  value = ''
) => {
  return removeValidDateCharacters(value) === '' ? undefined : ERRORS.endDate;
};

const handleVariableValueFieldValidation: FieldValidator<
  FiltersType['variableValue']
> = (value = '') => {
  return value === '' || isValidJSON(value)
    ? undefined
    : 'Value has to be JSON';
};

const handleOperationIdFieldValidation: FieldValidator<
  FiltersType['operationId']
> = (value = '') => {
  const UUID_PATTERN = /^[a-f0-9-]{1,36}/;

  return value === '' || UUID_PATTERN.test(value)
    ? undefined
    : ERRORS.operationId;
};

export {
  submissionValidator,
  handleIdsFieldValidation,
  handleStartDateFieldValidation,
  handleEndDateFieldValidation,
  handleVariableValueFieldValidation,
  handleOperationIdFieldValidation,
};
