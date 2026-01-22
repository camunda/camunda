/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {queryBatchOperationItems} from 'modules/api/v2/batchOperations/queryBatchOperationItems';
import type {QueryBatchOperationItemsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys';

const useOperationItemsForInstances = (
  batchOperationKey: string | undefined,
  processInstanceKeys: string[],
) => {
  return useQuery({
    queryKey: [
      ...queryKeys.batchOperationItems.query({
        filter: {
          batchOperationKey: batchOperationKey
            ? {$eq: batchOperationKey}
            : undefined,
          processInstanceKey:
            processInstanceKeys.length > 0
              ? {$in: processInstanceKeys}
              : undefined,
        },
      }),
      {batchOperationKey, processInstanceKeys},
    ],
    queryFn: async (): Promise<QueryBatchOperationItemsResponseBody> => {
      const {response, error} = await queryBatchOperationItems({
        filter: {
          batchOperationKey: {$eq: batchOperationKey!},
          processInstanceKey: {$in: processInstanceKeys},
        },
        page: {
          limit: processInstanceKeys.length,
        },
      });

      if (response !== null) {
        return response;
      }
      throw error;
    },
    enabled: !!batchOperationKey && processInstanceKeys.length > 0,
  });
};

export {useOperationItemsForInstances};
