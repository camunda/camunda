/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {
  fetchProcessInstancesStatistics,
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {RequestError} from 'modules/request';

function useProcessInstancesStatistics(
  payload: ProcessInstancesStatisticsRequest,
) {
  return useQuery<ProcessInstancesStatisticsDto[], RequestError>({
    queryKey: getQueryKey(Object.values(payload)),
    queryFn: async () => {
      const {response, error} = await fetchProcessInstancesStatistics(payload);

      if (response !== null) {
        return response;
      }

      throw error ?? new Error('Failed to fetch process instances statistics');
    },
  });
}

function getQueryKey(keys: unknown[]) {
  return ['processInstances', ...keys];
}

export {useProcessInstancesStatistics};
