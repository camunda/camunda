/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {addDays, startOfDay, addMinutes, format, parse} from 'date-fns';
import {processesStore} from 'modules/stores/processes';
import {getSearchString} from 'modules/utils/getSearchString';
import {Location} from 'history';

type ProcessInstanceFilterField =
  | 'process'
  | 'version'
  | 'ids'
  | 'parentInstanceId'
  | 'errorMessage'
  | 'startDate'
  | 'endDate'
  | 'flowNodeId'
  | 'variableName'
  | 'variableValue'
  | 'operationId'
  | 'active'
  | 'incidents'
  | 'completed'
  | 'canceled';

type DecisionInstanceFilterField =
  | 'name'
  | 'version'
  | 'evaluated'
  | 'failed'
  | 'decisionInstanceIds'
  | 'processInstanceId'
  | 'evaluationDate';

type ProcessInstanceFilters = {
  process?: string;
  version?: string;
  ids?: string;
  parentInstanceId?: string;
  errorMessage?: string;
  startDate?: string;
  endDate?: string;
  flowNodeId?: string;
  variableName?: string;
  variableValue?: string;
  operationId?: string;
  active?: boolean;
  incidents?: boolean;
  completed?: boolean;
  canceled?: boolean;
};

type DecisionInstanceFilters = {
  name?: string;
  version?: string;
  evaluated?: boolean;
  failed?: boolean;
  decisionInstanceIds?: string;
  processInstanceId?: string;
  evaluationDate?: string;
};

type RequestFilters = {
  running?: boolean;
  active?: boolean;
  incidents?: boolean;
  finished?: boolean;
  canceled?: boolean;
  completed?: boolean;
  activityId?: string;
  batchOperationId?: string;
  endDateAfter?: string;
  endDateBefore?: string;
  errorMessage?: string;
  ids?: string[];
  parentInstanceId?: string;
  startDateAfter?: string;
  startDateBefore?: string;
  variable?: {
    name: string;
    value: string;
  };
  processIds?: string[];
};

type DecisionRequestFilters = {
  completed?: boolean;
  failed?: boolean;
  ids?: string[];
  processInstanceId?: string;
  evaluationDateAfter?: string;
  evaluationDateBefore?: string;
  decisionIds?: string[];
};

const PROCESS_INSTANCE_FILTER_FIELDS: ProcessInstanceFilterField[] = [
  'process',
  'version',
  'ids',
  'parentInstanceId',
  'errorMessage',
  'startDate',
  'endDate',
  'flowNodeId',
  'variableName',
  'variableValue',
  'operationId',
  'active',
  'incidents',
  'completed',
  'canceled',
];
const DECISION_INSTANCE_FILTER_FIELDS: DecisionInstanceFilterField[] = [
  'name',
  'version',
  'evaluated',
  'failed',
  'decisionInstanceIds',
  'processInstanceId',
  'evaluationDate',
];

const BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS: ProcessInstanceFilterField[] = [
  'active',
  'incidents',
  'completed',
  'canceled',
];

const BOOLEAN_DECISION_INSTANCE_FILTER_FIELDS: DecisionInstanceFilterField[] = [
  'failed',
  'evaluated',
];

function getFilters<Fields extends string, Filters>(
  searchParams: string,
  fields: Fields[],
  booleanFields: string[]
): Filters {
  return Array.from(new URLSearchParams(searchParams)).reduce(
    (accumulator, [param, value]) => {
      if (booleanFields.includes(param)) {
        return {
          ...accumulator,
          [param]: value === 'true',
        };
      }

      if (fields.includes(param as Fields)) {
        return {
          ...accumulator,
          [param]: value,
        };
      }

      return accumulator;
    },
    {}
  ) as Filters;
}

function getProcessInstanceFilters(
  searchParams: string
): ProcessInstanceFilters {
  return getFilters<ProcessInstanceFilterField, ProcessInstanceFilters>(
    searchParams,
    PROCESS_INSTANCE_FILTER_FIELDS,
    BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS
  );
}

