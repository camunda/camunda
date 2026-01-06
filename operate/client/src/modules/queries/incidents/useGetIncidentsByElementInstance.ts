/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryElementInstanceIncidentsRequestBody,
  QueryElementInstanceIncidentsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useQuery} from '@tanstack/react-query';
import {searchIncidentsByElementInstance} from 'modules/api/v2/incidents/searchIncidentsByElementInstance';
import {queryKeys} from '../queryKeys';

type QueryOptions<T> = {
  payload?: QueryElementInstanceIncidentsRequestBody;
  select?: (result: QueryElementInstanceIncidentsResponseBody) => T;
  enabled?: boolean;
};

const useGetIncidentsByElementInstance = <
  T = QueryElementInstanceIncidentsResponseBody,
>(
  elementInstanceKey: string,
  options?: QueryOptions<T>,
) => {
  const payload: QueryElementInstanceIncidentsRequestBody = {
    ...options?.payload,
    filter: {state: 'ACTIVE', ...options?.payload?.filter},
  };
  return useQuery({
    queryKey: queryKeys.incidents.searchByElementInstanceKey(
      elementInstanceKey,
      payload,
    ),
    enabled: options?.enabled ?? !!elementInstanceKey,
    select: options?.select,
    queryFn: async () => {
      const {response, error} = await searchIncidentsByElementInstance(
        elementInstanceKey,
        payload,
      );
      if (response !== null) {
        return response;
      }

      throw error;
    },
  });
};

export {useGetIncidentsByElementInstance};
