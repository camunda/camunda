/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {queryBatchOperationItems} from 'modules/api/v2/batchOperations/queryBatchOperationItems';
import type {
  BatchOperationItem,
  QueryBatchOperationItemsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys';

const useActiveOperationItemsForInstances = (processInstanceKeys: string[]) => {
  return useQuery({
    queryKey: [
      ...queryKeys.batchOperationItems.query({
        filter: {
          processInstanceKey:
            processInstanceKeys.length > 0
              ? {$in: processInstanceKeys}
              : undefined,
          state: {$eq: 'ACTIVE'},
        },
      }),
      {processInstanceKeys},
    ],
    queryFn: async (): Promise<QueryBatchOperationItemsResponseBody> => {
      if (processInstanceKeys.length === 0) {
        return {items: [] as BatchOperationItem[], page: {totalItems: 0}};
      }

      const {response, error} = await queryBatchOperationItems({
        filter: {
          processInstanceKey: {$in: processInstanceKeys},
          state: {$eq: 'ACTIVE'},
        },
        page: {
          // Limit based on assumption that instances typically have ~3 active operations
          // Provides reasonable buffer while preventing over-fetching
          limit: processInstanceKeys.length * 3,
        },
      });

      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: processInstanceKeys.length > 0,
    refetchInterval: (query) => {
      const hasActiveOperations = (query.state.data?.items.length ?? 0) > 0;
      return hasActiveOperations ? 5000 : false;
    },
  });
};

export {useActiveOperationItemsForInstances};
