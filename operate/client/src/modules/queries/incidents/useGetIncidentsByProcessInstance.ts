/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryIncidentsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {useQuery} from '@tanstack/react-query';
import {searchIncidentsByProcessInstance} from 'modules/api/v2/incidents/searchIncidentsByProcessInstance';
import {queryKeys} from '../queryKeys';

type QueryOptions<T> = {
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  select?: (result: QueryIncidentsResponseBody) => T;
};

const useGetIncidentsByProcessInstance = <T = QueryIncidentsResponseBody>(
  processInstanceKey: string,
  options?: QueryOptions<T>,
) => {
  return useQuery({
    queryKey:
      queryKeys.incidents.searchByProcessInstanceKey(processInstanceKey),
    refetchInterval: () => (options?.enablePeriodicRefetch ? 5000 : false),
    enabled: options?.enabled ?? !!processInstanceKey,
    select: options?.select,
    queryFn: async () => {
      const {response, error} = await searchIncidentsByProcessInstance({
        processInstanceKey,
      });
      if (error) {
        throw error;
      }

      return response;
    },
  });
};

function useProcessInstanceIncidentsCount(processInstanceKey: string): number {
  const {data} = useGetIncidentsByProcessInstance(processInstanceKey, {
    select: (incidents) => incidents.page.totalItems,
  });

  return data ?? 0;
}

export {useGetIncidentsByProcessInstance, useProcessInstanceIncidentsCount};
