/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
  queryProcessInstancesRequestBodySchema,
  type ProcessInstanceState,
  type QueryProcessInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {formatToISO} from 'modules/utils/date/formatDate';
import {parseIds, parseSortParamsV2, updateFiltersSearchString} from '../index';
import type {VariableCondition} from 'modules/stores/variableFilter';
import {getValidVariableValues} from '../getValidVariableValues';

/**
 * ProcessInstancesFilter represents the URL search params.
 * This is the single source of truth for what can be filtered from the UI.
 * Note: variableName/variableValues are included in the schema for form state management
 * but are stripped when updating the URL (variable filtering is stored in variableFilterStore).
 */
const ProcessInstancesFilterSchema = z
  .object({
    process: z.string().optional(),
    version: z.string().optional(),
    tenant: z.string().optional(),
    ids: z.string().optional(),
    parentInstanceId: z.string().optional(),
    active: z.coerce.boolean().optional(),
    incidents: z.coerce.boolean().optional(),
    completed: z.coerce.boolean().optional(),
    canceled: z.coerce.boolean().optional(),
    flowNodeId: z.string().optional(),
    operationId: z.string().optional(),
    errorMessage: z.string().optional(),
    retriesLeft: z.coerce.boolean().optional(),
    hasElementInstanceIncident: z.coerce.boolean().optional(),
    incidentErrorHashCode: z.coerce.number().optional(),
    startDateAfter: z.string().optional(),
    startDateBefore: z.string().optional(),
    endDateAfter: z.string().optional(),
    endDateBefore: z.string().optional(),
    variableName: z.string().optional(),
    variableValues: z.string().optional(),
  })
  .catch({});

type ProcessInstancesFilter = z.infer<typeof ProcessInstancesFilterSchema>;
type ProcessInstancesSearchFilter = NonNullable<
  QueryProcessInstancesRequestBody['filter']
>;

const parseProcessInstancesFilter = (
  search: URLSearchParams,
): ProcessInstancesFilter => {
  return ProcessInstancesFilterSchema.parse(Object.fromEntries(search));
};

const parseProcessInstancesSearchFilter = (
  search: URLSearchParams,
): ProcessInstancesSearchFilter | undefined => {
  const filter = parseProcessInstancesFilter(search);

  const hasStateFilters =
    filter.active || filter.completed || filter.canceled || filter.incidents;

  if (!hasStateFilters && !filter.operationId) {
    return undefined;
  }

  const apiFilter: ProcessInstancesSearchFilter = {};

  if (filter.process) {
    apiFilter.processDefinitionId = {$eq: filter.process};
  }

  if (filter.version && filter.version !== 'all') {
    const versionNumber = parseInt(filter.version, 10);
    if (!isNaN(versionNumber)) {
      apiFilter.processDefinitionVersion = versionNumber;
    }
  }

  if (filter.tenant && filter.tenant !== 'all') {
    apiFilter.tenantId = {$eq: filter.tenant};
  }

  if (filter.ids) {
    const keys = parseIds(filter.ids);
    if (keys.length > 0) {
      apiFilter.processInstanceKey = {$in: keys};
    }
  }

  if (filter.parentInstanceId) {
    apiFilter.parentProcessInstanceKey = {$eq: filter.parentInstanceId};
  }

  const states: ProcessInstanceState[] = [];
  if (filter.active) {
    states.push('ACTIVE');
  }
  if (filter.completed) {
    states.push('COMPLETED');
  }
  if (filter.canceled) {
    states.push('TERMINATED');
  }

  if (filter.incidents) {
    if (states.length > 0) {
      apiFilter.$or = [{state: {$in: states}}, {hasIncident: true}];
    } else {
      apiFilter.hasIncident = true;
    }
  } else if (states.length > 0) {
    apiFilter.state = states.length === 1 ? {$eq: states[0]} : {$in: states};
    apiFilter.hasIncident = false;
  }

  if (filter.flowNodeId) {
    apiFilter.elementId = {$eq: filter.flowNodeId};
  }

  if (filter.operationId) {
    apiFilter.batchOperationId = {$eq: filter.operationId};
  }

  if (filter.errorMessage) {
    apiFilter.errorMessage = {$in: [filter.errorMessage]};
  }

  if (filter.retriesLeft) {
    apiFilter.hasRetriesLeft = true;
  }

  if (filter.hasElementInstanceIncident) {
    apiFilter.hasElementInstanceIncident = true;
  }

  if (typeof filter.incidentErrorHashCode === 'number') {
    apiFilter.incidentErrorHashCode = filter.incidentErrorHashCode;
  }

  if (filter.startDateAfter || filter.startDateBefore) {
    apiFilter.startDate = {
      ...(filter.startDateAfter && {$gt: formatToISO(filter.startDateAfter)}),
      ...(filter.startDateBefore && {$lt: formatToISO(filter.startDateBefore)}),
    };
  }

  if (filter.endDateAfter || filter.endDateBefore) {
    apiFilter.endDate = {
      ...(filter.endDateAfter && {$gt: formatToISO(filter.endDateAfter)}),
      ...(filter.endDateBefore && {$lt: formatToISO(filter.endDateBefore)}),
    };
  }

  return apiFilter;
};

