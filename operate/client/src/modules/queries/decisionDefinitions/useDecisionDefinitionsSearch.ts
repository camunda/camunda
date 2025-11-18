/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {queryKeys} from '../queryKeys';
import {searchDecisionDefinitions} from 'modules/api/v2/decisionDefinitions/searchDecisionDefinitions';
import type {
  QueryDecisionDefinitionsRequestBody,
  QueryDecisionDefinitionsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';

type QueryOptions<T> = {
  payload?: QueryDecisionDefinitionsRequestBody;
  enabled?: boolean;
  select?: (result: QueryDecisionDefinitionsResponseBody['items']) => T;
};

const useDecisionDefinitionsSearch = <
  T = QueryDecisionDefinitionsResponseBody['items'],
>(
  options?: QueryOptions<T>,
) => {
  return useQuery({
    queryKey: queryKeys.decisionDefinitions.search(options?.payload),
    enabled: options?.enabled,
    select: options?.select,
    queryFn: async () => {
      const {error, response} = await searchDecisionDefinitions(
        options?.payload,
      );
      if (error) {
        throw error;
      }

      if (response.page.totalItems <= response.items.length) {
        return response.items;
      }

      const {response: remaining, error: remainingError} =
        await searchDecisionDefinitions({
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
};

export {useDecisionDefinitionsSearch};
