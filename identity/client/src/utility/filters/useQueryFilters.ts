/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { createQuerySync } from "src/utility/filters/queryFilters.ts";
import { useLocation, useNavigate } from "react-router-dom";
import { useMemo } from "react";

function useQueryFilters<T>(querySync: ReturnType<typeof createQuerySync<T>>) {
  const location = useLocation();
  const navigate = useNavigate();

  const queryFilters = useMemo(() => {
    return querySync.parse(location.search);
  }, [location.search, querySync]);

  const setQueryFilters = (next: T) => {
    const nextSearch = querySync.serialize(next);
    if (nextSearch === location.search) {
      return;
    }

    void navigate({ search: querySync.serialize(next) }, { replace: true });
  };

  return { queryFilters, setQueryFilters };
}

export { useQueryFilters };
