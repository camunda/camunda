/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { createSearchParamsSync } from "src/utility/filters/searchParamsFilters";
import { useLocation, useNavigate } from "react-router-dom";
import { useMemo } from "react";

function useSearchParamsFilters<T>(
  querySync: ReturnType<typeof createSearchParamsSync<T>>,
) {
  const location = useLocation();
  const navigate = useNavigate();

  const searchParamsFilters = useMemo(() => {
    return querySync.parse(location.search);
  }, [location.search, querySync]);

  const setSearchParamsFilters = (next: T) => {
    void navigate({ search: querySync.serialize(next) }, { replace: true });
  };

  return { searchParamsFilters, setSearchParamsFilters };
}

export { useSearchParamsFilters };
