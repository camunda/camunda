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

const validateNameCharacters: FieldValidator<string | undefined> = (
  variableName = ''
) => {
  if (variableName.includes('"') || variableName.includes(' ')) {
    return ERRORS.INVALID_NAME;
  }
};

const validateNameComplete: FieldValidator<string | undefined> =
  promisifyValidator((variableName = '', allValues: {value?: string}, meta) => {
    const variableValue = allValues.value ?? '';

    if (variableValue.trim() !== '' && variableName === '') {
      return ERRORS.EMPTY_NAME;
    }

    const isVariableDuplicate =
      variablesStore.state.items
        .map(({name}) => name)
        .filter((name) => name === variableName).length > 0;

    if (meta?.dirty && isVariableDuplicate) {
      return ERRORS.DUPLICATE_NAME;
    }
  }, VALIDATION_DELAY);

const validateValueComplete: FieldValidator<string | undefined> =
  promisifyValidator((variableValue = '', allValues: {name?: string}) => {
    const variableName = allValues.name ?? '';

    if (
      (variableName === '' && variableValue === '') ||
      isValidJSON(variableValue)
    ) {
      return;
    }

    return ERRORS.INVALID_VALUE;
  }, VALIDATION_DELAY);

export {validateNameCharacters, validateNameComplete, validateValueComplete};
