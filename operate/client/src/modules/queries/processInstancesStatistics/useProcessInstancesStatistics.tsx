/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fetchProcessInstancesStatistics} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import type {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {skipToken, type UseQueryOptions} from '@tanstack/react-query';
import type {RequestError} from 'modules/request';

const PROCESS_INSTANCES_STATISTICS_QUERY_KEY = 'processInstancesStatistics';

function getQueryKey(
  payload: GetProcessDefinitionStatisticsRequestBody,
  processDefinitionKey?: string,
) {
  return [
    PROCESS_INSTANCES_STATISTICS_QUERY_KEY,
    processDefinitionKey,
    ...Object.values(payload),
  ];
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
  return {
    queryKey: getQueryKey(payload, processDefinitionKey),
    queryFn: processDefinitionKey
      ? async () => {
          const {response, error} = await fetchProcessInstancesStatistics(
            payload,
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
