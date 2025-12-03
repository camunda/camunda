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
import {parseIds, parseSortParamsV2} from '../index';
import {getValidVariableValues} from '../getValidVariableValues';

/**
 * ProcessInstancesFilter represents the URL search params.
 * This is the single source of truth for what can be filtered from the UI.
 */
const ProcessInstancesFilterSchema = z
  .object({
    process: z.string().optional(),
    version: z.string().optional(),
    tenant: z.string().optional(),
    ids: z.string().optional(),
    parentProcessInstanceId: z.string().optional(),
    active: z.coerce.boolean().optional(),
    incidents: z.coerce.boolean().optional(),
    completed: z.coerce.boolean().optional(),
    canceled: z.coerce.boolean().optional(),
    variableName: z.string().optional(),
    variableValues: z.string().optional(),
    flowNodeId: z.string().optional(),
    operationId: z.string().optional(),
    errorMessage: z.string().optional(),
    retriesLeft: z.coerce.boolean().optional(),
    hasElementInstanceIncident: z.coerce.boolean().optional(),
    incidentErrorHashCode: z.coerce.number().optional(),
    startDateAfter: z.string().transform(formatToISO).optional(),
    startDateBefore: z.string().transform(formatToISO).optional(),
    endDateAfter: z.string().transform(formatToISO).optional(),
    endDateBefore: z.string().transform(formatToISO).optional(),
  })
  .catch({});

type ProcessInstancesFilter = z.infer<typeof ProcessInstancesFilterSchema>;
type ProcessInstancesSearchFilter = NonNullable<
  QueryProcessInstancesRequestBody['filter']
>;

const parseProcessInstancesFilter = (
  search: URLSearchParams,
): ProcessInstancesFilter => {
  if (typeof search === 'string') {
    search = new URLSearchParams(search);
  }
  return ProcessInstancesFilterSchema.parse(Object.fromEntries(search));
};

const parseProcessInstancesSearchFilter = (
  search: URLSearchParams,
): ProcessInstancesSearchFilter | undefined => {
  const filter = parseProcessInstancesFilter(search);

  if (
    !filter.active &&
    !filter.completed &&
    !filter.canceled &&
    !filter.incidents
  ) {
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

  if (filter.parentProcessInstanceId) {
    apiFilter.parentProcessInstanceKey = {$eq: filter.parentProcessInstanceId};
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
  }

  if (filter.variableName && filter.variableValues) {
    const parsed = (getValidVariableValues(filter.variableValues) ?? []).map(
      (v) => JSON.stringify(v),
    );
    if (parsed.length > 0) {
      apiFilter.variables = parsed.map((value) => ({
        name: filter.variableName!,
        value,
      }));
    }
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
      ...(filter.startDateAfter && {$gt: filter.startDateAfter}),
      ...(filter.startDateBefore && {$lt: filter.startDateBefore}),
    };
  }

  if (filter.endDateAfter || filter.endDateBefore) {
    apiFilter.endDate = {
      ...(filter.endDateAfter && {$gt: filter.endDateAfter}),
      ...(filter.endDateBefore && {$lt: filter.endDateBefore}),
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

export {
  parseProcessInstancesFilter,
  parseProcessInstancesSearchFilter,
  parseProcessInstancesSearchSort,
};
export type {ProcessInstancesFilter};