type ProcessInstancesSearchSort = NonNullable<
  QueryProcessInstancesRequestBody['sort']
>;

const ProcessInstancesSearchSortFieldSchema =
  queryProcessInstancesRequestBodySchema.shape.sort.unwrap().unwrap()
    .shape.field;

const parseProcessInstancesSearchSort = (
  search: URLSearchParams,
): ProcessInstancesSearchSort => {
  return parseSortParamsV2(search, ProcessInstancesSearchSortFieldSchema, {
    field: 'startDate',
    order: 'desc',
  });
};

const PROCESS_INSTANCE_FILTER_FIELDS = Object.values(
  ProcessInstancesFilterSchema.unwrap().keyof().enum,
);

const BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS = Object.values(
  ProcessInstancesFilterSchema.unwrap()
    .pick({
      active: true,
      incidents: true,
      completed: true,
      canceled: true,
      retriesLeft: true,
      hasElementInstanceIncident: true,
    })
    .keyof().enum,
);

function updateProcessInstancesFilterSearchString(
  currentSearch: URLSearchParams,
  newFilters: ProcessInstancesFilter,
) {
  // Strip variable filter fields - they're stored in MobX, not URL
  const {variableName, variableValues, ...filtersWithoutVariable} = newFilters;

  return updateFiltersSearchString<ProcessInstancesFilter>(
    currentSearch,
    filtersWithoutVariable,
    PROCESS_INSTANCE_FILTER_FIELDS,
    BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS,
  );
}

/**
 * Internal type for variable filter value in V2 API format.
 */
type VariableFilterValue =
  | string
  | {
      $eq?: string;
      $neq?: string;
      $exists?: boolean;
      $in?: string[];
      $like?: string;
    };

/**
 * Internal type representing a single variable filter condition in V2 API format.
 * Used by convertVariableConditionsToApiFormat.
 */
type VariableFilter = {
  name: string;
  value: VariableFilterValue;
};

/**
 * Converts UI variable filter conditions to the V2 API format.
 *
 * Operator mappings:
 * - equals → {$eq: value}
 * - notEqual → {$neq: value}
 * - contains → {$like: "*value*"} (auto-wrap wildcards)
 * - oneOf → {$in: [values]} (always array)
 * - exists → {$exists: true}
 * - doesNotExist → {$exists: false}
 */
function convertVariableConditionsToApiFormat(
  conditions: VariableCondition[],
): VariableFilter[] {
  return conditions
    .filter((c) => {
      // Filter out invalid conditions
      if (!c.name.trim()) {
        return false;
      }
      if (c.operator === 'exists' || c.operator === 'doesNotExist') {
        return true;
      }
      return c.value.trim() !== '';
    })
    .map((condition) => {
      const {name, operator, value} = condition;

      switch (operator) {
        case 'exists':
          return {name, value: {$exists: true}};
        case 'doesNotExist':
          return {name, value: {$exists: false}};
        case 'equals': {
          const parsed = getValidVariableValues(value);
          const jsonValue =
            parsed && parsed.length > 0 ? JSON.stringify(parsed[0]) : value;
          return {name, value: {$eq: jsonValue}};
        }
        case 'notEqual': {
          const parsed = getValidVariableValues(value);
          const jsonValue =
            parsed && parsed.length > 0 ? JSON.stringify(parsed[0]) : value;
          return {name, value: {$neq: jsonValue}};
        }
        case 'contains': {
          const parsed = getValidVariableValues(value);
          const jsonValue =
            parsed && parsed.length > 0 ? JSON.stringify(parsed[0]) : value;
          return {name, value: {$like: `*${jsonValue}*`}};
        }
        case 'oneOf': {
          const parsed = getValidVariableValues(value) ?? [];
          const jsonValues = parsed.map((v) => JSON.stringify(v));
          return {
            name,
            value: {$in: jsonValues.length > 0 ? jsonValues : [value]},
          };
        }
        default:
          return {name, value};
      }
    });
}

export {
  parseProcessInstancesFilter,
  parseProcessInstancesSearchFilter,
  parseProcessInstancesSearchSort,
  updateProcessInstancesFilterSearchString,
  convertVariableConditionsToApiFormat,
};
export type {ProcessInstancesFilter};
