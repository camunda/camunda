/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {genericQueryOptions} from 'modules/queries/genericQuery';
import {
  fetchProcessInstancesStatistics,
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {useProcessInstanceFilters} from 'modules/hooks/useProcessInstancesFilters';
import {usePrefetchQuery, UseQueryOptions} from '@tanstack/react-query';
import {RequestError} from 'modules/request';

function getQueryKey(payload: ProcessInstancesStatisticsRequest) {
  return ['processInstancesStatistics', ...Object.values(payload)];
}

function useProcessInstancesStatisticsOptions<
  T = ProcessInstancesStatisticsDto[],
>(
  payload: ProcessInstancesStatisticsRequest,
  select?: (data: ProcessInstancesStatisticsDto[]) => T,
  enabled?: boolean,
): UseQueryOptions<ProcessInstancesStatisticsDto[], RequestError, T> {
  const filters = useProcessInstanceFilters();

  return genericQueryOptions<ProcessInstancesStatisticsDto[], T>(
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

function usePrefetchProcessInstancesStatistics(
  payload: ProcessInstancesStatisticsRequest,
  enabled?: boolean,
) {
  return usePrefetchQuery(
    useProcessInstancesStatisticsOptions(payload, (data) => data, enabled),
  );
}

export {
  usePrefetchProcessInstancesStatistics,
  useProcessInstancesStatisticsOptions,
};
