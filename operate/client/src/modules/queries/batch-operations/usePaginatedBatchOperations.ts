/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, type UseQueryResult} from '@tanstack/react-query';
import type {RequestError} from 'modules/request';
import {queryBatchOperations} from 'modules/api/v2/batchOperations/queryBatchOperations';
import type {
  QueryBatchOperationsRequestBody,
  QueryBatchOperationsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys';

type PaginationArgs = {page: number; pageSize: number};

const usePaginatedBatchOperations = (
  payload: QueryBatchOperationsRequestBody,
  pagination: PaginationArgs,
): UseQueryResult<QueryBatchOperationsResponseBody, RequestError> => {
  const limit = pagination.pageSize;
  const from = (pagination.page - 1) * pagination.pageSize;

  return useQuery({
    queryKey: [queryKeys.batchOperations.query(payload), {limit, from}],
    queryFn: async () => {
      const requestPayload = {
        ...payload,
        page: {
          ...payload.page,
          limit,
          from,
        },
      };

      const {response, error} = await queryBatchOperations(requestPayload);

      if (response !== null) {
        return response;
      }
      throw error;
    },
    placeholderData: (prev) => prev,
  });
};

export {usePaginatedBatchOperations};
