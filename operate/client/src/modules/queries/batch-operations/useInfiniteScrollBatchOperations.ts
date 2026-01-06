/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import {queryBatchOperations} from 'modules/api/v2/batchOperations/queryBatchOperations';
import type {QueryBatchOperationsRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys';

const MAX_OPERATIONS_PER_REQUEST = 50;

const useInfiniteScrollBatchOperations = (
  payload: QueryBatchOperationsRequestBody,
) => {
  return useInfiniteQuery({
    queryKey: queryKeys.batchOperations.query(payload),
    queryFn: async ({pageParam}) => {
      const requestPayload = {
        ...payload,
        page: {
          ...payload.page,
          limit: MAX_OPERATIONS_PER_REQUEST,
          from: pageParam,
        },
      };

      const {response, error} = await queryBatchOperations(requestPayload);

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
    refetchInterval: 5000,
    maxPages: 2,
  });
};

export {useInfiniteScrollBatchOperations};
