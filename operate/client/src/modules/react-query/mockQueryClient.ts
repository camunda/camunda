/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient} from '@tanstack/react-query';

interface MockQueryClientOptions {
  /** Forcefully disables queries refetch intervals in tests. @default true */
  forceNoRetchInterval?: boolean;
}

function getMockQueryClient(options?: MockQueryClientOptions) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: Infinity,
        staleTime: Infinity,
      },
      mutations: {
        retry: false,
        gcTime: Infinity,
      },
    },
  });

  if (options?.forceNoRetchInterval !== false) {
    // TanStack Query v5 applies query specific options after the initial default options.
    // In tests, we want to ensure that refetch intervals are always disabled,
    // unless explicitly enabled by a test case.
    const resolvesOptions = queryClient.defaultQueryOptions.bind(queryClient);
    queryClient.defaultQueryOptions = (options) => {
      const defaultedOptions = resolvesOptions(options);
      defaultedOptions.refetchInterval = false;
      return defaultedOptions;
    };
  }

  queryClient.clear();
  return queryClient;
}

export {getMockQueryClient};