function getDecisionInstanceFilters(
  searchParams: string
): DecisionInstanceFilters {
  return getFilters<DecisionInstanceFilterField, DecisionInstanceFilters>(
    searchParams,
    DECISION_INSTANCE_FILTER_FIELDS,
    BOOLEAN_DECISION_INSTANCE_FILTER_FIELDS
  );
}

function deleteSearchParams(location: Location, paramsToDelete: string[]) {
  const params = new URLSearchParams(location.search);

  paramsToDelete.forEach((param) => {
    params.delete(param);
  });

  return {
    ...location,
    search: params.toString(),
  };
}

type GetRequestDatePairReturn =
  | {
      startDateBefore: string;
      startDateAfter: string;
    }
  | {
      endDateBefore: string;
      endDateAfter: string;
    };

function getRequestDatePair(
  date: Date,
  type: 'startDate' | 'endDate'
): GetRequestDatePairReturn {
  const DATE_REQUEST_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSxx";
  const hasTime = date.getHours() + date.getMinutes() + date.getSeconds() !== 0;
  const dateAfter = format(
    hasTime ? date : startOfDay(date),
    DATE_REQUEST_FORMAT
  );
  const dateBefore = format(
    hasTime ? addMinutes(date, 1) : addDays(startOfDay(date), 1),
    DATE_REQUEST_FORMAT
  );

  if (type === 'startDate') {
    return {
      startDateBefore: dateBefore,
      startDateAfter: dateAfter,
    };
  }

  return {
    endDateBefore: dateBefore,
    endDateAfter: dateAfter,
  };
}

function parseIds(value: string) {
  return value
    .trim()
    .replace(/,\s/g, '|')
    .replace(/\s{1,}/g, '|')
    .replace(/,{1,}/g, '|')
    .split('|');
}

function parseFilterDate(value: string) {
  const DATE_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
  const DATE_HOUR_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}\s[0-9]{2}$/;
  const DATE_HOUR_MINUTES_PATTERN =
    /^[0-9]{4}-[0-9]{2}-[0-9]{2}\s[0-9]{2}:[0-9]{2}$/;
  const DATE_TIME_PATTERN_WITH =
    /^[0-9]{4}-[0-9]{2}-[0-9]{2}\s[0-9]{2}:[0-9]{2}:[0-9]{2}$/;
  const trimmedValue = value.trim();

  if (DATE_PATTERN.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd', new Date());
  }

  if (DATE_HOUR_PATTERN.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd kk', new Date());
  }

  if (DATE_HOUR_MINUTES_PATTERN.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd kk:mm', new Date());
  }

  if (DATE_TIME_PATTERN_WITH.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd kk:mm:ss', new Date());
  }
}

function getProcessIds(process: string, processVersion: string) {
  if (processVersion === 'all') {
    return processesStore.versionsByProcess?.[process]?.map(({id}) => id) ?? [];
  }

  return (
    processesStore.versionsByProcess?.[process]
      ?.filter(({version}) => version === parseInt(processVersion))
      ?.map(({id}) => id) ?? []
  );
}

