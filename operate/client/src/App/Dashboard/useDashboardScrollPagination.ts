/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback} from 'react';

const ROW_HEIGHT = 64;
const DEFAULT_PAGE_SIZE = 50;

type PaginatedResult = {
  hasPreviousPage: boolean;
  hasNextPage: boolean;
  isFetchingPreviousPage: boolean;
  isFetchingNextPage: boolean;
  fetchPreviousPage: () => Promise<unknown>;
  fetchNextPage: () => Promise<unknown>;
};

function useDashboardScrollPagination(
  queryResult: PaginatedResult,
  pageSize: number = DEFAULT_PAGE_SIZE,
) {
  const {
    hasPreviousPage,
    hasNextPage,
    isFetchingPreviousPage,
    isFetchingNextPage,
    fetchPreviousPage,
    fetchNextPage,
  } = queryResult;

  const smoothScrollStepSize = pageSize * ROW_HEIGHT;

  const handleScrollStartReach = useCallback(
    async (scrollDown: (distance: number) => void) => {
      if (hasPreviousPage && !isFetchingPreviousPage) {
        await fetchPreviousPage();
        scrollDown(smoothScrollStepSize);
      }
    },
    [
      hasPreviousPage,
      isFetchingPreviousPage,
      fetchPreviousPage,
      smoothScrollStepSize,
    ],
  );

  const handleScrollEndReach = useCallback(() => {
    if (hasNextPage && !isFetchingNextPage) {
      fetchNextPage();
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return {
    handleScrollStartReach,
    handleScrollEndReach,
    isFetchingPreviousPage,
    isFetchingNextPage,
  };
}

export {useDashboardScrollPagination};
