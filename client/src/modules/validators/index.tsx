/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';
import {
  parseIds,
  ProcessInstanceFilters,
  DecisionInstanceFilters,
  parseFilterTime,
} from 'modules/utils/filter';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {isValid} from 'date-fns';
import {parseDate} from '../utils/date/formatDate';

const ERRORS = {
  decisionsIds:
    'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1',
  ids: 'Key has to be a 16 to 19 digit number, separated by space or comma',
  parentInstanceId: 'Key has to be a 16 to 19 digit number',
  date: 'Date has to be in format YYYY-MM-DD hh:mm:ss',
  time: 'Time has to be in format hh:mm:ss',
  timeRange: '"From time" is after "To time"',
  operationId: 'Id has to be a UUID',
  variables: {
    nameUnfilled: 'Name has to be filled',
    valueUnfilled: 'Value has to be filled',
    valueInvalid: 'Value has to be JSON',
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
  value = ''
) => {
  if (
    value !== '' &&
    !/^[0-9]+$/g.test(value.replace(/,/g, '').replace(/\s/g, ''))
  ) {
    return ERRORS.ids;
  }
};

const validateDecisionIdsCharacters: FieldValidator<
  DecisionInstanceFilters['decisionInstanceIds']
> = (value = '') => {
  if (
    value !== '' &&
    !/^[0-9-]+$/g.test(value.replace(/,/g, '').replace(/\s/g, ''))
  ) {
    return ERRORS.decisionsIds;
  }
};

const validateIdsLength: FieldValidator<ProcessInstanceFilters['ids']> = (
  value = ''
) => {
  if (areIdsTooLong(value)) {
    return ERRORS.ids;
  }
};

const validateDecisionIdsLength: FieldValidator<
  DecisionInstanceFilters['decisionInstanceIds']
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
  DecisionInstanceFilters['decisionInstanceIds']
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
    meta
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
  VALIDATION_TIMEOUT
);

const validateTimeCharacters = (value = '') => {
  if (value !== '' && value.replace(/[0-9]|:/g, '') !== '') {
    return ERRORS.time;
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
  (variableName = '', allValues: {variableValue?: string} | undefined) => {
    const variableValue = allValues?.variableValue ?? '';

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
  (variableValue = '', allValues: {variableName?: string} | undefined) => {
    const variableName = allValues?.variableName ?? '';

    if ((variableName === '' && variableValue === '') || variableValue !== '') {
      return;
    }

    return ERRORS.variables.valueUnfilled;
  },
  VALIDATION_TIMEOUT
);

const validateVariableValueValid: FieldValidator<
  ProcessInstanceFilters['variableValue']
> = promisifyValidator((variableValue = '') => {
  if (variableValue === '' || isValidJSON(variableValue)) {
    return undefined;
  }

  return ERRORS.variables.valueInvalid;
}, VALIDATION_TIMEOUT);

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
  validateVariableValueComplete,
  validateVariableValueValid,
  validateDecisionIdsCharacters,
  validateDecisionIdsLength,
  validatesDecisionIdsComplete,
  validateTimeRange,
};
