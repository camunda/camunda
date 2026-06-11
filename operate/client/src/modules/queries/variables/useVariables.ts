/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type QueryVariablesResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {keepPreviousData, useInfiniteQuery} from '@tanstack/react-query';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useDisplayStatus, useVariableScopeKey} from 'modules/hooks/variables';
import {queryKeys} from '../queryKeys';

const MAX_VARIABLES_PER_REQUEST = 50;

const DOCUMENT_VALUE_FILTER = '*camunda.document.type*';

function useVariables(options?: {
  refetchInterval?: number | false;
  documentsOnly?: boolean;
  keepPreviousResults?: boolean;
}) {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const scopeKey = useVariableScopeKey();
  const {
    refetchInterval = false,
    documentsOnly = false,
    keepPreviousResults = false,
  } = options ?? {};
  const valueFilter = documentsOnly ? DOCUMENT_VALUE_FILTER : undefined;
  const result = useInfiniteQuery({
    queryKey: queryKeys.variables.searchWithFilter({
      processInstanceKey: processInstanceId,
      scopeKey,
      ...(valueFilter !== undefined ? {value: valueFilter} : {}),
    }),
    queryFn: async ({pageParam = 0}) => {
      const {response, error} = await searchVariables({
        filter: {
          processInstanceKey: {$eq: processInstanceId},
          scopeKey: {$eq: scopeKey ?? undefined},
          ...(valueFilter !== undefined ? {value: {$like: valueFilter}} : {}),
        },
        page: {
          from: pageParam,
          limit: MAX_VARIABLES_PER_REQUEST,
        },
        sort: [{field: 'name', order: 'asc'}],
      });

      if (response !== null) {
        return response as QueryVariablesResponseBody;
      }

      throw error;
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const {page} = lastPage;
      const nextPage = lastPageParam + MAX_VARIABLES_PER_REQUEST;

      if (nextPage > page.totalItems) {
        return null;
      }

      return nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - MAX_VARIABLES_PER_REQUEST;

      if (previousPage < 0) {
        return null;
      }

      return previousPage;
    },
    placeholderData: keepPreviousResults ? keepPreviousData : undefined,
    refetchInterval,
  });
  const displayStatus = useDisplayStatus({
    scopeKey,
    isLoading: result.isLoading,
    isFetchingNextPage: result.isFetchingNextPage,
    isFetchingPreviousPage: result.isFetchingPreviousPage,
    isFetched: result.isFetched,
    isError: result.isError,
    hasItems: (result.data?.pages?.[0]?.items?.length ?? 0) > 0,
  });
  return Object.assign(result, {displayStatus});
}

export {useVariables};
