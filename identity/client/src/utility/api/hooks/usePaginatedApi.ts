/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition } from "../request";
import useApi, { UseApiOptions } from "./useApi";
import usePagination, {
  PageResult,
  PaginationRequestParams,
} from "./usePagination";

const usePaginatedApi = <R extends { page?: PageResult }, P>(
  apiDefinition: ApiDefinition<R, P>,
  params: P = {} as P,
  Options?: UseApiOptions,
) => {
  const { pageParams, page, setPage, setPageSize, ...more } = usePagination();

  const result = useApi(
    apiDefinition,
    mergeParams(params as Record<string, unknown>, pageParams) as P,
    Options,
  );

  return {
    ...result,
    page: { ...page, ...result?.data?.page },
    setPage,
    setPageSize,
    ...more,
  };
};

export default usePaginatedApi;

const mergeParams = (
  custom = {} as Record<string, unknown>,
  page = {} as PaginationRequestParams,
): Record<string, unknown> => {
  const result = { ...custom } as Record<string, unknown>;

  for (const key of Object.keys(page)) {
    const typedKey = key as keyof PaginationRequestParams;

    if (result[typedKey] !== undefined) {
      result[typedKey] = {
        ...result[typedKey],
        ...page[typedKey],
      };
    } else {
      result[typedKey] = page[typedKey];
    }
  }

  return result;
};
