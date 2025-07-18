/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useEffect, useState } from "react";
import useApiCall from "./useApiCall";
import usePagination, { PageResult } from "./usePagination";

const usePaginatedApiCall = (...props) => {
  const { pageParams, page, setPage, setPageSize, ...rest } = usePagination();

  const [pageResult, setPageResult] = useState(() => ({}));

  const [_call, result, reset] = useApiCall(...props);

  const call = useCallback(
    (params) => {
      console.log("pageParams", pageParams);

      return _call({
        ...pageParams,
        ...params,
      });
    },
    [_call, pageParams],
  );

  useEffect(() => {
    setPageResult(result.data?.page as PageResult);
  }, [result.data]);

  return [
    call,
    { page: { ...page, ...pageResult }, setPage, setPageSize, ...rest },
    result,
    reset,
  ];
};

export default usePaginatedApiCall;
