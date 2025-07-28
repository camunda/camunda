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
import { mergeParams } from "./utils";

const usePaginatedApi = <R extends { page?: PageResult }, P>(
  apiDefinition: ApiDefinition<R, P>,
  params: P = {} as P,
  Options?: UseApiOptions,
) => {
  const { pageParams, page, ...paginationCallbacks } = usePagination();

  const result = useApi(
    apiDefinition,
    mergeParams(params as Record<string, unknown>, pageParams) as P,
    Options,
  );

  return {
    ...result,
    page: { ...page, ...result?.data?.page },
    ...paginationCallbacks,
  };
};

export default usePaginatedApi;
