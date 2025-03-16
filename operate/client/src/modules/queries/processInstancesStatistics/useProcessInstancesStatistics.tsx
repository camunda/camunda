/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useGenericQuery} from 'modules/queries/useGenericQuery';
import {
  fetchProcessInstancesStatistics,
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {useProcessInstanceFilters} from 'modules/hooks/useProcessInstancesFilters';
import {UseQueryResult} from '@tanstack/react-query';
import {RequestError} from 'modules/request';

function getQueryKey(payload: ProcessInstancesStatisticsRequest) {
  return ['processInstancesStatistics', ...Object.values(payload)];
}

function useProcessInstancesStatistics<T = ProcessInstancesStatisticsDto[]>(
  payload: ProcessInstancesStatisticsRequest,
  select?: (data: ProcessInstancesStatisticsDto[]) => T,
  enabled?: boolean,
): UseQueryResult<T, RequestError> {
  const filters = useProcessInstanceFilters();

  return useGenericQuery<ProcessInstancesStatisticsDto[], T>(
    getQueryKey(payload),
    () =>
      fetchProcessInstancesStatistics({
        ...payload,
        ...filters,
      }),
    {
      queryKey: getQueryKey({
        ...payload,
        ...filters,
      }),
      select,
      enabled,
    },
  );
}

export {useProcessInstancesStatistics};
