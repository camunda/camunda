/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createVariableFieldName} from '../createVariableFieldName';
import {getNewVariablePrefix} from '../getVariableFieldName';
import {isValidJSON} from 'modules/utils/isValidJSON';
import {promisifyValidator} from './promisifyValidator';
import {FormValues} from '../types';
import {get} from 'lodash';
import {FieldValidator} from 'final-form';

const VALIDATION_DELAY = 1000;

const validateNameCharacters: FieldValidator<string | undefined> = (
  variableName = '',
) => {
  if (variableName.includes('"') || variableName.match(new RegExp('[\\s]+'))) {
    return 'Name is invalid';
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

      const variableValue =
        get(allValues, `${getNewVariablePrefix(fieldName)}.value`) ?? '';

      if (variableValue.trim() !== '' && variableName.trim() === '') {
        return 'Name has to be filled';
      }

      return;
    },
    VALIDATION_DELAY,
  );

const validateNameNotDuplicate: FieldValidator<string | undefined> =
  promisifyValidator(
    (
      variableName = '',
      allValues: {value?: string; newVariables?: Array<FormValues>} | undefined,
      meta,
    ) => {
      if (allValues?.newVariables === undefined) {
        return;
      }

      if (allValues.hasOwnProperty(createVariableFieldName(variableName))) {
        return 'Name must be unique';
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
        meta?.error === 'Name must be unique' ||
        meta?.validating
      ) {
        return 'Name must be unique';
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

      const variableName =
        get(allValues, `${getNewVariablePrefix(fieldName)}.name`) ?? '';

      if (
        (variableName === '' && variableValue === '') ||
        isValidJSON(variableValue)
      ) {
        return;
      }

      return 'Value has to be JSON';
    },
    VALIDATION_DELAY,
  );

const validateValueJSON: FieldValidator<string | undefined> =
  promisifyValidator((value = '') => {
    if (isValidJSON(value)) {
      return;
    }

    return 'Value has to be JSON';
  }, VALIDATION_DELAY);

export {
  validateValueComplete,
  validateValueJSON,
  validateNameCharacters,
  validateNameComplete,
  validateNameNotDuplicate,
};
