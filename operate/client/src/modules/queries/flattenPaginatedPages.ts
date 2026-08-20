/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {InfiniteData} from '@tanstack/react-query';

type PageResponse = {
  items: unknown[];
  page: {totalItems: number};
};

/**
 * Shared select function for paginated infinite queries.
 * Flattens infinite query pages into a single items array with totalCount.
 */
function flattenPaginatedPages<T extends PageResponse>(
  data: InfiniteData<T>,
): {items: T['items']; totalCount: number} {
  return {
    items: data.pages.flatMap((page) => page.items),
    totalCount: data.pages[0]?.page.totalItems ?? 0,
  };
}

export {flattenPaginatedPages};
