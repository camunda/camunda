/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback } from "react";
import useApiCall from "./useApiCall";
import usePagination, { PageResult } from "./usePagination";
import { mergeParams } from "./utils";

const usePaginatedApiCall = <T extends { page?: PageResult }, P>(
  ...props: Parameters<typeof useApiCall<T, P>>
) => {
  const { pageParams, page, setPageNumber, setPageSize, ...rest } =
    usePagination();

  const [_call, result, reset] = useApiCall<T, P>(...props);

  const call = useCallback(
    (params: P) => {
      return _call(
        mergeParams(params as Record<string, unknown>, pageParams) as P,
      );
    },
    [_call, pageParams],
  );

  return [
    call,
    {
      page: { ...page, ...result.data?.page },
      setPageNumber,
      setPageSize,
      ...rest,
    },
    result,
    reset,
  ] as const;
};

export default usePaginatedApiCall;
