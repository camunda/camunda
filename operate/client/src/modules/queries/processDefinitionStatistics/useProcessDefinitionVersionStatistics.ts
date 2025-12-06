/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {fetchProcessDefinitionVersionStatistics} from 'modules/api/v2/processDefinitions/fetchProcessDefinitionVersionStatistics';
import type {
  GetProcessDefinitionInstanceVersionStatisticsRequestBody,
  GetProcessDefinitionInstanceVersionStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {RequestError} from 'modules/request';

const PROCESS_DEFINITION_VERSION_STATISTICS_QUERY_KEY =
  'processDefinitionVersionStatistics';

type UseProcessDefinitionVersionStatisticsOptions = {
  payload?: GetProcessDefinitionInstanceVersionStatisticsRequestBody;
  enabled?: boolean;
};

const useProcessDefinitionVersionStatistics = (
  processDefinitionId: string,
  {payload, enabled = true}: UseProcessDefinitionVersionStatisticsOptions,
) => {
  return useQuery<
    GetProcessDefinitionInstanceVersionStatisticsResponseBody,
    RequestError,
    GetProcessDefinitionInstanceVersionStatisticsResponseBody
  >({
    queryKey: [
      PROCESS_DEFINITION_VERSION_STATISTICS_QUERY_KEY,
      processDefinitionId,
      payload,
    ],
    queryFn: async () => {
      const {response, error} = await fetchProcessDefinitionVersionStatistics(
        processDefinitionId,
        payload,
      );

      if (response !== null) {
        return response;
      }

      throw error;
    },
    enabled: enabled && !!processDefinitionId,
  });
};

export {useProcessDefinitionVersionStatistics};
