/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, UseQueryOptions} from '@tanstack/react-query';
import {RequestError} from 'modules/request';
import {GetProcessInstanceStatisticsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {fetchFlownodeInstancesStatistics} from 'modules/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';

function getQueryKey(processInstanceKey?: string) {
  return ['flownodeInstancesStatistics', processInstanceKey];
}

function useFlownodeInstancesStatisticsOptions<
  T = GetProcessInstanceStatisticsResponseBody,
>(
  select?: (data: GetProcessInstanceStatisticsResponseBody) => T,
  enabled?: boolean,
): UseQueryOptions<GetProcessInstanceStatisticsResponseBody, RequestError, T> {
  const {processInstanceId} = useProcessInstancePageParams();

  return {
    queryKey: getQueryKey(processInstanceId),
    queryFn: !!processInstanceId
      ? async () => {
          const {response, error} =
            await fetchFlownodeInstancesStatistics(processInstanceId);

          if (response !== null) {
            return response;
          }

          throw error;
        }
      : skipToken,
    select,
    enabled: enabled && !!processInstanceId,
  };
}

export {useFlownodeInstancesStatisticsOptions};
