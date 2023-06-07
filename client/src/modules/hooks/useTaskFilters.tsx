/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {enum as ZodEnum, object as ZodObject, infer as ZodInfer} from 'zod';

const filtersSchema = ZodObject({
  filter: ZodEnum([
    'all-open',
    'unassigned',
    'assigned-to-me',
    'completed',
  ]).default('all-open'),
  sortBy: ZodEnum(['creation', 'follow-up', 'due', 'completion']).default(
    'creation',
  ),
  sortOrder: ZodEnum(['asc', 'desc']).default('desc'),
});

const DEFAULT_FILTERS = filtersSchema.parse({});

type TaskFilters = ZodInfer<typeof filtersSchema>;

function useTaskFilters(): TaskFilters {
  const [params, setSearchParams] = useSearchParams();
  const currentFilter = params.get('filter');
  const OLD_FILTERS = {
    'claimed-by-me': 'assigned-to-me',
    unclaimed: 'unassigned',
  } as const;

  useEffect(() => {
    if (
      currentFilter !== null &&
      Object.keys(OLD_FILTERS).includes(currentFilter)
    ) {
      params.set(
        'filter',
        OLD_FILTERS[currentFilter as keyof typeof OLD_FILTERS],
      );

      setSearchParams(params, {
        replace: true,
      });
    }
  });

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
