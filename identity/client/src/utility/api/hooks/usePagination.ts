/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useMemo, useState } from "react";
import type { QueryPage } from "@camunda/camunda-api-zod-schemas/8.10";

export type PageSearchParams = {
  page: QueryPage;
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

/** A search term, either matched exactly or, wrapped as `{ $like }`, as a substring. */
export type SearchFilterValue = string | { $like: string };

export type PaginationRequestParams = PageSearchParams & {
  sort?: SortConfig[];
  filter?: Record<string, SearchFilterValue>;
};

export type UsePaginationResult = {
  pageParams: PaginationRequestParams;
  page: Page;
  setPageNumber: (newPage: number) => void;
  setPageSize: (newPageSize: number) => void;
  setSort: (sort: SortConfig[] | undefined) => void;
  setSearch: (search: Record<string, SearchFilterValue> | undefined) => void;
  search?: Record<string, SearchFilterValue>;
  resetPagination: () => void;
};

const useSearch = (
  reset = () => {},
): [
  Record<string, SearchFilterValue> | undefined,
  (newSearch: Record<string, SearchFilterValue> | undefined) => void,
] => {
  const [search, setSearch] = useState<
    Record<string, SearchFilterValue> | undefined
  >(undefined);

  const handleSearchChange = useCallback(
    (newSearch: Record<string, SearchFilterValue> | undefined) => {
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
