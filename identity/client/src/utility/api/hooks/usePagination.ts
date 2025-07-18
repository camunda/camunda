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

const useSorting = (defaultConfig?: SortConfig[]) => {
  const [sort, setSort] = useState(defaultConfig);

  return [sort, setSort];
};

const usePagination = (config: Page = DEFAULT_PAGINATION_CONFIG) => {
  const [sortParams, setSort] = useSorting();

  const [pageState, setPageState] = useState<Page>(() => config);

  const setPage = useCallback((newPage: number) => {
    setPageState((prevState) => ({
      ...prevState,
      page: newPage,
    }));
  }, []);

  const setPageSize = (newPageSize: number) => {
    setPageState((prevState) => ({
      ...prevState,
      pageSize: newPageSize,
    }));
  };

  const pageParams = useMemo(() => {
    const result = {
      page: {
        from: (pageState.page - 1) * pageState.pageSize,
        limit: pageState.pageSize,
      },
    };

    if (sortParams) result.sort = sortParams;

    return result;
  }, [pageState, sortParams]);

  return { pageParams, page: pageState, setPage, setPageSize, setSort };
};

export default usePagination;
