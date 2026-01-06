/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parse, isValid} from 'date-fns';
import {processesStore} from 'modules/stores/processes/processes.list';
import {getSearchString} from 'modules/utils/getSearchString';
import type {Location} from 'react-router-dom';
import {getValidVariableValues} from './getValidVariableValues';
import {variableFilterStore} from 'modules/stores/variableFilter';
import {generateProcessKey} from '../generateProcessKey';
import {getProcessInstanceFilters} from './getProcessInstanceFilters';
import {
  PROCESS_INSTANCE_FILTER_FIELDS,
  BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS,
  type ProcessInstanceFilters,
} from './shared';
import type z from 'zod';
import {
  querySortOrderSchema,
  type QuerySortOrder,
} from '@camunda/camunda-api-zod-schemas/8.8';

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
  incidentErrorHashCode?: number;
  ids?: string[];
  parentInstanceId?: string;
  startDateAfter?: string;
  startDateBefore?: string;
  variable?: {
    name: string;
    values: string[];
  };
  processIds?: string[];
  tenantId?: string;
  retriesLeft?: boolean;
};

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

function getProcessIds({
  process,
  processVersion,
  tenant,
}: {
  process: string;
  processVersion: string;
  tenant?: string;
}) {
  if (processVersion === 'all') {
    return (
      processesStore.versionsByProcessAndTenant?.[
        generateProcessKey(process, tenant)
      ]?.map(({id}) => id) ?? []
    );
  }

  return (
    processesStore.versionsByProcessAndTenant?.[
      generateProcessKey(process, tenant)
    ]
      ?.filter(({version}) => version === parseInt(processVersion))
      ?.map(({id}) => id) ?? []
  );
}

function getProcessInstancesRequestFilters(): RequestFilters {
  const {variable} = variableFilterStore.state;

  const filters = {
    ...getProcessInstanceFilters(getSearchString()),
    variableName: variable?.name,
    variableValues: variable?.values,
  };

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

        if (key === 'retriesLeft' && value === true) {
          return {...accumulator, retriesLeft: true};
        }
      } else if (typeof value === 'number') {
        if (key === 'incidentErrorHashCode') {
          return {...accumulator, incidentErrorHashCode: value};
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
          const processIds = getProcessIds({
            process: filters.process,
            processVersion: value,
            tenant: filters.tenant,
          });

          if (processIds.length > 0) {
            return {
              ...accumulator,
              processIds,
            };
          }
        }

        if (
          (key === 'variableName' || key === 'variableValues') &&
          filters.variableName !== undefined &&
          filters.variableValues !== undefined
        ) {
          const values =
            getValidVariableValues(filters.variableValues)?.map((value) =>
              JSON.stringify(value),
            ) ?? [];

          return {
            ...accumulator,
            variable: {
              name: filters.variableName,
              values,
            },
          };
        }

        if (
          [
            'startDateAfter',
            'startDateBefore',
            'endDateAfter',
            'endDateBefore',
          ].includes(key)
        ) {
          return {
            ...accumulator,
            [key]: value,
          };
        }

        if (key === 'tenant' && value !== 'all') {
          return {
            ...accumulator,
            tenantId: value,
          };
        }
      }

      return accumulator;
    },
    {},
  );
}

function updateFiltersSearchString<Filters extends object>(
  currentSearch: URLSearchParams,
  newFilters: Filters,
  possibleFilters: Array<keyof Filters>,
  possibleBooleanFilters: Array<keyof Filters>,
) {
  const oldParams = Object.fromEntries(currentSearch);
  const fieldsToDelete = possibleFilters.filter(
    (field) => newFilters[field] === undefined,
  );
  const newParams = new URLSearchParams(
    Object.entries({
      ...oldParams,
      ...newFilters,
    }).filter(([, value]) => value !== ''),
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
  newFilters: ProcessInstanceFilters,
) {
  return updateFiltersSearchString<ProcessInstanceFilters>(
    new URLSearchParams(currentSearch),
    newFilters,
    PROCESS_INSTANCE_FILTER_FIELDS,
    BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS,
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

type QuerySortItem<F extends string> = {field: F; order: QuerySortOrder};

/**
 * Parses sort options from the given {@linkcode URLSearchParams} for
 * search APIs. The given {@linkcode fieldSchema} is used to validate the sort
 * field. If no sort param is found, the given {@linkcode fallback} is returned.
 */
function parseSortParamsV2<F extends string>(
  search: URLSearchParams,
  fieldSchema: z.ZodEnum<Record<F, F>>,
  fallback: QuerySortItem<F>,
): QuerySortItem<F>[] {
  const sortParam = search.get('sort');
  if (sortParam === null || sortParam === '') {
    return [fallback];
  }
  const [unsafeField, unsafeOrder] = sortParam.split('+');
  const fieldResult = fieldSchema.safeParse(unsafeField);
  const orderResult = querySortOrderSchema.safeParse(unsafeOrder);
  if (!fieldResult.success || !orderResult.success) {
    return [fallback];
  }
  return [{field: fieldResult.data, order: orderResult.data}];
}

export {
  getProcessInstanceFilters,
  parseIds,
  parseFilterTime,
  getProcessInstancesRequestFilters,
  updateFiltersSearchString,
  updateProcessFiltersSearchString,
  deleteSearchParams,
  getSortParams,
  parseSortParamsV2,
};
export type {RequestFilters};
