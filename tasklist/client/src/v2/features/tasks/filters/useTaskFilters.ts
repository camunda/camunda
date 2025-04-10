/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {z} from 'zod';
import {queryUserTasksRequestBodySchema} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {querySortOrderSchema} from '@vzeta/camunda-api-zod-schemas';

const apiFiltersSchema = queryUserTasksRequestBodySchema.shape.filter
  .unwrap()
  .omit({
    localVariables: true,
    processInstanceVariables: true,
  });

const filtersSchema = z
  .object({
    filter: z.string().default('all-open'),
    sortBy: z
      .enum(['creation', 'follow-up', 'due', 'completion', 'priority'])
      .default('creation'),
    sortOrder: querySortOrderSchema.default('desc'),
    candidateGroup: z.string().optional(),
    candidateUser: z.string().optional(),
    processInstanceKey: z.string().optional(),
    processDefinitionKey: z.string().optional(),
    userTaskKey: z.string().optional(),
    dueDateFrom: z.coerce.date().optional(),
    dueDateTo: z.coerce.date().optional(),
    followUpDateFrom: z.coerce.date().optional(),
    followUpDateTo: z.coerce.date().optional(),
  })
  .merge(
    apiFiltersSchema.omit({
      candidateGroup: true,
      candidateUser: true,
      processInstanceKey: true,
      processDefinitionKey: true,
      userTaskKey: true,
      creationDate: true,
      dueDate: true,
      followUpDate: true,
      completionDate: true,
    }),
  );

const numberFiltersSchema = z
  .object({
    processInstanceKey: z.coerce.number().optional(),
    processDefinitionKey: z.coerce.number().optional(),
    userTaskKey: z.coerce.number().optional(),
  })
  .transform((result) =>
    Object.fromEntries(
      Object.entries(result).filter(([_, value]) => typeof value === 'number'),
    ),
  );

const DEFAULT_FILTERS = filtersSchema.parse({});

type TaskFilters = z.infer<typeof filtersSchema>;

function useTaskFilters(): TaskFilters {
  const [params] = useSearchParams();

  const queryString = params.toString();

  return useMemo<TaskFilters>(() => {
    const result = filtersSchema.safeParse(
      Object.fromEntries(new URLSearchParams(queryString).entries()),
    );
    const filters = result.success ? result.data : DEFAULT_FILTERS;

    return filters;
  }, [queryString]);
}

export {useTaskFilters, filtersSchema, numberFiltersSchema};
export type {TaskFilters};
