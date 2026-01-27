/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {
  type QueryMessageSubscriptionsRequestBody,
  type QueryMessageSubscriptionsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {searchMessageSubscriptions} from 'modules/api/v2/messageSubscriptions/searchMessageSubscriptions';

type QueryOptions<T> = {
  select?: (result: QueryMessageSubscriptionsResponseBody) => T;
  enabled?: boolean;
};

const useSearchMessageSubscriptions = <
  T = QueryMessageSubscriptionsResponseBody,
>(
  payload: QueryMessageSubscriptionsRequestBody,
  options?: QueryOptions<T>,
) => {
  const {enabled = true, select} = options ?? {};

  return useQuery({
    queryKey: ['incidentsSearch', payload],
    queryFn: async () => {
      const {response, error} = await searchMessageSubscriptions(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled,
    select,
  });
};

export {useSearchMessageSubscriptions};
