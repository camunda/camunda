/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {queryKeys} from '../queryKeys';
import {searchIncidentsByElementInstance} from 'modules/api/v2/incidents/searchIncidentsByElementInstance';

type CountQueryOptions = {
  enabled?: boolean;
};

function useElementInstanceIncidentsCount(
  elementInstanceKey: string,
  options?: CountQueryOptions,
) {
  return useQuery({
    queryKey:
      queryKeys.incidents.elementInstanceIncidentsCount(elementInstanceKey),
    enabled: options?.enabled ?? !!elementInstanceKey,
    refetchInterval: 5000,
    staleTime: 5000,
    queryFn: async () => {
      const {response, error} = await searchIncidentsByElementInstance(
        elementInstanceKey,
        {page: {limit: 0}, filter: {state: 'ACTIVE'}},
      );
      if (response !== null) {
        return response.page.totalItems;
      }

      throw error;
    },
  });
}

export {useElementInstanceIncidentsCount};
