/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import zod from 'zod';

const dateFilterSchema = zod.object({
  from: zod.string(),
  to: zod.string(),
});
const searchSchema = zod.tuple([zod.string(), zod.string()]);

const filtersSchema = zod.object({
  filter: zod
    .enum(['all-open', 'unassigned', 'assigned-to-me', 'completed'])
    .default('all-open'),
  sortBy: zod
    .enum(['creation', 'follow-up', 'due', 'completion'])
    .default('creation'),
  sortOrder: zod.enum(['asc', 'desc']).default('desc'),
  state: zod.enum(['CREATED', 'COMPLETED', 'CANCELED']).optional(),
  assigned: zod.boolean().optional(),
  assignee: zod.string().optional(),
  taskDefinitionId: zod.string().optional(),
  candidateGroup: zod.string().optional(),
  candidateUser: zod.string().optional(),
  processDefinitionKey: zod.string().optional(),
  processInstanceKey: zod.string().optional(),
  pageSize: zod.number().optional(),
  followUpDate: dateFilterSchema.optional(),
  dueDate: dateFilterSchema.optional(),
  sort: zod
    .array(
      zod.object({
        field: zod.enum([
          'creationTime',
          'dueDate',
          'followUpDate',
          'completionTime',
        ]),
        order: zod.enum(['ASC', 'DESC']),
      }),
    )
    .optional(),
  searchAfter: searchSchema.optional(),
  searchAfterOrEqual: searchSchema.optional(),
  searchBefore: searchSchema.optional(),
  searchBeforeOrEqual: searchSchema.optional(),
});

const DEFAULT_FILTERS = filtersSchema.parse({});

type TaskFilters = zod.infer<typeof filtersSchema>;

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
  const filters = result.success ? result.data : DEFAULT_FILTERS;

  return useMemo<TaskFilters>(() => filters, [filters]);
}

export {useTaskFilters};
export type {TaskFilters};
