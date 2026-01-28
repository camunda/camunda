/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {fetchProcessDefinitionStatistics} from 'modules/api/v2/processDefinitions/fetchProcessDefinitionStatistics';
import type {GetProcessDefinitionInstanceStatisticsRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys.ts';

type UseProcessDefinitionStatisticsOptions = {
  payload?: GetProcessDefinitionInstanceStatisticsRequestBody;
  enabled?: boolean;
};

const useProcessDefinitionStatistics = ({
  payload,
  enabled = true,
}: UseProcessDefinitionStatisticsOptions = {}) => {
  const payloadWithDefaultSorting: GetProcessDefinitionInstanceStatisticsRequestBody =
    {
      sort: [
        {field: 'activeInstancesWithIncidentCount', order: 'desc'},
        {field: 'activeInstancesWithoutIncidentCount', order: 'desc'},
      ],
      ...payload,
    };

  return useQuery({
    queryKey: queryKeys.processDefinitionStatistics.get(
      payloadWithDefaultSorting,
    ),
    queryFn: async () => {
      const {response, error} = await fetchProcessDefinitionStatistics(
        payloadWithDefaultSorting,
      );

      if (response !== null) {
        return response;
      }

      throw error;
    },
    enabled,
    refetchInterval: 5000,
  });
};

export {useProcessDefinitionStatistics};
