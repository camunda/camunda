/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition } from "../request";
import useApi, { UseApiOptions } from "./useApi";
import usePagination, { PageResult } from "./usePagination";

const DEFAULT_PAGINATION_CONFIG = {
  page: 1,
  pageSize: 1, // TODO(@Marstamm): Make it 15
};

const usePaginatedApi = <R extends { page?: PageResult }, P>(
  apiDefinition: ApiDefinition<R, P>,
  params: P = {} as P,
  Options?: UseApiOptions,
) => {
  const { pageParams, page, setPage, setPageSize } = usePagination(
    DEFAULT_PAGINATION_CONFIG,
  );

  const result = useApi(
    apiDefinition,
    {
      ...pageParams,
      ...params,
    },
    Options,
  );

  return {
    ...result,
    page: { ...page, ...result?.data?.page },
    setPage,
    setPageSize,
  };
};

export default usePaginatedApi;
