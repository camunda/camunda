/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryElementInstanceIncidentsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {useQuery} from '@tanstack/react-query';
import {searchIncidentsByElementInstance} from 'modules/api/v2/incidents/searchIncidentsByElementInstance';
import {queryKeys} from '../queryKeys';

type QueryOptions<T> = {
  enabled?: boolean;
  enablePeriodicRefetch?: boolean;
  select?: (result: QueryElementInstanceIncidentsResponseBody) => T;
};

const useGetIncidentsByElementInstance = <
  T = QueryElementInstanceIncidentsResponseBody,
>(
  elementInstanceKey: string,
  options?: QueryOptions<T>,
) => {
  return useQuery({
    queryKey:
      queryKeys.incidents.searchByElementInstanceKey(elementInstanceKey),
    refetchInterval: () => (options?.enablePeriodicRefetch ? 5000 : false),
    enabled: options?.enabled ?? !!elementInstanceKey,
    select: options?.select,
    queryFn: async () => {
      const {response, error} =
        await searchIncidentsByElementInstance(elementInstanceKey);
      if (response !== null) {
        return response;
      }

      throw error;
    },
  });
};

export {useGetIncidentsByElementInstance};