function getProcessInstancesRequestFilters(): RequestFilters {
  const filters = getProcessInstanceFilters(getSearchString());

  return Object.entries(filters).reduce<RequestFilters>(
    (accumulator, [key, value]): RequestFilters => {
      if (value === undefined) {
        return accumulator;
      }

      if (typeof value === 'boolean') {
        if (['active', 'incidents'].includes(key)) {
          return {
            ...accumulator,
            [key]: value,
            ...(value === true ? {running: true} : {}),
          };
        }

        if (['canceled', 'completed'].includes(key)) {
          return {
            ...accumulator,
            [key]: value,
            ...(value === true ? {finished: true} : {}),
          };
        }
      } else {
        if (key === 'errorMessage') {
          return {
            ...accumulator,
            errorMessage: value,
          };
        }

        if (key === 'flowNodeId') {
          return {
            ...accumulator,
            activityId: value,
          };
        }

        if (key === 'operationId') {
          return {
            ...accumulator,
            batchOperationId: value,
          };
        }

        if (key === 'ids') {
          return {
            ...accumulator,
            ids: parseIds(value),
          };
        }

        if (key === 'parentInstanceId') {
          return {...accumulator, parentInstanceId: value};
        }

        if (
          key === 'version' &&
          filters.process !== undefined &&
          value !== undefined
        ) {
          const processIds = getProcessIds(filters.process, value);

          if (processIds.length > 0) {
            return {
              ...accumulator,
              processIds,
            };
          }
        }

        if (
          (key === 'variableName' || key === 'variableValue') &&
          filters.variableName !== undefined &&
          filters.variableValue !== undefined
        ) {
          return {
            ...accumulator,
            variable: {
              name: filters.variableName,
              value: filters.variableValue,
            },
          };
        }

        const parsedDate = parseFilterDate(value);
        if (
          (key === 'startDate' || key === 'endDate') &&
          parsedDate !== undefined
        ) {
          return {
            ...accumulator,
            ...getRequestDatePair(parsedDate, key),
          };
        }
      }

      return accumulator;
    },
    {}
  );
}

function updateFiltersSearchString<Filters extends object>(
  currentSearch: string,
  newFilters: Filters,
  possibleFilters: Array<keyof Filters>,
  possibleBooleanFilters: Array<keyof Filters>
) {
  const oldParams = Object.fromEntries(new URLSearchParams(currentSearch));
  const fieldsToDelete = possibleFilters.filter(
    (field) => newFilters[field] === undefined
  );
  const newParams = new URLSearchParams(
    Object.entries({
      ...oldParams,
      ...newFilters,
    }).filter(([, value]) => value !== '')
  );

  fieldsToDelete.forEach((field) => {
    if (newParams.has(field.toString())) {
      newParams.delete(field.toString());
    }
  });

  possibleBooleanFilters.forEach((field) => {
    if (newParams.get(field.toString()) === 'false') {
      newParams.delete(field.toString());
    }
  });

  return newParams.toString();
}

function updateProcessFiltersSearchString(
  currentSearch: string,
  newFilters: ProcessInstanceFilters
) {
  return updateFiltersSearchString<ProcessInstanceFilters>(
    currentSearch,
    newFilters,
    PROCESS_INSTANCE_FILTER_FIELDS,
    BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS
  );
}

function updateDecisionsFiltersSearchString(
  currentSearch: string,
  newFilters: DecisionInstanceFilters
) {
  return updateFiltersSearchString<DecisionInstanceFilters>(
    currentSearch,
    newFilters,
    DECISION_INSTANCE_FILTER_FIELDS,
    BOOLEAN_DECISION_INSTANCE_FILTER_FIELDS
  );
}

function getSortParams(): {
  sortBy: string;
  sortOrder: 'asc' | 'desc';
} | null {
  const params = new URLSearchParams(getSearchString());
  const sort = params.get('sort');
  const PARAM_PATTERN = /^\w{1,}\+(asc|desc)/;

  if (sort !== null && PARAM_PATTERN.test(sort)) {
    const [sortBy, sortOrder] = sort.split('+');

    return {
      sortBy,
      sortOrder,
    } as {sortBy: string; sortOrder: 'asc' | 'desc'};
  }

  return null;
}

export {
  getProcessInstanceFilters,
  parseIds,
  parseFilterDate,
  getProcessInstancesRequestFilters,
  updateProcessFiltersSearchString,
  updateDecisionsFiltersSearchString,
  deleteSearchParams,
  getSortParams,
  getDecisionInstanceFilters,
};
export type {
  ProcessInstanceFilters,
  ProcessInstanceFilterField,
  RequestFilters,
  DecisionRequestFilters,
  DecisionInstanceFilters,
};
