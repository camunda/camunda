/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, UseQueryOptions} from '@tanstack/react-query';
import {
  fetchProcessInstancesStatistics,
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';

function getProcessInstanceStatisticsOptions<T>(
  payload: ProcessInstancesStatisticsRequest,
  parser?: (data: ProcessInstancesStatisticsDto) => T,
): UseQueryOptions<T> {
  return {
    queryKey: getQueryKey(Object.values(payload)),
    queryFn: async () => {
      const response = await fetchProcessInstancesStatistics(payload);
      const data = await response.json();
      return parser?.(data) ?? data;
    },
  };
}

function useProcessInstancesStatistics(
  payload: ProcessInstancesStatisticsRequest,
) {
  return useQuery(
    getProcessInstanceStatisticsOptions<ProcessInstancesStatisticsDto>(payload),
  );
}

function getQueryKey(keys: any[]) {
  return ['processInstances', ...keys];
}

export {useProcessInstancesStatistics};
