/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import {queryBatchOperationItems} from 'modules/api/v2/batchOperations/queryBatchOperationItems';
import type {QueryBatchOperationItemsRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys';

const MAX_OPERATIONS_PER_REQUEST = 50;

const useBatchOperationItems = (
  payload: QueryBatchOperationItemsRequestBody,
) => {
  return useInfiniteQuery({
    queryKey: queryKeys.batchOperationItems.query(payload),
    queryFn: async () => {
      const requestPayload = {
        ...payload,
        page: {
          ...payload.page,
          limit: payload.page?.limit ?? MAX_OPERATIONS_PER_REQUEST,
          from: payload.page?.from ?? 0,
        },
      };

      const {response, error} = await queryBatchOperationItems(requestPayload);

      if (response !== null) {
        return response;
      }

      throw error;
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const {page} = lastPage;
      const nextPage = lastPageParam + MAX_OPERATIONS_PER_REQUEST;

      if (nextPage > page.totalItems) {
        return null;
      }

      return nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - MAX_OPERATIONS_PER_REQUEST;

      if (previousPage < 0) {
        return null;
      }

      return previousPage;
    },
    maxPages: 2,
  });
};

export {useBatchOperationItems};
