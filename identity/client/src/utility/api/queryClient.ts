/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { MutationCache, QueryCache, QueryClient } from "@tanstack/react-query";
import { notifyApiError } from "./errorNotification";

declare module "@tanstack/react-query" {
  interface Register {
    queryMeta: { skipErrorNotification?: boolean };
    mutationMeta: { skipErrorNotification?: boolean };
  }
}

const handleError = (
  error: unknown,
  meta: { skipErrorNotification?: boolean } | undefined,
) => {
  if (meta?.skipErrorNotification) return;
  notifyApiError(error);
};

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
      refetchOnWindowFocus: false,
    },
  },
  queryCache: new QueryCache({
    onError: (error, query) => handleError(error, query.meta),
  }),
  mutationCache: new MutationCache({
    onError: (error, _variables, _context, mutation) =>
      handleError(error, mutation.options.meta),
  }),
});
