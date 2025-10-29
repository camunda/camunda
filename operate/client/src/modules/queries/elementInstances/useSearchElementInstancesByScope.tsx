/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  useInfiniteQuery,
  type InfiniteData,
  type QueryKey,
} from '@tanstack/react-query';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import {queryKeys} from '../queryKeys';
import type {QueryElementInstancesResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import type {RequestError} from 'modules/request';

const MAX_ELEMENT_INSTANCES_PER_REQUEST = 50;

type PageParam = {
  page: number;
  pageElementInstanceScopeKey?: string;
};

const useSearchElementInstancesByScope = (
  payload: Parameters<typeof queryKeys.elementInstances.searcyByScope>[0],
) => {
  const {elementInstanceScopeKey} = payload;
  return useInfiniteQuery<
    QueryElementInstancesResponseBody,
    RequestError,
    InfiniteData<QueryElementInstancesResponseBody>,
    QueryKey,
    PageParam
  >({
    queryKey: queryKeys.elementInstances.searcyByScope({
      elementInstanceScopeKey,
    }),
    queryFn: async ({pageParam}) => {
      const {page, pageElementInstanceScopeKey} = pageParam;
      const {response, error} = await searchElementInstances({
        filter: {
          elementInstanceScopeKey:
            pageElementInstanceScopeKey ?? elementInstanceScopeKey,
        },
        page: {
          limit: MAX_ELEMENT_INSTANCES_PER_REQUEST,
          from: page,
        },
      });
      if (response !== null) {
        return response;
      }
      throw error;
    },
    initialPageParam: {
      page: 0,
    },
    getNextPageParam: (
      lastPage,
      _,
      {page: lastPageParam, pageElementInstanceScopeKey},
    ) => {
      const {page} = lastPage;
      const nextPage = lastPageParam + MAX_ELEMENT_INSTANCES_PER_REQUEST;

      if (nextPage > page.totalItems) {
        return null;
      }

      return {page: nextPage, pageElementInstanceScopeKey};
    },
    getPreviousPageParam: (
      _,
      __,
      {page: firstPageParam, pageElementInstanceScopeKey},
    ) => {
      const previousPage = firstPageParam - MAX_ELEMENT_INSTANCES_PER_REQUEST;

      if (previousPage < 0) {
        return null;
      }

      return {page: previousPage, pageElementInstanceScopeKey};
    },
    refetchInterval: 5000,
    maxPages: 2,
  });
};

export {useSearchElementInstancesByScope};
