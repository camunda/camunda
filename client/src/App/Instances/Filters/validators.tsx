/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';
import {
  parseIds,
  parseFilterDate,
  ProcessInstanceFilters,
} from 'modules/utils/filter';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {isValid} from 'date-fns';

const ERRORS = {
  ids: 'Id has to be 16 to 19 digit numbers, separated by space or comma',
  parentInstanceId: 'Id has to be 16 to 19 digit numbers',
  date: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
  operationId: 'Id has to be a UUID',
  variables: {
    nameUnfilled: 'Variable has to be filled',
    valueInvalid: 'Value has to be JSON',
    bothInvalid: 'Variable has to be filled and Value has to be JSON',
  },
} as const;

const VALIDATION_TIMEOUT = 750;

const areIdsTooLong = (value: string) => {
  return parseIds(value).some((id) => id.length > 19);
};

const areIdsComplete = (value: string) => {
  return (
    value === '' || parseIds(value).every((id) => /^[0-9]{16,19}$/.test(id))
  );
};

const validateIdsCharacters: FieldValidator<ProcessInstanceFilters['ids']> = (
  value = ''
) => {
  if (
    value !== '' &&
    !/^[0-9]+$/g.test(value.replace(/,/g, '').replace(/\s/g, ''))
  ) {
    return ERRORS.ids;
  }
};

const validateIdsNotTooLong: FieldValidator<ProcessInstanceFilters['ids']> = (
  value = ''
) => {
  if (areIdsTooLong(value)) {
    return ERRORS.ids;
  }
};

const validatesIdsComplete: FieldValidator<ProcessInstanceFilters['ids']> =
  promisifyValidator((value = '') => {
    if (!areIdsComplete(value)) {
      return ERRORS.ids;
    }
  }, VALIDATION_TIMEOUT);

const validateParentInstanceIdCharacters: FieldValidator<
  ProcessInstanceFilters['parentInstanceId']
> = (value = '') => {
  if (value !== '' && !/^[0-9]+$/.test(value)) {
    return ERRORS.parentInstanceId;
  }
};

const validateParentInstanceIdComplete: FieldValidator<
  ProcessInstanceFilters['parentInstanceId']
> = promisifyValidator((value = '') => {
  if (!areIdsComplete(value)) {
    return ERRORS.parentInstanceId;
  }
}, VALIDATION_TIMEOUT);

const validateParentInstanceIdNotTooLong: FieldValidator<
  ProcessInstanceFilters['parentInstanceId']
> = (value = '') => {
  if (areIdsTooLong(value)) {
    return ERRORS.parentInstanceId;
  }
};

const validateDateComplete: FieldValidator<
  ProcessInstanceFilters['startDate'] | ProcessInstanceFilters['endDate']
> = promisifyValidator((value = '') => {
  if (value !== '' && !isValid(parseFilterDate(value.trim()))) {
    return ERRORS.date;
  }
}, VALIDATION_TIMEOUT);

const validateDateCharacters: FieldValidator<
  ProcessInstanceFilters['startDate'] | ProcessInstanceFilters['endDate']
> = (value = '') => {
  if (value !== '' && value.replace(/[0-9]|\s|:|-/g, '') !== '') {
    return ERRORS.date;
  }
};

const validateVariableNameCharacters: FieldValidator<string | undefined> = (
  variableName = ''
) => {
  if (variableName.includes('"') || variableName.match(new RegExp('[\\s]+'))) {
    return 'Name is invalid';
  }

  return;
};

const validateVariableNameComplete: FieldValidator<
  ProcessInstanceFilters['variableName']
> = promisifyValidator(
  (variableName = '', allValues: {variableValue?: string}) => {
    const variableValue = allValues.variableValue ?? '';

    if ((variableName === '' && variableValue === '') || variableName !== '') {
      return undefined;
    }

    return ERRORS.variables.nameUnfilled;
  },
  VALIDATION_TIMEOUT
);

const validateVariableValueComplete: FieldValidator<
  ProcessInstanceFilters['variableValue']
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
      return ERRORS.variables.valueInvalid;
    }
  },
  VALIDATION_TIMEOUT
);

const validateOperationIdCharacters: FieldValidator<
  ProcessInstanceFilters['operationId']
> = (value = '') => {
  if (value !== '' && !/^[a-f0-9-]{1,36}/.test(value)) {
    return ERRORS.operationId;
  }
};

const validateOperationIdComplete: FieldValidator<
  ProcessInstanceFilters['operationId']
> = promisifyValidator((value = '') => {
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
  validateParentInstanceIdCharacters,
  validateParentInstanceIdComplete,
  validateParentInstanceIdNotTooLong,
  validateDateCharacters,
  validateDateComplete,
  validateOperationIdCharacters,
  validateOperationIdComplete,
  validateVariableNameCharacters,
  validateVariableNameComplete,
  validateVariableValueComplete,
};
