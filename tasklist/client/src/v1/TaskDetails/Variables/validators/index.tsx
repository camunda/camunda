/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createVariableFieldName} from '../createVariableFieldName';
import {getNewVariablePrefix} from '../getVariableFieldName';
import {isValidJSON} from 'common/utils/isValidJSON';
import {promisifyValidator} from './promisifyValidator';
import type {FormValues} from '../types';
import get from 'lodash/get';
import type {FieldValidator} from 'final-form';

const ERROR_MESSAGES = {
  invalidName: 'Name is invalid',
  emptyName: 'Name has to be filled',
  duplicateName: 'Name must be unique',
  invalidValue: 'Value has to be JSON or a literal',
} as const;

const VALIDATION_DELAY = 1000;

const validateNameCharacters: FieldValidator<string | undefined> = (
  variableName = '',
) => {
  if (variableName.includes('"') || variableName.match(new RegExp('[\\s]+'))) {
    return ERROR_MESSAGES.invalidName;
  }

  return;
};

const validateNameComplete: FieldValidator<string | undefined> =
  promisifyValidator(
    (
      variableName = '',
      allValues: {value?: string; newVariables?: Array<FormValues>} | undefined,
      meta,
    ) => {
      const fieldName = meta?.name ?? '';

      if (allValues?.newVariables === undefined) {
        return;
      }

      const variableValue: string =
        get(allValues, `${getNewVariablePrefix(fieldName)}.value`) ?? '';

      if (variableValue.trim() !== '' && variableName.trim() === '') {
        return ERROR_MESSAGES.emptyName;
      }

      return;
    },
    VALIDATION_DELAY,
  );

const validateDuplicateNames: FieldValidator<string | undefined> =
  promisifyValidator(
    (
      variableName = '',
      allValues: {value?: string; newVariables?: Array<FormValues>} | undefined,
      meta,
    ) => {
      if (allValues?.newVariables === undefined) {
        return;
      }

      if (
        Object.prototype.hasOwnProperty.call(
          allValues,
          createVariableFieldName(variableName),
        )
      ) {
        return ERROR_MESSAGES.duplicateName;
      }

      if (
        allValues.newVariables.filter(
          (variable) => variable?.name === variableName,
        ).length <= 1
      ) {
        return;
      }

      if (
        meta?.active ||
        meta?.error === ERROR_MESSAGES.duplicateName ||
        meta?.validating
      ) {
        return ERROR_MESSAGES.duplicateName;
      }

      return;
    },
    VALIDATION_DELAY,
  );

const validateValueComplete: FieldValidator<string | undefined> =
  promisifyValidator(
    (
      variableValue = '',
      allValues: {value?: string; newVariables?: Array<FormValues>} | undefined,
      meta,
    ) => {
      const fieldName = meta?.name ?? '';

      if (allValues?.newVariables === undefined) {
        return;
      }

      const variableName: string =
        get(allValues, `${getNewVariablePrefix(fieldName)}.name`) ?? '';

      if (
        (variableName === '' && variableValue === '') ||
        isValidJSON(variableValue)
      ) {
        return;
      }

      return ERROR_MESSAGES.invalidValue;
    },
    VALIDATION_DELAY,
  );

const validateValueJSON: FieldValidator<string | undefined> =
  promisifyValidator((value = '') => {
    if (isValidJSON(value)) {
      return;
    }

    return ERROR_MESSAGES.invalidValue;
  }, VALIDATION_DELAY);

export {
  validateValueComplete,
  validateValueJSON,
  validateNameCharacters,
  validateNameComplete,
  validateDuplicateNames,
};
