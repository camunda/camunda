/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {formatToISO} from 'modules/utils/date/formatDate';
import type {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import type {RequestFilters} from 'modules/utils/filter';
import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';
import {buildProcessInstanceKeyCriterion} from 'modules/mutations/processes/buildProcessInstanceKeyCriterion';
import type {QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';

type ProcessInstanceFilterQuery = NonNullable<
  QueryProcessInstancesRequestBody['filter']
>;

type BuildProcessInstanceFilterOptions = {
  includeIds?: string[];
  excludeIds?: string[];
  processDefinitionKeys?: string[];
};

// Unified input type that accepts either URL format or Request format
type UnifiedProcessInstanceFilters = ProcessInstanceFilters | RequestFilters;

const applyDateRanges = (
  query: NonNullable<QueryProcessInstancesRequestBody['filter']>,
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
  query: NonNullable<QueryProcessInstancesRequestBody['filter']>,
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
  query: NonNullable<QueryProcessInstancesRequestBody['filter']>,
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
  query: NonNullable<QueryProcessInstancesRequestBody['filter']>,
  options: BuildProcessInstanceFilterOptions,
) => {
  if (
    options.processDefinitionKeys &&
    options.processDefinitionKeys.length > 0
  ) {
    query.processDefinitionKey = {$in: options.processDefinitionKeys};
  }
};

const buildProcessInstanceFilter = (
  filters: UnifiedProcessInstanceFilters,
  options: BuildProcessInstanceFilterOptions = {},
): ProcessInstanceFilterQuery => {
  const query: ProcessInstanceFilterQuery = {};

  if ('ids' in filters && filters.ids) {
    const ids =
      typeof filters.ids === 'string' ? filters.ids.split(',') : filters.ids;
    query.processInstanceKey = {$in: ids};
  }

  if (filters.parentInstanceId) {
    query.parentProcessInstanceKey = {$eq: filters.parentInstanceId};
  }

  const batchOperationId =
    ('operationId' in filters && filters.operationId) ||
    ('batchOperationId' in filters && filters.batchOperationId);
  if (batchOperationId) {
    query.batchOperationId = {$eq: batchOperationId};
  }

  if (filters.errorMessage) {
    query.errorMessage = {$in: [filters.errorMessage]};
  }

  if (typeof filters.incidentErrorHashCode === 'number') {
    query.incidentErrorHashCode = filters.incidentErrorHashCode;
  }

  const elementId =
    ('flowNodeId' in filters && filters.flowNodeId) ||
    ('activityId' in filters && filters.activityId);
  if (elementId) {
    query.elementId = {$eq: elementId};
  }

  const tenantId =
    ('tenant' in filters && filters.tenant) ||
    ('tenantId' in filters && filters.tenantId);
  if (tenantId && tenantId !== 'all') {
    query.tenantId = {$eq: tenantId};
  }

  if (filters.retriesLeft) {
    query.hasRetriesLeft = true;
  }

  if ('version' in filters && filters.version) {
    query.processDefinitionVersionTag = filters.version;
  }

  if (
    'processIds' in filters &&
    Array.isArray(filters.processIds) &&
    filters.processIds.length > 0
  ) {
    query.processDefinitionKey = {$in: filters.processIds};
  } else {
    mapProcessKeys(query, options);
  }

  applyDateRanges(query, {
    startDateAfter: filters.startDateAfter,
    startDateBefore: filters.startDateBefore,
    endDateAfter: filters.endDateAfter,
    endDateBefore: filters.endDateBefore,
  });

  mapStatesAndIncidents(query, {
    active: !!filters.active,
    completed: !!filters.completed,
    canceled: !!filters.canceled,
    incidents: !!filters.incidents,
  });

  if (
    'variableName' in filters &&
    filters.variableName &&
    filters.variableValues
  ) {
    const parsed = (getValidVariableValues(filters.variableValues) ?? []).map(
      (v) => JSON.stringify(v),
    );
    mapVariables(query, {name: filters.variableName, values: parsed});
  } else if (
    'variable' in filters &&
    filters.variable?.name &&
    Array.isArray(filters.variable.values)
  ) {
    mapVariables(query, {
      name: filters.variable.name,
      values: filters.variable.values,
    });
  }

  const processInstanceKeyCriterion = buildProcessInstanceKeyCriterion(
    options.includeIds,
    options.excludeIds,
  );

  if (processInstanceKeyCriterion) {
    query.processInstanceKey = processInstanceKeyCriterion;
  }

  return query;
};

export {buildProcessInstanceFilter};
export type {BuildProcessInstanceFilterOptions, ProcessInstanceFilterQuery};
