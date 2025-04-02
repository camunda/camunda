/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {ProcessInstanceState} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {useFilters} from 'modules/hooks/useFilters';
import {GetProcessDefinitionStatisticsRequestBody} from '@vzeta/camunda-api-zod-schemas/operate';

const formatToISO = (dateString: string | undefined): string | undefined => {
  if (!dateString) return undefined;
  const date = new Date(dateString);
  return date.toISOString();
};

function mapFiltersToRequest(
  filters: ProcessInstanceFilters,
): GetProcessDefinitionStatisticsRequestBody {
  const {
    startDateAfter,
    startDateBefore,
    endDateAfter,
    endDateBefore,
    ids,
    active,
    incidents,
    completed,
    canceled,
    parentInstanceId,
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

  if (incidents) {
    request.filter.hasIncident = true;
  }

  if (tenant) {
    request.filter.tenantId = {
      $eq: tenant,
    };
  }

  const state: ProcessInstanceState[] = [];
  if (active) state.push(ProcessInstanceState.ACTIVE);
  if (completed) state.push(ProcessInstanceState.COMPLETED);
  if (canceled) state.push(ProcessInstanceState.TERMINATED);

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
