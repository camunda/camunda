/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {parse, isValid} from 'date-fns';
import {processesStore} from 'modules/stores/processes';
import {getSearchString} from 'modules/utils/getSearchString';
import {Location} from 'react-router-dom';
import {groupedDecisionsStore} from 'modules/stores/groupedDecisions';

type ProcessInstanceFilterField =
  | 'process'
  | 'version'
  | 'ids'
  | 'parentInstanceId'
  | 'errorMessage'
  | 'flowNodeId'
  | 'variableName'
  | 'variableValue'
  | 'operationId'
  | 'active'
  | 'incidents'
  | 'completed'
  | 'canceled'
  | 'startDateAfter'
  | 'startDateBefore'
  | 'endDateAfter'
  | 'endDateBefore';

type DecisionInstanceFilterField =
  | 'name'
  | 'version'
  | 'evaluated'
  | 'failed'
  | 'decisionInstanceIds'
  | 'processInstanceId'
  | 'evaluationDateBefore'
  | 'evaluationDateAfter';

type ProcessInstanceFilters = {
  process?: string;
  version?: string;
  ids?: string;
  parentInstanceId?: string;
  errorMessage?: string;
  flowNodeId?: string;
  variableName?: string;
  variableValue?: string;
  operationId?: string;
  active?: boolean;
  incidents?: boolean;
  completed?: boolean;
  canceled?: boolean;
  startDateAfter?: string;
  startDateBefore?: string;
  endDateAfter?: string;
  endDateBefore?: string;
};

type DecisionInstanceFilters = {
  name?: string;
  version?: string;
  evaluated?: boolean;
  failed?: boolean;
  decisionInstanceIds?: string;
  processInstanceId?: string;
  evaluationDateBefore?: string;
  evaluationDateAfter?: string;
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
  evaluated?: boolean;
  failed?: boolean;
  ids?: string[];
  processInstanceId?: string;
  evaluationDateAfter?: string;
  evaluationDateBefore?: string;
  decisionDefinitionIds?: string[];
};

const PROCESS_INSTANCE_FILTER_FIELDS: ProcessInstanceFilterField[] = [
  'process',
  'version',
  'ids',
  'parentInstanceId',
  'errorMessage',
  'flowNodeId',
  'variableName',
  'variableValue',
  'operationId',
  'active',
  'incidents',
  'completed',
  'canceled',
  'startDateAfter',
  'startDateBefore',
  'endDateAfter',
  'endDateBefore',
];
const DECISION_INSTANCE_FILTER_FIELDS: DecisionInstanceFilterField[] = [
  'name',
  'version',
  'evaluated',
  'failed',
  'decisionInstanceIds',
  'processInstanceId',
  'evaluationDateAfter',
  'evaluationDateBefore',
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

function parseIds(value: string) {
  return value
    .trim()
    .replace(/,\s/g, '|')
    .replace(/\s{1,}/g, '|')
    .replace(/,{1,}/g, '|')
    .split('|');
}

function parseFilterTime(value: string) {
  const HOUR_MINUTES_PATTERN = /^[0-9]{2}:[0-9]{2}$/;
  const HOUR_MINUTES_SECONDS_PATTERN = /^[0-9]{2}:[0-9]{2}:[0-9]{2}$/;

  if (HOUR_MINUTES_PATTERN.test(value)) {
    const parsedDate = parse(value, 'HH:mm', new Date());
    return isValid(parsedDate) ? parsedDate : undefined;
  }

  if (HOUR_MINUTES_SECONDS_PATTERN.test(value)) {
    const parsedDate = parse(value, 'HH:mm:ss', new Date());
    return isValid(parsedDate) ? parsedDate : undefined;
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

function getDecisionIds(name: string, decisionVersion: string) {
  if (decisionVersion === 'all') {
    return (
      groupedDecisionsStore.decisionVersionsById[name]?.map(({id}) => id) ?? []
    );
  }

  return (
    groupedDecisionsStore.decisionVersionsById?.[name]
      ?.filter(({version}) => version === parseInt(decisionVersion))
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

        if (
          [
            'startDateAfter',
            'startDateBefore',
            'endDateAfter',
            'endDateBefore',
          ].includes(key) &&
          value !== undefined
        ) {
          return {
            ...accumulator,
            [key]: value,
          };
        }
      }

      return accumulator;
    },
    {}
  );
}

function getDecisionInstancesRequestFilters() {
  const filters = getDecisionInstanceFilters(getSearchString());

  return Object.entries(filters).reduce<DecisionRequestFilters>(
    (accumulator, [key, value]) => {
      if (value === undefined) {
        return accumulator;
      }

      if (typeof value === 'boolean') {
        if (['evaluated', 'failed'].includes(key)) {
          return {
            ...accumulator,
            [key]: value,
          };
        }
        if (key === 'failed') {
          return {
            ...accumulator,
            [key]: value,
          };
        }
      } else {
        if (key === 'decisionInstanceIds') {
          return {
            ...accumulator,
            ids: parseIds(value),
          };
        }
        if (key === 'processInstanceId') {
          return {...accumulator, processInstanceId: value};
        }
        if (
          key === 'version' &&
          filters.name !== undefined &&
          value !== undefined
        ) {
          const decisionDefinitionIds = getDecisionIds(filters.name, value);

          if (decisionDefinitionIds.length > 0) {
            return {
              ...accumulator,
              decisionDefinitionIds,
            };
          }
        }

        if (['evaluationDateAfter', 'evaluationDateBefore'].includes(key)) {
          return {
            ...accumulator,
            [key]: value,
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

function getSortParams(search?: string): {
  sortBy: string;
  sortOrder: 'asc' | 'desc';
} | null {
  const params = new URLSearchParams(search ?? getSearchString());
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
  parseFilterTime,
  getProcessInstancesRequestFilters,
  getDecisionInstancesRequestFilters,
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
  DecisionInstanceFilterField,
};
