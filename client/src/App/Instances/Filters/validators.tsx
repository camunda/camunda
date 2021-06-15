/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';
import {parseIds, parseFilterDate, FiltersType} from 'modules/utils/filter';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {isValid} from 'date-fns';

const ERRORS = {
  ids: 'Id has to be 16 to 19 digit numbers, separated by space or comma',
  date: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
  operationId: 'Id has to be a UUID',
  variables: {
    nameUnfilled: 'Variable has to be filled',
    valueInvalid: 'Value has to be JSON',
    bothInvalid: 'Variable has to be filled and Value has to be JSON',
  },
} as const;

const VALIDATION_TIMEOUT = 750;

const validateIdsCharacters: FieldValidator<FiltersType['ids']> = (
  value = ''
) => {
  if (
    value !== '' &&
    !/^[0-9]+$/g.test(value.replace(/,/g, '').replace(/\s/g, ''))
  ) {
    return ERRORS.ids;
  }
};

const validateIdsNotTooLong: FieldValidator<FiltersType['ids']> = (
  value = ''
) => {
  const hasTooLongIds = parseIds(value).some((item) => item.length > 19);
  if (hasTooLongIds) {
    return ERRORS.ids;
  }
};

const validatesIdsComplete: FieldValidator<FiltersType['ids']> =
  promisifyValidator((value = '') => {
    if (
      value !== '' &&
      !parseIds(value).every((id) => /^[0-9]{16,19}$/.test(id))
    ) {
      return ERRORS.ids;
    }
  }, VALIDATION_TIMEOUT);

const validateDateComplete: FieldValidator<
  FiltersType['startDate'] | FiltersType['endDate']
> = promisifyValidator((value = '') => {
  if (value !== '' && !isValid(parseFilterDate(value.trim()))) {
    return ERRORS.date;
  }
}, VALIDATION_TIMEOUT);

const validateDateCharacters: FieldValidator<
  FiltersType['startDate'] | FiltersType['endDate']
> = (value = '') => {
  if (value !== '' && value.replace(/[0-9]|\s|:|-/g, '') !== '') {
    return ERRORS.date;
  }
};

const validateVariableNameComplete: FieldValidator<
  FiltersType['variableName']
> = promisifyValidator(
  (variableName = '', allValues: {variableValue?: string}) => {
    const variableValue = allValues.variableValue ?? '';

    if ((variableName === '' && variableValue === '') || variableName !== '') {
      return undefined;
    }

    return isValidJSON(variableValue)
      ? `${ERRORS.variables.nameUnfilled}`
      : `${ERRORS.variables.bothInvalid}`;
  },
  VALIDATION_TIMEOUT
);

const validateVariableValueComplete: FieldValidator<
  FiltersType['variableValue']
> = promisifyValidator(
  (variableValue = '', allValues: {variableName?: string}) => {
    const variableName = allValues.variableName ?? '';

    if (variableName === '' && variableValue === '') {
      return undefined;
    }

    if (variableName !== '' && isValidJSON(variableValue)) {
      return undefined;
    }

    if (!isValidJSON(variableValue)) {
      return variableName === ''
        ? `${ERRORS.variables.bothInvalid}`
        : `${ERRORS.variables.valueInvalid}`;
    }
  },
  VALIDATION_TIMEOUT
);

const validateOperationIdCharacters: FieldValidator<
  FiltersType['operationId']
> = (value = '') => {
  if (value !== '' && !/^[a-f0-9-]{1,36}/.test(value)) {
    return ERRORS.operationId;
  }
};

const validateOperationIdComplete: FieldValidator<FiltersType['operationId']> =
  promisifyValidator((value = '') => {
    if (
      value !== '' &&
      !/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}$/.test(
        value
      )
    ) {
      return ERRORS.operationId;
    }
  }, VALIDATION_TIMEOUT);

export {
  validateIdsCharacters,
  validateIdsNotTooLong,
  validatesIdsComplete,
  validateDateCharacters,
  validateDateComplete,
  validateOperationIdCharacters,
  validateOperationIdComplete,
  validateVariableNameComplete,
  validateVariableValueComplete,
};
