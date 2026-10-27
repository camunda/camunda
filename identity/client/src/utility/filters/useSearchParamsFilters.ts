/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { createSearchParamsSync } from "src/utility/filters/searchParamsFilters";
import { useSearchParams } from "react-router-dom";
import { useCallback, useMemo } from "react";
function useSearchParamsFilters<T>(
  querySync: ReturnType<typeof createSearchParamsSync<T>>,
) {
  const [searchParams, setSearchParams] = useSearchParams();

  const searchParamsFilters = useMemo(() => {
    return querySync.parse(searchParams);
  }, [searchParams, querySync]);

  const setSearchParamsFilters = useCallback(
    (next: T) => {
      const search = querySync.serialize(next);
      if (search === searchParams.toString()) return;

      void setSearchParams(search, { replace: true });
    },
    [searchParams, setSearchParams, querySync],
  );

  return { searchParamsFilters, setSearchParamsFilters };
}

export { useSearchParamsFilters };
