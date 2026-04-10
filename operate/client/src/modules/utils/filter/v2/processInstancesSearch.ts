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
} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatToISO} from 'modules/utils/date/formatDate';
import {parseIds, parseSortParamsV2, updateFiltersSearchString} from '../index';

/**
 * ProcessInstancesFilter represents the URL search params.
 * This is the single source of truth for what can be filtered from the UI.
 */
const ProcessInstancesFilterSchema = z
  .object({
    processDefinitionId: z.string().optional(),
    processDefinitionVersion: z.string().optional(),
    tenantId: z.string().optional(),
    processInstanceKey: z.string().optional(),
    parentProcessInstanceKey: z.string().optional(),
    active: z.coerce.boolean().optional(),
    incidents: z.coerce.boolean().optional(),
    completed: z.coerce.boolean().optional(),
    canceled: z.coerce.boolean().optional(),
    elementId: z.string().optional(),
    batchOperationId: z.string().optional(),
    errorMessage: z.string().optional(),
    hasRetriesLeft: z.coerce.boolean().optional(),
    hasElementInstanceIncident: z.coerce.boolean().optional(),
    incidentErrorHashCode: z.coerce.number().optional(),
    startDateFrom: z.string().optional(),
    startDateTo: z.string().optional(),
    endDateFrom: z.string().optional(),
    endDateTo: z.string().optional(),
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

  if (!hasStateFilters && !filter.batchOperationId) {
    return undefined;
  }

  const apiFilter: ProcessInstancesSearchFilter = {};

  if (filter.processDefinitionId) {
    apiFilter.processDefinitionId = {$eq: filter.processDefinitionId};
  }

  if (
    filter.processDefinitionVersion &&
    filter.processDefinitionVersion !== 'all'
  ) {
    const versionNumber = parseInt(filter.processDefinitionVersion, 10);
    if (!isNaN(versionNumber)) {
      apiFilter.processDefinitionVersion = versionNumber;
    }
  }

  if (filter.tenantId && filter.tenantId !== 'all') {
    apiFilter.tenantId = {$eq: filter.tenantId};
  }

  if (filter.processInstanceKey) {
    const keys = parseIds(filter.processInstanceKey);
    if (keys.length > 0) {
      apiFilter.processInstanceKey = {$in: keys};
    }
  }

  if (filter.parentProcessInstanceKey) {
    apiFilter.parentProcessInstanceKey = {
      $eq: filter.parentProcessInstanceKey,
    };
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

  if (filter.elementId) {
    apiFilter.elementId = {$eq: filter.elementId};
    apiFilter.elementInstanceState = {$eq: 'ACTIVE'};
  }

  if (filter.batchOperationId) {
    apiFilter.batchOperationId = {$eq: filter.batchOperationId};
  }

  if (filter.errorMessage) {
    apiFilter.errorMessage = {$in: [filter.errorMessage]};
  }

  if (filter.hasRetriesLeft) {
    apiFilter.hasRetriesLeft = true;
  }

  if (filter.hasElementInstanceIncident) {
    apiFilter.hasElementInstanceIncident = true;
  }

  if (typeof filter.incidentErrorHashCode === 'number') {
    apiFilter.incidentErrorHashCode = filter.incidentErrorHashCode;
  }

  if (filter.startDateFrom || filter.startDateTo) {
    apiFilter.startDate = {
      ...(filter.startDateFrom && {
        $gt: formatToISO(filter.startDateFrom),
      }),
      ...(filter.startDateTo && {
        $lt: formatToISO(filter.startDateTo),
      }),
    };
  }

  if (filter.endDateFrom || filter.endDateTo) {
    apiFilter.endDate = {
      ...(filter.endDateFrom && {$gt: formatToISO(filter.endDateFrom)}),
      ...(filter.endDateTo && {$lt: formatToISO(filter.endDateTo)}),
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
      hasRetriesLeft: true,
      hasElementInstanceIncident: true,
    })
    .keyof().enum,
);

function updateProcessInstancesFilterSearchString(
  currentSearch: URLSearchParams,
  newFilters: ProcessInstancesFilter,
) {
  const {variableName, variableValues, ...filtersWithoutVariable} = newFilters;

  return updateFiltersSearchString<ProcessInstancesFilter>(
    currentSearch,
    filtersWithoutVariable,
    PROCESS_INSTANCE_FILTER_FIELDS,
    BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS,
  );
}

export {
  parseProcessInstancesFilter,
  parseProcessInstancesSearchFilter,
  parseProcessInstancesSearchSort,
  updateProcessInstancesFilterSearchString,
};
export type {ProcessInstancesFilter};
