/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {fetchProcessDefinitionStatistics} from 'modules/api/v2/processDefinitions/fetchProcessDefinitionStatistics';
import {queryKeys} from '../queryKeys.ts';
import type {ProcessDefinitionInstanceStatistics} from '@camunda/camunda-api-zod-schemas/8.8';

interface RunningProcessInstancesCount {
  total: number;
  withoutIncidents: number;
  withIncidents: number;
}

function aggregateRunningInstancesCount(
  definitionStatistics: ProcessDefinitionInstanceStatistics[],
): RunningProcessInstancesCount {
  let count: RunningProcessInstancesCount = {
    total: 0,
    withoutIncidents: 0,
    withIncidents: 0,
  };
  for (const statistic of definitionStatistics) {
    count.withoutIncidents += statistic.activeInstancesWithoutIncidentCount;
    count.withIncidents += statistic.activeInstancesWithIncidentCount;
  }
  count.total = count.withoutIncidents + count.withIncidents;
  return count;
}

function useRunningInstancesCountStatistics() {
  return useQuery({
    queryKey: queryKeys.processDefinitionStatistics.runningInstancesCount(),
    queryFn: async () => {
      const {response, error} = await fetchProcessDefinitionStatistics();
      if (error) {
        throw error;
      }

      if (response.page.totalItems <= response.items.length) {
        return aggregateRunningInstancesCount(response.items);
      }

      const {response: remaining, error: remainingError} =
        await fetchProcessDefinitionStatistics({
          page: {from: response.items.length, limit: response.page.totalItems},
        });
      if (remainingError) {
        throw remainingError;
      }

      const allItems = response.items.concat(remaining.items);
      return aggregateRunningInstancesCount(allItems);
    },
    refetchInterval: 5000,
  });
}

export {useRunningInstancesCountStatistics};
