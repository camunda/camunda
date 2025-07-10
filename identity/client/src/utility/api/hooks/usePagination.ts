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
};

type Page = {
  pageNumber: number;
  pageSize: number;
};

export const DEFAULT_PAGINATION_CONFIG = {
  pageNumber: 1,
  pageSize: 10,
};

export type SortConfig = {
  field: string;
  order: "ASC" | "DESC";
};

export type PaginationRequestParams = PageSearchParams & {
  sort?: SortConfig[];
  filter?: Record<string, string>;
};

export type UsePaginationResult = {
  pageParams: PaginationRequestParams;
  page: Page;
  setPageNumber: (newPage: number) => void;
  setPageSize: (newPageSize: number) => void;
  setSort: (sort: SortConfig[] | undefined) => void;
  setSearch: (search: Record<string, string> | undefined) => void;
  search?: Record<string, string>;
  resetPagination: () => void;
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
  const [page, setPage] = useState<Page>(() => config);

  const resetPageState = useCallback(() => {
    setPage(config);
  }, [config]);

  const [sortParams, setSort] = useSorting();

  const [searchParams, setSearch] = useSearch(resetPageState);

  const setPageNumber = useCallback((newPage: number) => {
    setPage((prevState) => ({
      ...prevState,
      pageNumber: newPage,
    }));
  }, []);

  const setPageSize = useCallback((newPageSize: number) => {
    setPage((prevState) => ({
      ...prevState,
      pageSize: newPageSize,
    }));
  }, []);

  const pageParams = useMemo(() => {
    const result: PaginationRequestParams = {
      page: {
        from: (page.pageNumber - 1) * page.pageSize,
        limit: page.pageSize,
      },
    };

    if (sortParams) result.sort = sortParams;

    if (searchParams) result.filter = searchParams;

    return result;
  }, [page, sortParams, searchParams]);

  const reset = useCallback(() => {
    resetPageState();
    setSort(undefined);
    setSearch(undefined);
  }, [resetPageState, setSort, setSearch]);

  return {
    pageParams,
    page,
    search: searchParams,
    setPageNumber,
    setPageSize,
    setSort,
    setSearch,
    resetPagination: reset,
  };
};

export default usePagination;
