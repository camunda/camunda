/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';
import {parseIds, parseFilterTime} from 'modules/utils/filter';
import type {DecisionsFilter} from 'modules/utils/filter/decisionsFilter';
import type {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {isValid} from 'date-fns';
import {parseDate} from '../utils/date/formatDate';
import {validateMultipleVariableValues} from './validateMultipleVariableValues';
import z from 'zod';

const ERRORS = {
  decisionsIds:
    'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1',
  ids: 'Key has to be a 16 to 19 digit number, separated by a space or a comma',
  parentInstanceId: 'Key has to be a 16 to 19 digit number',
  time: 'Time has to be in the format hh:mm:ss',
  timeRange: '"From time" is after "To time"',
  operationId: 'Id has to be a 16 to 19 digit number or a UUID',
  variables: {
    nameUnfilled: 'Name has to be filled',
    valueUnfilled: 'Value has to be filled',
    valueInvalid: 'Value has to be JSON',
    multipleValueInvalid:
      'Values have to be in JSON format, separated by comma',
  },
} as const;

const VALIDATION_TIMEOUT = 750;

const areIdsTooLong = (value: string) => {
  return parseIds(value).some((id) => id.length > 19);
};

const areDecisionIdsTooLong = (value: string) => {
  return parseIds(value).some((id) => {
    const [firstPart] = id.split('-');
    return firstPart !== undefined && firstPart.length > 20;
  });
};

const areIdsComplete = (value: string) => {
  return (
    value === '' || parseIds(value).every((id) => /^[0-9]{16,19}$/.test(id))
  );
};

const areDecisionIdsComplete = (value: string) => {
  return (
    value === '' || parseIds(value).every((id) => /^[0-9]{16,20}-\d+$/.test(id))
  );
};

const validateIdsCharacters: FieldValidator<ProcessInstanceFilters['ids']> = (
  value = '',
) => {
  if (
    value !== '' &&
    !/^[0-9]+$/g.test(value.replace(/,/g, '').replace(/\s/g, ''))
  ) {
    return ERRORS.ids;
  }
};

const validateDecisionIdsCharacters: FieldValidator<
  DecisionsFilter['decisionInstanceIds']
> = (value = '') => {
  if (
    value !== '' &&
    !/^[0-9-]+$/g.test(value.replace(/,/g, '').replace(/\s/g, ''))
  ) {
    return ERRORS.decisionsIds;
  }
};

const validateIdsLength: FieldValidator<ProcessInstanceFilters['ids']> = (
  value = '',
) => {
  if (areIdsTooLong(value)) {
    return ERRORS.ids;
  }
};

const validateDecisionIdsLength: FieldValidator<
  DecisionsFilter['decisionInstanceIds']
> = (value = '') => {
  if (areDecisionIdsTooLong(value)) {
    return ERRORS.decisionsIds;
  }
};

const validatesIdsComplete: FieldValidator<ProcessInstanceFilters['ids']> =
  promisifyValidator((value = '') => {
    if (!areIdsComplete(value)) {
      return ERRORS.ids;
    }
  }, VALIDATION_TIMEOUT);

const validatesDecisionIdsComplete: FieldValidator<
  DecisionsFilter['decisionInstanceIds']
> = promisifyValidator((value = '') => {
  if (!areDecisionIdsComplete(value)) {
    return ERRORS.decisionsIds;
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

const validateTimeComplete = promisifyValidator((value = '') => {
  if (value !== '' && !isValid(parseFilterTime(value.trim()))) {
    return ERRORS.time;
  }
}, VALIDATION_TIMEOUT);

const validateTimeRange = promisifyValidator(
  (
    _,
    allValues: {
      fromDate?: string;
      toDate?: string;
      fromTime?: string;
      toTime?: string;
    },
    meta,
  ) => {
    const {fromDate, toDate, fromTime, toTime} = allValues;

    if (
      fromDate === undefined ||
      toDate === undefined ||
      fromTime === undefined ||
      toTime === undefined
    ) {
      return undefined;
    }

    const parsedFromDate = parseDate(fromDate).getTime();
    const parsedToDate = parseDate(toDate).getTime();
    const parsedFromTime = parseFilterTime(fromTime.trim())?.getTime() ?? 0;
    const parsedToTime = parseFilterTime(toTime.trim())?.getTime() ?? 0;

    if (parsedFromDate === parsedToDate && parsedFromTime > parsedToTime) {
      // ' ' allows the field to have error indicators without error message
      return meta?.name === 'fromTime' ? ERRORS.timeRange : ' ';
    }
  },
  VALIDATION_TIMEOUT,
);

const validateTimeCharacters = (value = '') => {
  if (value !== '' && value.replace(/[0-9]|:/g, '') !== '') {
    return ERRORS.time;
  }
};

const validateVariableNameCharacters: FieldValidator<string | undefined> = (
  variableName = '',
) => {
  if (variableName.includes('"') || variableName.match(new RegExp('[\\s]+'))) {
    return 'Name is invalid';
  }

  return;
};

const validateVariableNameComplete: FieldValidator<
  ProcessInstanceFilters['variableName']
> = promisifyValidator(
  (variableName = '', allValues: {variableValues?: string} | undefined) => {
    const variableValues = allValues?.variableValues ?? '';

    if ((variableName === '' && variableValues === '') || variableName !== '') {
      return undefined;
    }

    return ERRORS.variables.nameUnfilled;
  },
  VALIDATION_TIMEOUT,
);

const validateVariableValuesComplete: FieldValidator<
  ProcessInstanceFilters['variableValues']
> = promisifyValidator(
  (variableValues = '', allValues: {variableName?: string} | undefined) => {
    const variableName = allValues?.variableName ?? '';

    if (
      (variableName === '' && variableValues === '') ||
      variableValues !== ''
    ) {
      return;
    }

    return ERRORS.variables.valueUnfilled;
  },
  VALIDATION_TIMEOUT,
);

const validateVariableValueValid: FieldValidator<
  ProcessInstanceFilters['variableValues']
> = promisifyValidator((variableValue = '') => {
  if (variableValue === '' || isValidJSON(variableValue)) {
    return undefined;
  }

  return ERRORS.variables.valueInvalid;
}, VALIDATION_TIMEOUT);

const validateMultipleVariableValuesValid: FieldValidator<
  ProcessInstanceFilters['variableValues']
> = promisifyValidator((variableValues = '') => {
  if (validateMultipleVariableValues(variableValues)) {
    return undefined;
  }

  return ERRORS.variables.multipleValueInvalid;
}, VALIDATION_TIMEOUT);

/**
 * Validates if value contains only characters from a key or UUID
 */
const validateOperationIdCharacters: FieldValidator<
  ProcessInstanceFilters['operationId']
> = (value = '') => {
  const schema = z.union([
    z.string().length(0),
    z.string().regex(/^[0-9]+$/),
    z.string().regex(/^[a-f0-9-]{1,36}/),
  ]);

  if (!schema.safeParse(value).success) {
    return ERRORS.operationId;
  }
};

/**
 * Validates if value is a complete key (16-19 characters) or a complete UUID
 */
const validateOperationIdComplete: FieldValidator<
  ProcessInstanceFilters['operationId']
> = promisifyValidator((value = '') => {
  const schema = z.union([
    z.string().length(0),
    z.string().regex(/^[0-9]{16,19}$/),
    z.uuid(),
  ]);

  if (!schema.safeParse(value).success) {
    return ERRORS.operationId;
  }
}, VALIDATION_TIMEOUT);

export {
  validateIdsCharacters,
  validateIdsLength,
  validatesIdsComplete,
  validateParentInstanceIdCharacters,
  validateParentInstanceIdComplete,
  validateParentInstanceIdNotTooLong,
  validateTimeComplete,
  validateTimeCharacters,
  validateOperationIdCharacters,
  validateOperationIdComplete,
  validateVariableNameCharacters,
  validateVariableNameComplete,
  validateVariableValuesComplete,
  validateVariableValueValid,
  validateMultipleVariableValuesValid,
  validateDecisionIdsCharacters,
  validateDecisionIdsLength,
  validatesDecisionIdsComplete,
  validateTimeRange,
  ERRORS,
};
