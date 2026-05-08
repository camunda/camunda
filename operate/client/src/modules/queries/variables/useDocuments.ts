/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type SearchDocumentReferencesResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {useInfiniteQuery} from '@tanstack/react-query';
import {searchDocumentReferences} from 'modules/api/v2/documentReferences/searchDocumentReferences';
import {useProcessInstancePageParams} from 'App/ProcessInstance/useProcessInstancePageParams';
import {useVariableScopeKey} from 'modules/hooks/variables';

const MAX_DOCUMENTS_PER_REQUEST = 50;

type SortConfig = {
  sortBy: string;
  sortOrder: 'asc' | 'desc';
};

function useDocuments(sortConfig?: SortConfig) {
  const {processInstanceId = ''} = useProcessInstancePageParams();
  const scopeKey = useVariableScopeKey();

  const result = useInfiniteQuery({
    queryKey: ['documents', processInstanceId, scopeKey, sortConfig?.sortBy, sortConfig?.sortOrder],
    queryFn: async ({pageParam = 0}) => {
      const {response, error} = await searchDocumentReferences({
        filter: {
          processInstanceKey: {$eq: processInstanceId},
          ...(scopeKey !== null ? {scopeKey: {$eq: scopeKey}} : {}),
        },
        page: {
          from: pageParam,
          limit: MAX_DOCUMENTS_PER_REQUEST,
        },
        ...(sortConfig !== undefined
          ? {sort: [{field: sortConfig.sortBy, order: sortConfig.sortOrder}]}
          : {}),
      });

      if (response !== null) {
        return response as SearchDocumentReferencesResponseBody;
      }

      throw error;
    },
    initialPageParam: 0,
    getNextPageParam: (lastPage, _, lastPageParam) => {
      const nextPage = lastPageParam + MAX_DOCUMENTS_PER_REQUEST;
      if (nextPage >= lastPage.page.totalItems) return null;
      return nextPage;
    },
    getPreviousPageParam: (_, __, firstPageParam) => {
      const previousPage = firstPageParam - MAX_DOCUMENTS_PER_REQUEST;
      if (previousPage < 0) return null;
      return previousPage;
    },
  });

  const documents = (result.data?.pages ?? []).flatMap((page) => page.items);

  return Object.assign(result, {documents});
}

export {useDocuments};
