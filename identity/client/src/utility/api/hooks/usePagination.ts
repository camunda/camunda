/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useState } from "react";
import useApi from "./useApi";

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

type UsePaginationConfig = {
  page?: number;
  pageSize?: number;
};

type Page = {
  page: number;
  pageSize: number;
};

const usePagination = (
  config: UsePaginationConfig = { page: 1, pageSize: 15 },
) => {
  const { page = 1, pageSize = 15 } = config;

  const [pageState, setPageState] = useState<Page>({
    page: page,
    pageSize: pageSize,
  });

  const setPage = (newPage: number) => {
    setPageState((prevState) => ({
      ...prevState,
      page: newPage,
    }));
  };

  const setPageSize = (newPageSize: number) => {
    setPageState((prevState) => ({
      ...prevState,
      pageSize: newPageSize,
    }));
  };

  const pageParams = {
    page: {
      from: (pageState.page - 1) * pageState.pageSize,
      limit: pageState.pageSize,
    },
  };

  return { pageParams, page: pageState, setPage, setPageSize };
};

export default usePagination;
