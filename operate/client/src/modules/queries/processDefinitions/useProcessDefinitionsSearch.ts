/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryProcessDefinitionsRequestBody,
  QueryProcessDefinitionsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useQuery} from '@tanstack/react-query';
import {queryKeys} from '../queryKeys';
import {searchProcessDefinitions} from 'modules/api/v2/processDefinitions/searchProcessDefinitions';

type QueryOptions<T> = {
  payload?: QueryProcessDefinitionsRequestBody;
  enabled?: boolean;
  select?: (result: QueryProcessDefinitionsResponseBody['items']) => T;
};

function useProcessDefinitionsSearch<T>(options?: QueryOptions<T>) {
  return useQuery({
    queryKey: queryKeys.processDefinitions.search(options?.payload),
    enabled: options?.enabled,
    select: options?.select,
    queryFn: async () => {
      const {error, response} = await searchProcessDefinitions(
        options?.payload,
      );
      if (error) {
        throw error;
      }

      if (response.page.totalItems <= response.items.length) {
        return response.items;
      }

      const {response: remaining, error: remainingError} =
        await searchProcessDefinitions({
          ...options?.payload,
          page: {
            from: response.items.length,
            limit: response.page.totalItems,
          },
        });
      if (remainingError) {
        throw remainingError;
      }

      return response.items.concat(remaining.items);
    },
  });
}

export {useProcessDefinitionsSearch};
