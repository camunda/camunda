/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const INITIAL_SORT_ORDER = 'desc';

const sortOrderSchema = z.enum(['asc', 'desc']);

const sortParamsSchema = z.object({
  sortBy: z.enum(['timestamp', 'operationType', 'actorId']),
  sortOrder: sortOrderSchema,
});

type SortParams = z.infer<typeof sortParamsSchema>;

function getSortParams(search: string): SortParams | null {
  const params = new URLSearchParams(search);
  const sort = params.get('sort');

  if (sort === null) {
    return null;
  }

  const parts = sort.split('+');
  if (parts.length !== 2) {
    return null;
  }

  const [sortBy, sortOrder] = parts;

  const result = sortParamsSchema.safeParse({sortBy, sortOrder});
  return result.success ? result.data : null;
}

function toggleSorting(
  search: string,
  sortKey: string,
  currentSortOrder?: 'asc' | 'desc',
): string {
  const params = new URLSearchParams(search);

  if (currentSortOrder === undefined) {
    params.set('sort', `${sortKey}+${INITIAL_SORT_ORDER}`);
    return params.toString();
  }

  params.set(
    'sort',
    `${sortKey}+${currentSortOrder === 'asc' ? 'desc' : 'asc'}`,
  );
  return params.toString();
}

export {getSortParams, toggleSorting, INITIAL_SORT_ORDER, sortParamsSchema};
