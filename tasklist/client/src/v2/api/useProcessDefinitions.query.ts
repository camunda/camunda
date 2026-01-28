/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import {request} from 'common/api/request';
import {api} from './index';
import type {
  QueryProcessDefinitionsRequestBody,
  QueryProcessDefinitionsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';

const PAGE_SIZE = 12;
const DEFAULT_PAGE_PARAM = -1 as number | string;

function useProcessDefinitions(
  params: QueryProcessDefinitionsRequestBody,
  options?: {
    refetchInterval?: number | false;
  },
) {
  return useInfiniteQuery({
    queryKey: ['process-definitions', params],
    queryFn: async ({pageParam}) => {
      const {response, error} = await request(
        api.queryProcesses({
          ...params,
          page:
            typeof pageParam === 'number'
              ? {
                  limit: PAGE_SIZE,
                }
              : {
                  limit: PAGE_SIZE,
                  after: pageParam,
                },
        }),
      );

      if (response !== null) {
        return response.json() as Promise<QueryProcessDefinitionsResponseBody>;
      }

      throw error;
    },
    initialPageParam: DEFAULT_PAGE_PARAM,
    getNextPageParam: (lastPage) => {
      const {page} = lastPage;

      return page.endCursor;
    },
    ...options,
  });
}

export {useProcessDefinitions};
