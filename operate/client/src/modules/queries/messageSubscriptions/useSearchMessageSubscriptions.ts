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
  type QueryUserTasksRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {searchMessageSubscriptions} from 'modules/api/v2/messageSubscriptions/searchMessageSubscriptions';

const MESSAGE_SUBSCRIPTION_SEARCH_QUERY_KEY = 'incidentsSearch';

const useSearchMessageSubscriptions = (
  payload: QueryMessageSubscriptionsRequestBody,
  options?: {enabled?: boolean},
) => {
  const {enabled = true} = options ?? {};

  return useQuery({
    queryKey: [MESSAGE_SUBSCRIPTION_SEARCH_QUERY_KEY, payload],
    queryFn: async () => {
      const {response, error} = await searchMessageSubscriptions(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled,
  });
};

export {useSearchMessageSubscriptions};
