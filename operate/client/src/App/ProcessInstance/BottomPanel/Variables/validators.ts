/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {ERRORS, VALIDATION_DELAY} from './constants';
import get from 'lodash/get';
import {getNewVariablePrefix} from './getNewVariablePrefix';
import {type VariableFormValues} from 'modules/types/variables';
import type {Variable} from '@vzeta/camunda-api-zod-schemas';
import {variablesStore} from 'modules/stores/variables';

const validateNameCharacters: FieldValidator<string | undefined> = (
  variableName = '',
) => {
  if (variableName.includes('"') || variableName.includes(' ')) {
    return ERRORS.INVALID_NAME;
  }
};

const validateModifiedNameComplete: FieldValidator<string | undefined> = (
  variableName = '',
  allValues: {value?: string} | undefined,
  meta,
) => {
  const fieldName = meta?.name ?? '';

  const variableValue =
    get(allValues, `${getNewVariablePrefix(fieldName)}.value`) ?? '';

  if (variableValue.trim() !== '' && variableName === '') {
    return ERRORS.EMPTY_NAME;
  }
};

const validateModifiedNameNotDuplicateDeprecated: FieldValidator<
  string | undefined
> = (
  variableName = '',
  allValues:
    | {value?: string; newVariables?: Array<VariableFormValues>}
    | undefined,
  meta,
) => {
  if (allValues?.newVariables === undefined) {
    return;
  }

  const isVariableDuplicate = variablesStore.state.items.some(
    ({name}) => name === variableName,
  );

  if (meta?.dirty && isVariableDuplicate) {
    return ERRORS.DUPLICATE_NAME;
  }

  if (
    allValues.newVariables.filter((variable) => variable?.name === variableName)
      .length <= 1
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
};

const validateModifiedNameNotDuplicate =
  (allVariables: Variable[]): FieldValidator<string | undefined> =>
  (
    variableName = '',
    allValues:
      | {value?: string; newVariables?: Array<VariableFormValues>}
      | undefined,
    meta,
  ) => {
    if (allValues?.newVariables === undefined) {
      return;
    }

    const isVariableDuplicate = allVariables.some(
      ({name}) => name === variableName,
    );

    if (meta?.dirty && isVariableDuplicate) {
      return ERRORS.DUPLICATE_NAME;
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
      meta?.error === ERRORS.DUPLICATE_NAME ||
      meta?.validating
    ) {
      return ERRORS.DUPLICATE_NAME;
    }

    return;
  };

const validateNameCompleteDeprecated: FieldValidator<string | undefined> =
  promisifyValidator(
    (variableName = '', allValues: {value?: string} | undefined, meta) => {
      const variableValue = allValues?.value ?? '';

      if (variableValue.trim() !== '' && variableName === '') {
        return ERRORS.EMPTY_NAME;
      }

      const isVariableDuplicate = variablesStore.state.items.some(
        ({name}) => name === variableName,
      );

      if (meta?.dirty && isVariableDuplicate) {
        return ERRORS.DUPLICATE_NAME;
      }
    },
    VALIDATION_DELAY,
  );

const validateNameComplete =
  (allVariables: Variable[]): FieldValidator<string | undefined> =>
  (variableName = '', allValues: {value?: string} | undefined, meta) => {
    const variableValue = allValues?.value ?? '';

    if (variableValue.trim() !== '' && variableName === '') {
      return ERRORS.EMPTY_NAME;
    }

    const isVariableDuplicate = allVariables.some(
      ({name}) => name === variableName,
    );

    if (meta?.dirty && isVariableDuplicate) {
      return ERRORS.DUPLICATE_NAME;
    }
  };

const validateNameNotDuplicate = (
  allVariables: Variable[],
): FieldValidator<string | undefined> =>
  promisifyValidator((variableName = '', _, meta) => {
    const isVariableDuplicate = allVariables.some(
      ({name}) => name === variableName,
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
        variableValue !== ''
      ) {
        return;
      }

      return ERRORS.EMPTY_VALUE;
    },
    VALIDATION_DELAY,
  );

const validateValueValid: FieldValidator<string | undefined> =
  promisifyValidator((variableValue = '') => {
    if (variableValue === '' || isValidJSON(variableValue)) {
      return;
    }

    return ERRORS.INVALID_VALUE;
  }, VALIDATION_DELAY);

const validateModifiedValueComplete: FieldValidator<string | undefined> = (
  variableValue = '',
  allValues: {name?: string} | undefined,
  meta,
) => {
  if (!meta?.visited) {
    return undefined;
  }

  const fieldName = meta?.name ?? '';

  const variableName =
    get(allValues, `${getNewVariablePrefix(fieldName)}.name`) ?? '';

  if ((variableName === '' && variableValue === '') || variableValue !== '') {
    return;
  }

  return ERRORS.EMPTY_VALUE;
};

const validateModifiedValueValid: FieldValidator<string | undefined> = (
  variableValue = '',
) => {
  if (variableValue === '' || isValidJSON(variableValue)) {
    return;
  }

  return ERRORS.INVALID_VALUE;
};

export {
  validateNameCharacters,
  validateNameComplete,
  validateNameCompleteDeprecated,
  validateNameNotDuplicate,
  validateValueComplete,
  validateValueValid,
  validateModifiedNameComplete,
  validateModifiedValueComplete,
  validateModifiedValueValid,
  validateModifiedNameNotDuplicate,
  validateModifiedNameNotDuplicateDeprecated,
};
