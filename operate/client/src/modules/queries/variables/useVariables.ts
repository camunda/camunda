/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type QueryVariablesRequestBody,
  type QueryVariablesResponseBody,
} from '@vzeta/camunda-api-zod-schemas';
import {useInfiniteQuery} from '@tanstack/react-query';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';
import {MAX_VARIABLES_PER_REQUEST} from 'modules/constants/variables';

const VARIABLES_SEARCH_QUERY_KEY = 'variablesSearch';

function getQueryKey(payload: QueryVariablesRequestBody) {
  const {filter = {}, page = {}, sort = []} = payload;

  return [
    VARIABLES_SEARCH_QUERY_KEY,
    ...Object.entries(filter).map(
      ([key, value]) => `${key}:${Object.entries(value)}`,
    ),
    ...Object.entries(page).map(([key, value]) => `${key}:${value}`),
    ...sort.flatMap((item) =>
      Object.entries(item).map(([key, value]) => `${key}:${value}`),
    ),
  ];
}

function useVariables(
  payload: QueryVariablesRequestBody,
  options?: {
    refetchInterval?: number | false;
  },
) {
  const {refetchInterval = false} = options ?? {};
  return useInfiniteQuery({
    queryKey: getQueryKey(payload),
    queryFn: async ({pageParam = 0}) => {
      const {response, error} = await searchVariables({
        ...payload,
        page: {
          ...payload.page,
          from: pageParam,
          limit: payload.page?.limit ?? MAX_VARIABLES_PER_REQUEST,
        },
      });

      if (response !== null) {
        return response as QueryVariablesResponseBody;
      }

      throw error;
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage, __pages_, lastPageParam) => {
      const {page} = lastPage;
      if (!page) return null;
      const nextPage =
        lastPageParam + (page.totalItems ?? MAX_VARIABLES_PER_REQUEST);
      if (nextPage >= page.totalItems) {
        return null;
      }
      return nextPage;
    },
    getPreviousPageParam: (_firstPage, _pages, firstPageParam) => {
      const previousPage = firstPageParam - MAX_VARIABLES_PER_REQUEST;
      if (previousPage < 0) {
        return null;
      }
      return previousPage;
    },
    refetchInterval,
  });
}

export {VARIABLES_SEARCH_QUERY_KEY, useVariables};
