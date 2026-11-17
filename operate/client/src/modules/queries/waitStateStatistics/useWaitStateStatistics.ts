/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {
  GetProcessInstanceWaitStateStatisticsResponseBody,
  WaitStateStatistic,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {RequestError} from 'modules/request';
import {fetchProcessInstanceWaitStateStatistics} from 'modules/api/v2/processInstances/fetchProcessInstanceWaitStateStatistics';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useIsProcessInstanceRunning} from 'modules/queries/processInstance/useIsProcessInstanceRunning';
import {queryKeys} from '../queryKeys';

type UseWaitStateStatisticsParams = {
  enabled?: boolean;
};

const useWaitStateStatistics = (params: UseWaitStateStatisticsParams = {}) => {
  const {enabled = true} = params;
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const {data: isProcessInstanceRunning} = useIsProcessInstanceRunning();

  const isEnabled =
    enabled && !!processInstanceId && !!isProcessInstanceRunning;

  return useQuery<
    GetProcessInstanceWaitStateStatisticsResponseBody,
    RequestError,
    WaitStateStatistic[]
  >({
    queryKey: queryKeys.waitStateStatistics.get(processInstanceId),
    queryFn: async ({signal}) => {
      const {response, error} = await fetchProcessInstanceWaitStateStatistics(
        processInstanceId,
        signal,
      );
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: isEnabled,
    refetchInterval: () => (isProcessInstanceRunning ? 5000 : undefined),
    select: (data) => (isEnabled ? data.items : []),
  });
};

export {useWaitStateStatistics};
