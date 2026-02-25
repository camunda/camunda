/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { createQuerySync } from "src/utility/filters/queryFilters.ts";
import { useLocation, useNavigate } from "react-router-dom";

function useQueryFilters<T>(querySync: ReturnType<typeof createQuerySync<T>>) {
  const location = useLocation();
  const navigate = useNavigate();

  const queryFilters = querySync.parse(location.search);

  const setQueryFilters = (next: T) => {
    void navigate({ search: querySync.serialize(next) });
  };

  return { queryFilters, setQueryFilters };
}

export { useQueryFilters };
