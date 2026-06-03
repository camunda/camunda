/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import type {QueryAgentInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {searchAgentInstances} from 'modules/api/v2/agentInstances/searchAgentInstances';
import {queryKeys} from '../queryKeys';

const useAgentInstancesSearch = (
  payload: QueryAgentInstancesRequestBody,
  options?: {
    enabled?: boolean;
    refetchInterval?: number | false;
    loadAllItems?: boolean;
  },
) => {
  return useQuery({
    queryKey: queryKeys.agentInstances.search(payload, {
      loadAllItems: options?.loadAllItems,
    }),
    queryFn: async ({signal}) => {
      const {response, error} = await searchAgentInstances(payload, signal);
      if (response === null) {
        throw error;
      }

      if (
        !options?.loadAllItems ||
        response.page.totalItems <= response.items.length
      ) {
        return response;
      }

      const {response: remaining, error: remainingError} =
        await searchAgentInstances(
          {
            ...payload,
            page: {
              from: response.items.length,
              limit: response.page.totalItems,
            },
          },
          signal,
        );
      if (remainingError) {
        throw remainingError;
      }

      return {...response, items: response.items.concat(remaining.items)};
    },
    enabled: options?.enabled,
    refetchInterval: options?.refetchInterval,
  });
};

export {useAgentInstancesSearch};
