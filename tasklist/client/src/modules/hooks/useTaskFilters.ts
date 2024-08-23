/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import zod from 'zod';

const searchSchema = zod.tuple([zod.string(), zod.string()]);

const filtersSchema = zod.object({
  filter: zod.string().default('all-open'),
  sortBy: zod
    .enum(['creation', 'follow-up', 'due', 'completion', 'priority'])
    .default('creation'),
  sortOrder: zod.enum(['asc', 'desc']).default('desc'),
  state: zod.enum(['CREATED', 'COMPLETED', 'CANCELED']).optional(),
  assigned: zod
    .enum(['true', 'false'])
    .transform((value) => value === 'true')
    .optional(),
  assignee: zod.string().optional(),
  taskDefinitionId: zod.string().optional(),
  candidateGroup: zod.string().optional(),
  candidateUser: zod.string().optional(),
  processDefinitionKey: zod.string().optional(),
  processInstanceKey: zod.string().optional(),
  pageSize: zod.coerce.number().optional(),
  tenantIds: zod
    .string()
    .transform<string[] | undefined>((value) => {
      if (value === undefined) {
        return undefined;
      }
      const parsedValue = JSON.parse(value);

      if (Array.isArray(parsedValue)) {
        return parsedValue;
      }

      return [parsedValue];
    })
    .optional(),
  dueDateFrom: zod.coerce.date().optional(),
  dueDateTo: zod.coerce.date().optional(),
  followUpDateFrom: zod.coerce.date().optional(),
  followUpDateTo: zod.coerce.date().optional(),
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

  const queryString = params.toString();

  return useMemo<TaskFilters>(() => {
    const result = filtersSchema.safeParse(
      Object.fromEntries(new URLSearchParams(queryString).entries()),
    );
    const filters = result.success ? result.data : DEFAULT_FILTERS;

    return filters;
  }, [queryString]);
}

export {useTaskFilters, filtersSchema};
export type {TaskFilters};
