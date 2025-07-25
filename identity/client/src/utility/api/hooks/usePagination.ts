/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useMemo, useState } from "react";

export type PageSearchParams = {
  page: {
    from?: number;
    limit?: number;
    after?: string;
    before?: string;
  };
};

export type PageResult = {
  totalItems: number;
  startCursor: string;
  endCursor: string;
  hasMoreTotalItems: boolean;
};

type Page = {
  page: number;
  pageSize: number;
};

export const DEFAULT_PAGINATION_CONFIG = {
  page: 1,
  pageSize: 15,
};

export type SortConfig = {
  field: string;
  order: "ASC" | "DESC";
};

export type PaginationRequestParams = PageSearchParams & {
  sort?: SortConfig[];
  filter?: Record<string, string>;
};

export type SearchConfig = {
  filter?: Record<string, string>;
};

export type UsePaginationResult = {
  pageParams: PaginationRequestParams;
  page: Page;
  setPage: (newPage: number) => void;
  setPageSize: (newPageSize: number) => void;
  setSort: (sort: SortConfig[] | undefined) => void;
  setSearch: (search: Record<string, string> | undefined) => void;
  search?: Record<string, string>;
  resetPagination: () => void; // Needed to influence empty state
};

const useSearch = (
  reset = () => {},
): [
  Record<string, string> | undefined,
  (newSearch: Record<string, string> | undefined) => void,
] => {
  const [search, setSearch] = useState<Record<string, string> | undefined>(
    undefined,
  );

  const handleSearchChange = useCallback(
    (newSearch: Record<string, string> | undefined) => {
      setSearch(newSearch);
      reset();
    },
    [reset],
  );

  return [search, handleSearchChange];
};

const useSorting = (
  defaultConfig?: SortConfig[],
): [SortConfig[] | undefined, (sort: SortConfig[] | undefined) => void] => {
  const [sort, setSort] = useState<SortConfig[] | undefined>(defaultConfig);

  return [sort, setSort];
};

const usePagination = (
  config: Page = DEFAULT_PAGINATION_CONFIG,
): UsePaginationResult => {
  const [pageState, setPageState] = useState<Page>(() => config);

  const resetPageState = useCallback(() => {
    setPageState(config);
  }, [config]);

  const [sortParams, setSort] = useSorting();

  const [searchParams, setSearch] = useSearch(resetPageState);

  const setPage = useCallback((newPage: number) => {
    setPageState((prevState) => ({
      ...prevState,
      page: newPage,
    }));
  }, []);

  const setPageSize = useCallback((newPageSize: number) => {
    setPageState((prevState) => ({
      ...prevState,
      pageSize: newPageSize,
    }));
  }, []);

  const pageParams = useMemo(() => {
    const result: PaginationRequestParams = {
      page: {
        from: (pageState.page - 1) * pageState.pageSize,
        limit: pageState.pageSize,
      },
    };

    if (sortParams) result.sort = sortParams;

    if (searchParams) result.filter = searchParams;

    return result;
  }, [pageState, sortParams, searchParams]);

  const reset = useCallback(() => {
    resetPageState();
    setSort(undefined);
    setSearch(undefined);
  }, [resetPageState, setSort, setSearch]);

  return {
    pageParams,
    page: pageState,
    setPage,
    setPageSize,
    setSort,
    setSearch,
    search: searchParams,
    resetPagination: reset, // Needed to influence empty state
  };
};

export default usePagination;
