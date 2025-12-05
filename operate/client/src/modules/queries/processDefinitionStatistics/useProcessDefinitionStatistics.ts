/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {fetchProcessDefinitionStatistics} from 'modules/api/v2/processDefinitions/fetchProcessDefinitionStatistics';
import type {
  GetProcessDefinitionInstanceStatisticsRequestBody,
  GetProcessDefinitionInstanceStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {RequestError} from 'modules/request';

const REFETCH_INTERVAL = 5000;

const PROCESS_DEFINITION_STATISTICS_QUERY_KEY = 'processDefinitionStatistics';

type UseProcessDefinitionStatisticsOptions = {
  payload?: GetProcessDefinitionInstanceStatisticsRequestBody;
  enabled?: boolean;
};

const useProcessDefinitionStatistics = ({
  payload,
  enabled = true,
}: UseProcessDefinitionStatisticsOptions = {}) => {
  return useQuery<
    GetProcessDefinitionInstanceStatisticsResponseBody,
    RequestError,
    GetProcessDefinitionInstanceStatisticsResponseBody
  >({
    queryKey: [PROCESS_DEFINITION_STATISTICS_QUERY_KEY, payload],
    queryFn: async () => {
      const {response, error} = await fetchProcessDefinitionStatistics(payload);

      if (response !== null) {
        return response;
      }

      throw error;
    },
    enabled,
    refetchInterval: REFETCH_INTERVAL,
  });
};

export {useProcessDefinitionStatistics};
