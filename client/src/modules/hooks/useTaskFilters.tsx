/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {enum as ZodEnum, object as ZodObject, infer as ZodInfer} from 'zod';

const filtersSchema = ZodObject({
  filter: ZodEnum([
    'all-open',
    'unclaimed',
    'claimed-by-me',
    'completed',
  ]).default('all-open'),
  sortBy: ZodEnum(['creation', 'follow-up', 'due']).default('creation'),
  sortOrder: ZodEnum(['asc', 'desc']).default('desc'),
});

const DEFAULT_FILTERS = filtersSchema.parse({});

type TaskFilters = ZodInfer<typeof filtersSchema>;

function useTaskFilters(): TaskFilters {
  const [params] = useSearchParams();
  const result = filtersSchema.safeParse(Object.fromEntries(params.entries()));
  const {filter, sortBy, sortOrder} = result.success
    ? result.data
    : DEFAULT_FILTERS;

  return useMemo<TaskFilters>(
    () => ({filter, sortBy, sortOrder}),
    [filter, sortBy, sortOrder],
  );
}

export {useTaskFilters};
export type {TaskFilters};
