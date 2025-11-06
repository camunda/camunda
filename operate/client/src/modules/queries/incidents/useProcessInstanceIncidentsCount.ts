/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {queryKeys} from '../queryKeys';
import {searchIncidentsByProcessInstance} from 'modules/api/v2/incidents/searchIncidentsByProcessInstance';

type CountQueryOptions = {
  enabled?: boolean;
};

function useProcessInstanceIncidentsCount(
  processInstanceKey: string,
  options?: CountQueryOptions,
): number {
  const {data} = useQuery({
    queryKey:
      queryKeys.incidents.processInstanceIncidentsCount(processInstanceKey),
    enabled: options?.enabled ?? !!processInstanceKey,
    refetchInterval: 5000,
    staleTime: 5000,
    queryFn: async () => {
      const {response, error} = await searchIncidentsByProcessInstance(
        processInstanceKey,
        {page: {limit: 0}, filter: {state: 'ACTIVE'}},
      );
      if (response !== null) {
        return response.page.totalItems;
      }

      throw error;
    },
  });

  return data ?? 0;
}

export {useProcessInstanceIncidentsCount};
