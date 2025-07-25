/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useInfiniteQuery} from '@tanstack/react-query';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import type {QueryElementInstancesRequestBody} from '@vzeta/camunda-api-zod-schemas/8.8';

const MAX_ELEMENT_INSTANCES_PER_REQUEST = 50;

const useSearchElementInstancesByScope = (
  scopeKey: string,
  options: {enabled: boolean} = {enabled: true},
) => {
  return useInfiniteQuery({
    queryKey: ['elementInstancesSearchByScope', scopeKey],
    queryFn: async ({pageParam}) => {
      const payload: QueryElementInstancesRequestBody = {
        filter: {
          scopeKey,
        },
        page: {
          limit: MAX_ELEMENT_INSTANCES_PER_REQUEST,
          from: pageParam,
        },
      };
      const {response, error} = await searchElementInstances(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    select(data) {
      return {pages: data.pages.map((page) => page.items)};
    },
    refetchInterval: 5000,
    initialPageParam: 0,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const {page} = lastPage;
      const nextPage = lastPageParam + MAX_ELEMENT_INSTANCES_PER_REQUEST;

      if (nextPage > page.totalItems) {
        return null;
      }

      return nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - MAX_ELEMENT_INSTANCES_PER_REQUEST;

      if (previousPage < 0) {
        return null;
      }

      return previousPage;
    },
    ...options,
  });
};

export {useSearchElementInstancesByScope};
