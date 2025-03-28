/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fetchProcessInstancesStatistics} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {useProcessInstanceFilters} from 'modules/hooks/useProcessInstancesFilters';
import {skipToken, UseQueryOptions} from '@tanstack/react-query';
import {RequestError} from 'modules/request';

function getQueryKey(payload: GetProcessDefinitionStatisticsRequestBody) {
  return ['processInstancesStatistics', ...Object.values(payload)];
}

function useProcessInstancesStatisticsOptions<
  T = GetProcessDefinitionStatisticsResponseBody,
>(
  payload: GetProcessDefinitionStatisticsRequestBody,
  select?: (data: GetProcessDefinitionStatisticsResponseBody) => T,
  processDefinitionKey?: string,
  enabled: boolean = true,
): UseQueryOptions<
  GetProcessDefinitionStatisticsResponseBody,
  RequestError,
  T
> {
  const filters = useProcessInstanceFilters();

  const combinedFilters = {
    ...payload,
    ...filters,
    filter: {
      ...payload.filter,
      ...filters.filter,
    },
  };

  return {
    queryKey: getQueryKey(combinedFilters),
    queryFn: !!processDefinitionKey
      ? async () => {
          const {response, error} = await fetchProcessInstancesStatistics(
            combinedFilters,
            processDefinitionKey,
          );

          if (response !== null) {
            return response;
          }

          throw error;
        }
      : skipToken,
    select,
    enabled: enabled && !!processDefinitionKey,
  };
}

export {useProcessInstancesStatisticsOptions};
