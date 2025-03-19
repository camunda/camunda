/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {
  ProcessInstancesStatisticsRequest,
  ProcessInstanceState,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {useFilters} from 'modules/hooks/useFilters';
import {getProcessIds} from 'modules/utils/filter';

function mapFiltersToRequest(
  filters: ProcessInstanceFilters,
): ProcessInstancesStatisticsRequest {
  const {
    startDateAfter,
    startDateBefore,
    endDateAfter,
    endDateBefore,
    process,
    version,
    ids,
    active,
    incidents,
    completed,
    canceled,
    parentInstanceId,
    tenant,
    retriesLeft,
    operationId,
    ...rest
  } = filters;

  const request: ProcessInstancesStatisticsRequest = {
    ...rest,
  };

  if (startDateAfter || startDateBefore) {
    request.startDate = {
      ...(startDateAfter && {$gt: startDateAfter}),
      ...(startDateBefore && {$lt: startDateBefore}),
    };
  }

  if (endDateAfter || endDateBefore) {
    request.endDate = {
      ...(endDateAfter && {$gt: endDateAfter}),
      ...(endDateBefore && {$lt: endDateBefore}),
    };
  }

  if (process && version) {
    const processIds = getProcessIds({
      process,
      processVersion: version,
      tenant,
    });

    if (processIds.length > 0) {
      request.processDefinitionKey = {
        $in: processIds,
      };
    }
  }

  if (ids) {
    request.processInstanceKey = {
      $in: ids.split(','),
    };
  }

  if (parentInstanceId) {
    request.parentProcessInstanceKey = parentInstanceId;
  }

  if (tenant) {
    request.tenantId = tenant;
  }

  if (retriesLeft !== undefined) {
    request.hasRetriesLeft = retriesLeft;
  }

  if (operationId) {
    request.batchOperationKey = operationId;
  }

  const state: ProcessInstanceState[] = [];
  if (active) state.push(ProcessInstanceState.RUNNING);
  if (incidents) state.push(ProcessInstanceState.INCIDENT);
  if (completed) state.push(ProcessInstanceState.COMPLETED);
  if (canceled) state.push(ProcessInstanceState.CANCELED);

  if (state.length > 0) {
    request.state = {
      $in: state,
    };
  }

  return request;
}

function useProcessInstanceFilters(): ProcessInstancesStatisticsRequest {
  const {getFilters} = useFilters();
  const filters = getFilters();

  return mapFiltersToRequest(filters);
}

export {useProcessInstanceFilters};
