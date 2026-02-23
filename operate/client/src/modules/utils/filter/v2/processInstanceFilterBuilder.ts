/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {formatToISO} from 'modules/utils/date/formatDate';
import type {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import type {RequestFilters} from 'modules/utils/filter';
import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';
import {buildProcessInstanceKeyCriterion} from 'modules/mutations/processes/buildProcessInstanceKeyCriterion';
import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {getElementInstanceStateFilter} from './getElementInstanceStateFilter';

type ProcessInstanceFilterQuery = NonNullable<
  QueryProcessInstancesRequestBody['filter']
>;

type NormalizedFilters = {
  processInstanceKey?: string[];
  elementId?: string;
  batchOperationId?: string;
  tenantId?: string;
  processDefinitionKey?: string[];
  variable?: {name: string; values: string[]};
  parentInstanceId?: string;
  errorMessage?: string;
  hasElementInstanceIncident?: boolean;
  incidentErrorHashCode?: number;
  retriesLeft?: boolean;
  startDateAfter?: string;
  startDateBefore?: string;
  endDateAfter?: string;
  endDateBefore?: string;
  active?: boolean;
  completed?: boolean;
  canceled?: boolean;
  incidents?: boolean;
};

type BuildProcessInstanceFilterOptions = {
  includeIds?: string[];
  excludeIds?: string[];
  processDefinitionKeys?: string[];
  businessObjects?: BusinessObjects;
};

// Unified input type that accepts either URL format or Request format
type UnifiedProcessInstanceFilters = ProcessInstanceFilters | RequestFilters;

const inputFilterSchema = z
  .object({
    ids: z.union([z.string(), z.array(z.string())]).optional(),
    flowNodeId: z.string().optional(),
    operationId: z.string().optional(),
    tenant: z.string().optional(),
    version: z.string().optional(),
    variableName: z.string().optional(),
    variableValues: z.string().optional(),
    activityId: z.string().optional(),
    batchOperationId: z.string().optional(),
    tenantId: z.string().optional(),
    processIds: z.array(z.string()).optional(),
    variable: z
      .object({
        name: z.string(),
        values: z.array(z.string()),
      })
      .optional(),
    parentInstanceId: z.string().optional(),
    errorMessage: z.string().optional(),
    hasElementInstanceIncident: z.boolean().optional(),
    incidentErrorHashCode: z.number().optional(),
    retriesLeft: z.boolean().optional(),
    startDateAfter: z.string().optional(),
    startDateBefore: z.string().optional(),
    endDateAfter: z.string().optional(),
    endDateBefore: z.string().optional(),
    active: z.boolean().optional(),
    completed: z.boolean().optional(),
    canceled: z.boolean().optional(),
    incidents: z.boolean().optional(),
  })
  .transform((data) => {
    const normalized: NormalizedFilters = {};

    if (data.ids) {
      normalized.processInstanceKey =
        typeof data.ids === 'string' ? data.ids.split(',') : data.ids;
    }

    if (data.flowNodeId) {
      normalized.elementId = data.flowNodeId;
    } else if (data.activityId) {
      normalized.elementId = data.activityId;
    }

    if (data.operationId) {
      normalized.batchOperationId = data.operationId;
    } else if (data.batchOperationId) {
      normalized.batchOperationId = data.batchOperationId;
    }

    if (data.tenant) {
      normalized.tenantId = data.tenant;
    } else if (data.tenantId) {
      normalized.tenantId = data.tenantId;
    }

    if (data.processIds && data.processIds.length > 0) {
      normalized.processDefinitionKey = data.processIds;
    }

    if (data.variableName && data.variableValues) {
      const parsed = (getValidVariableValues(data.variableValues) ?? []).map(
        (v) => JSON.stringify(v),
      );
      normalized.variable = {
        name: data.variableName,
        values: parsed,
      };
    } else if (data.variable) {
      normalized.variable = data.variable;
    }

    if (data.parentInstanceId) {
      normalized.parentInstanceId = data.parentInstanceId;
    }
    if (data.errorMessage) {
      normalized.errorMessage = data.errorMessage;
    }
    if (data.hasElementInstanceIncident) {
      normalized.hasElementInstanceIncident = data.hasElementInstanceIncident;
    }
    if (typeof data.incidentErrorHashCode === 'number') {
      normalized.incidentErrorHashCode = data.incidentErrorHashCode;
    }
    if (data.retriesLeft) {
      normalized.retriesLeft = data.retriesLeft;
    }

    if (data.startDateAfter) {
      normalized.startDateAfter = data.startDateAfter;
    }
    if (data.startDateBefore) {
      normalized.startDateBefore = data.startDateBefore;
    }
    if (data.endDateAfter) {
      normalized.endDateAfter = data.endDateAfter;
    }
    if (data.endDateBefore) {
      normalized.endDateBefore = data.endDateBefore;
    }

    if (data.active !== undefined) {
      normalized.active = data.active;
    }
    if (data.completed !== undefined) {
      normalized.completed = data.completed;
    }
    if (data.canceled !== undefined) {
      normalized.canceled = data.canceled;
    }
    if (data.incidents !== undefined) {
      normalized.incidents = data.incidents;
    }

    return normalized;
  })
  .catch({});

const applyDateRanges = (
  query: ProcessInstanceFilterQuery,
  dates: {
    startDateAfter?: string;
    startDateBefore?: string;
    endDateAfter?: string;
    endDateBefore?: string;
  },
) => {
  const {startDateAfter, startDateBefore, endDateAfter, endDateBefore} = dates;

  if (startDateAfter || startDateBefore) {
    query.startDate = {
      ...(startDateAfter && {$gt: formatToISO(startDateAfter)}),
      ...(startDateBefore && {$lt: formatToISO(startDateBefore)}),
    };
  }
  if (endDateAfter || endDateBefore) {
    query.endDate = {
      ...(endDateAfter && {$gt: formatToISO(endDateAfter)}),
      ...(endDateBefore && {$lt: formatToISO(endDateBefore)}),
    };
  }
};

const mapStatesAndIncidents = (
  query: ProcessInstanceFilterQuery,
  params: {
    active: boolean;
    completed: boolean;
    canceled: boolean;
    incidents: boolean;
  },
) => {
  const states: Array<'ACTIVE' | 'COMPLETED' | 'TERMINATED'> = [];
  if (params.active) {
    states.push('ACTIVE');
  }
  if (params.completed) {
    states.push('COMPLETED');
  }
  if (params.canceled) {
    states.push('TERMINATED');
  }

  if (params.incidents) {
    if (states.length > 0) {
      query.$or = [{state: {$in: states}}, {hasIncident: true}];
    } else {
      query.hasIncident = true;
    }
  } else if (states.length > 0) {
    query.state = states.length === 1 ? {$eq: states[0]} : {$in: states};
  }
};

const mapVariables = (
  query: ProcessInstanceFilterQuery,
  variable: {name: string; values: string[]},
) => {
  const filteredValues = variable.values.filter((v) => v !== '');
  if (filteredValues.length > 0) {
    query.variables = filteredValues.map((value) => ({
      name: variable.name,
      value,
    }));
  }
};

const mapProcessKeys = (
  query: ProcessInstanceFilterQuery,
  options: BuildProcessInstanceFilterOptions,
) => {
  if (
    Array.isArray(options.processDefinitionKeys) &&
    options.processDefinitionKeys.length > 0
  ) {
    query.processDefinitionKey = {$in: options.processDefinitionKeys};
  }
};

const buildProcessInstanceFilter = (
  filters: UnifiedProcessInstanceFilters,
  options: BuildProcessInstanceFilterOptions = {},
): ProcessInstanceFilterQuery => {
  const normalizedFilters = inputFilterSchema.parse(filters);

  const query: ProcessInstanceFilterQuery = {};

  if (normalizedFilters.processInstanceKey) {
    query.processInstanceKey = {$in: normalizedFilters.processInstanceKey};
  }

  const processInstanceKeyCriterion = buildProcessInstanceKeyCriterion(
    options.includeIds,
    options.excludeIds,
  );
  if (processInstanceKeyCriterion) {
    query.processInstanceKey = processInstanceKeyCriterion;
  }

  if (normalizedFilters.parentInstanceId) {
    query.parentProcessInstanceKey = {$eq: normalizedFilters.parentInstanceId};
  }

  if (normalizedFilters.batchOperationId) {
    query.batchOperationId = {$eq: normalizedFilters.batchOperationId};
  }

  if (normalizedFilters.errorMessage) {
    query.errorMessage = {$in: [normalizedFilters.errorMessage]};
  }

  if (normalizedFilters.hasElementInstanceIncident) {
    query.hasElementInstanceIncident =
      normalizedFilters.hasElementInstanceIncident;
  }

  if (typeof normalizedFilters.incidentErrorHashCode === 'number') {
    query.incidentErrorHashCode = normalizedFilters.incidentErrorHashCode;
  }

  if (normalizedFilters.elementId) {
    query.elementId = {$eq: normalizedFilters.elementId};
  }

  if (normalizedFilters.tenantId && normalizedFilters.tenantId !== 'all') {
    query.tenantId = {$eq: normalizedFilters.tenantId};
  }

  if (normalizedFilters.retriesLeft) {
    query.hasRetriesLeft = true;
  }

  if (normalizedFilters.processDefinitionKey) {
    query.processDefinitionKey = {$in: normalizedFilters.processDefinitionKey};
  } else {
    mapProcessKeys(query, options);
  }

  applyDateRanges(query, {
    startDateAfter: normalizedFilters.startDateAfter,
    startDateBefore: normalizedFilters.startDateBefore,
    endDateAfter: normalizedFilters.endDateAfter,
    endDateBefore: normalizedFilters.endDateBefore,
  });

  mapStatesAndIncidents(query, {
    active: normalizedFilters.active ?? false,
    completed: normalizedFilters.completed ?? false,
    canceled: normalizedFilters.canceled ?? false,
    incidents: normalizedFilters.incidents ?? false,
  });

  if (normalizedFilters.variable) {
    mapVariables(query, normalizedFilters.variable);
  }

  const elementInstanceStateFilter = getElementInstanceStateFilter(
    normalizedFilters.elementId,
    options.businessObjects,
  );
  if (elementInstanceStateFilter) {
    query.elementInstanceState = elementInstanceStateFilter;
  }

  return query;
};

export {buildProcessInstanceFilter};
export type {BuildProcessInstanceFilterOptions};
