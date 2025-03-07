/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {UseQueryOptions} from '@tanstack/react-query';
import {
  fetchProcessInstancesStatistics,
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {RequestError} from 'modules/request';

function getProcessInstanceStatisticsOptions<T, P>(
  payload: ProcessInstancesStatisticsRequest,
  parser?: (data: ProcessInstancesStatisticsDto[], params: P) => T,
  parserParams?: P,
  enabled?: boolean,
): UseQueryOptions<T, RequestError> {
  return {
    queryKey: getQueryKey(Object.values(payload)),
    queryFn: async (): Promise<T> => {
      const {response, error} = await fetchProcessInstancesStatistics(payload);

      if (response !== null) {
        return (parser ? parser(response, parserParams!) : response) as T;
      }

      throw error ?? new Error('Failed to fetch process instances statistics');
    },
    enabled: enabled ?? true,
  };
}

function getQueryKey(keys: unknown[]) {
  return ['processInstances', ...keys];
}

export {getProcessInstanceStatisticsOptions};
