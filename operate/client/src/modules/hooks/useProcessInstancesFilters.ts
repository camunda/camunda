/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {useFilters} from 'modules/hooks/useFilters';
import {
  type GetProcessDefinitionStatisticsRequestBody,
  type ProcessInstanceState,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {formatToISO} from 'modules/utils/date/formatDate';

function mapFiltersToRequest(
  filters: ProcessInstanceFilters,
): GetProcessDefinitionStatisticsRequestBody {
  const {
    flowNodeId,
    startDateAfter,
    startDateBefore,
    endDateAfter,
    endDateBefore,
    errorMessage,
    ids,
    active,
    incidentErrorHashCode,
    incidents,
    completed,
    canceled,
    operationId,
    parentInstanceId,
    retriesLeft,
    tenant,
    variableName,
    variableValues,
  } = filters;

  const request: GetProcessDefinitionStatisticsRequestBody = {};
  request.filter = {};

  if (startDateAfter || startDateBefore) {
    request.filter.startDate = {
      ...(startDateAfter && {$gt: formatToISO(startDateAfter)}),
      ...(startDateBefore && {$lt: formatToISO(startDateBefore)}),
    };
  }

  if (endDateAfter || endDateBefore) {
    request.filter.endDate = {
      ...(endDateAfter && {$gt: formatToISO(endDateAfter)}),
      ...(endDateBefore && {$lt: formatToISO(endDateBefore)}),
    };
  }

  if (ids) {
    request.filter.processInstanceKey = {
      $in: ids.split(','),
    };
  }

  if (parentInstanceId) {
    request.filter.parentProcessInstanceKey = {
      $eq: parentInstanceId,
    };
  }

  if (operationId) {
    request.filter.batchOperationId = {
      $eq: operationId,
    };
  }

  if (errorMessage) {
    request.filter.errorMessage = {
      $in: [errorMessage],
    };
  }

  if (retriesLeft) {
    request.filter.hasRetriesLeft = true;
  }

  if (flowNodeId) {
    request.filter.elementId = {
      $eq: flowNodeId,
    };
  }

  if (incidentErrorHashCode) {
    request.filter.incidentErrorHashCode = incidentErrorHashCode;
  }

  if (tenant) {
    request.filter.tenantId = {
      $eq: tenant,
    };
  }

  const state: Array<ProcessInstanceState> = [];
  if (active) {
    state.push('ACTIVE');
  }
  if (completed) {
    state.push('COMPLETED');
  }
  if (canceled) {
    state.push('TERMINATED');
  }

  if (incidents) {
    if (active || completed || canceled) {
      request.filter.$or = [{state: {$in: state}}, {hasIncident: true}];
    } else {
      request.filter.hasIncident = true;
    }
  }
  if (state.length > 0) {
    request.filter.state = {
      $in: state,
    };
  }

  if (variableName && variableValues) {
    const variableNames = variableName.split(',');
    const variableVals = variableValues.split(',');
    request.filter.variables = variableNames
      .map((name, index) => ({
        name,
        value: variableVals[index] || '',
      }))
      .filter((variable) => variable.value !== '');
  }

  return request;
}

function useProcessInstanceFilters(): GetProcessDefinitionStatisticsRequestBody {
  const {getFilters} = useFilters();
  const filters = getFilters();

  return mapFiltersToRequest(filters);
}

export {useProcessInstanceFilters};
