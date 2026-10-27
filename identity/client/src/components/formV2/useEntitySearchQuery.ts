/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useCallback, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import useDebounce from "react-debounced";

export type EntitySearchQuery<Entity> = {
  queryKey: readonly unknown[];
  // The generated query builders type their queryFn's context param more
  // narrowly per entity; `any` here just needs to accept whatever they pass.
  queryFn?: (
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    context: any,
  ) => Promise<{ items: Entity[] }> | { items: Entity[] };
};

/**
 * Fetches items via the given search query as the user types, debounced so
 * a query isn't fired on every keystroke. Disabled while the search text is
 * empty, so opening the popover doesn't trigger an unfiltered fetch of the
 * entire entity list.
 */
export function useEntitySearchQuery<Entity>(
  buildQuery: (search: string) => EntitySearchQuery<Entity>,
) {
  const debounce = useDebounce();
  const [search, setSearch] = useState("");

  const {
    data,
    isLoading,
    error,
    refetch: reload,
  } = useQuery<{ items: Entity[] }>({
    ...buildQuery(search),
    enabled: search !== "",
  });

  const onInputChange = useCallback(
    (value: string) => {
      debounce(() => setSearch(value));
    },
    [debounce],
  );

  return {
    items: data?.items ?? [],
    search,
    isLoading,
    error,
    reload,
    onInputChange,
  };
}
