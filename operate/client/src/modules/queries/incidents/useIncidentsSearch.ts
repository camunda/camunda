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

const INCIDENTS_SEARCH_LIVE_QUERY_KEY = 'incidentsSearchLive';

type QueryOptions<T> = {
  enabled?: boolean;
  select?: (result: QueryIncidentsResponseBody) => T;
};

function useIncidentsSearch<T = QueryIncidentsResponseBody>(
  processInstanceKey: string,
  options?: QueryOptions<T>,
) {
  return useQuery({
    queryKey: [INCIDENTS_SEARCH_LIVE_QUERY_KEY, processInstanceKey],
    refetchInterval: 5000,
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
}

function useIncidentsCount(processInstanceKey: string): number {
  const {data} = useIncidentsSearch(processInstanceKey, {
    select: (incidents) => incidents.page.totalItems,
  });

  return data ?? 0;
}

export {useIncidentsSearch, useIncidentsCount};
