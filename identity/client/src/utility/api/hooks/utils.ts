/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { PaginationRequestParams } from "./usePagination";

export const mergeParams = (
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
