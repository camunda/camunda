/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {fetchProcessDefinitionVersionStatistics} from 'modules/api/v2/processDefinitions/fetchProcessDefinitionVersionStatistics';
import type {GetProcessDefinitionInstanceVersionStatisticsRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys.ts';

type UseProcessDefinitionVersionStatisticsOptions = {
  payload?: GetProcessDefinitionInstanceVersionStatisticsRequestBody;
  enabled?: boolean;
};

const useProcessDefinitionVersionStatistics = (
  processDefinitionId: string,
  {payload, enabled = true}: UseProcessDefinitionVersionStatisticsOptions = {},
) => {
  const payloadWithDefaultSorting: GetProcessDefinitionInstanceVersionStatisticsRequestBody =
    {
      sort: [
        {field: 'activeInstancesWithIncidentCount', order: 'desc'},
        {field: 'activeInstancesWithoutIncidentCount', order: 'desc'},
      ],
      ...payload,
    };

  return useQuery({
    queryKey: queryKeys.processDefinitionStatistics.getByVersion(
      processDefinitionId,
      payloadWithDefaultSorting,
    ),
    queryFn: async () => {
      const {response, error} = await fetchProcessDefinitionVersionStatistics(
        processDefinitionId,
        payloadWithDefaultSorting,
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
