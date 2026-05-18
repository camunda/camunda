/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {searchMessageSubscriptions} from 'modules/api/v2/messageSubscriptions/searchMessageSubscriptions';
import type {QueryMessageSubscriptionsRequestBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {queryKeys} from '../queryKeys';

const useMessageSubscriptions = (
  payload: QueryMessageSubscriptionsRequestBody,
  options: {enabled: boolean} = {enabled: true},
) => {
  return useQuery({
    queryKey: queryKeys.messageSubscriptions.search(payload),
    queryFn: async () => {
      const {response, error} = await searchMessageSubscriptions(payload);
      if (response !== null) {
        return response;
      }

      throw error;
    },
    ...options,
  });
};

export {useMessageSubscriptions};
