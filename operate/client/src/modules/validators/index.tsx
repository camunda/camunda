/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {FieldValidator} from 'final-form';
import {isValidJSON} from 'modules/utils';
import {
  parseIds,
  DecisionInstanceFilters,
  parseFilterTime,
} from 'modules/utils/filter';
import {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {promisifyValidator} from 'modules/utils/validators/promisifyValidator';
import {isValid} from 'date-fns';
import {parseDate} from '../utils/date/formatDate';
import {validateMultipleVariableValues} from './validateMultipleVariableValues';

const ERRORS = {
  decisionsIds:
    'Key has to be a 16 to 20 digit number with an index, e.g. 2251799813702856-1',
  ids: 'Key has to be a 16 to 19 digit number, separated by a space or a comma',
  parentInstanceId: 'Key has to be a 16 to 19 digit number',
  time: 'Time has to be in the format hh:mm:ss',
  timeRange: '"From time" is after "To time"',
  operationId: 'Id has to be a UUID',
  variables: {
    nameUnfilled: 'Name has to be filled',
    valueUnfilled: 'Value has to be filled',
    valueInvalid: 'Value has to be JSON',
    mulipleValueInvalid: 'Values have to be in JSON format, separated by comma',
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
  value = '',
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

  return ERRORS.variables.mulipleValueInvalid;
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
      value,
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
  validateVariableValuesComplete,
  validateVariableValueValid,
  validateMultipleVariableValuesValid,
  validateDecisionIdsCharacters,
  validateDecisionIdsLength,
  validatesDecisionIdsComplete,
  validateTimeRange,
  ERRORS,
};
