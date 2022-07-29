/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';
import {variablesStore} from 'modules/stores/variables';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {ERRORS, VALIDATION_DELAY} from './constants';
import {get} from 'lodash';
import {getNewVariablePrefix} from './getNewVariablePrefix';
import {VariableFormValues} from 'modules/types/variables';

const validateNameCharacters: FieldValidator<string | undefined> = (
  variableName = ''
) => {
  if (variableName.includes('"') || variableName.includes(' ')) {
    return ERRORS.INVALID_NAME;
  }
};

const validateModifiedNameComplete: FieldValidator<string | undefined> =
  promisifyValidator(
    (variableName = '', allValues: {value?: string} | undefined, meta) => {
      const fieldName = meta?.name ?? '';

      const variableValue =
        get(allValues, `${getNewVariablePrefix(fieldName)}.value`) ?? '';

      if (variableValue.trim() !== '' && variableName === '') {
        return ERRORS.EMPTY_NAME;
      }
    },
    VALIDATION_DELAY
  );

const validateModifiedNameNotDuplicate: FieldValidator<string | undefined> =
  promisifyValidator(
    (
      variableName = '',
      allValues:
        | {value?: string; newVariables?: Array<VariableFormValues>}
        | undefined,
      meta
    ) => {
      if (allValues?.newVariables === undefined) {
        return;
      }

      const isVariableDuplicate = variablesStore.state.items.some(
        ({name}) => name === variableName
      );

      if (meta?.dirty && isVariableDuplicate) {
        return ERRORS.DUPLICATE_NAME;
      }

      if (
        allValues.newVariables.filter(
          (variable) => variable?.name === variableName
        ).length <= 1
      ) {
        return;
      }

      if (
        meta?.active ||
        meta?.error === ERRORS.DUPLICATE_NAME ||
        meta?.validating
      ) {
        return ERRORS.DUPLICATE_NAME;
      }

      return;
    },
    VALIDATION_DELAY
  );

const validateNameComplete: FieldValidator<string | undefined> =
  promisifyValidator(
    (variableName = '', allValues: {value?: string} | undefined, meta) => {
      const variableValue = allValues?.value ?? '';

      if (variableValue.trim() !== '' && variableName === '') {
        return ERRORS.EMPTY_NAME;
      }

      const isVariableDuplicate = variablesStore.state.items.some(
        ({name}) => name === variableName
      );

      if (meta?.dirty && isVariableDuplicate) {
        return ERRORS.DUPLICATE_NAME;
      }
    },
    VALIDATION_DELAY
  );

const validateNameNotDuplicate: FieldValidator<string | undefined> =
  promisifyValidator((variableName = '', _, meta) => {
    const isVariableDuplicate = variablesStore.state.items.some(
      ({name}) => name === variableName
    );

    if (meta?.dirty && isVariableDuplicate) {
      return ERRORS.DUPLICATE_NAME;
    }
  }, VALIDATION_DELAY);

const validateValueComplete: FieldValidator<string | undefined> =
  promisifyValidator(
    (variableValue = '', allValues: {name?: string} | undefined) => {
      const variableName = allValues?.name ?? '';

      if (
        (variableName === '' && variableValue === '') ||
        isValidJSON(variableValue)
      ) {
        return;
      }

      return ERRORS.INVALID_VALUE;
    },
    VALIDATION_DELAY
  );

const validateModifiedValueComplete: FieldValidator<string | undefined> =
  promisifyValidator(
    (variableValue = '', allValues: {name?: string} | undefined, meta) => {
      const fieldName = meta?.name ?? '';

      const variableName =
        get(allValues, `${getNewVariablePrefix(fieldName)}.name`) ?? '';

      if (
        (variableName === '' && variableValue === '') ||
        isValidJSON(variableValue)
      ) {
        return;
      }

      return ERRORS.INVALID_VALUE;
    },
    VALIDATION_DELAY
  );

export {
  validateNameCharacters,
  validateNameComplete,
  validateNameNotDuplicate,
  validateValueComplete,
  validateModifiedNameComplete,
  validateModifiedValueComplete,
  validateModifiedNameNotDuplicate,
};
