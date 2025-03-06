/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {TaskFilters} from 'common/tasks/filters/useTaskFilters';
import type {TasksSearchBody} from 'v1/api/types';
import {getStateLocally} from 'common/local-storage';
import {formatRFC3339} from 'date-fns';

const SORT_BY_FIELD: Record<
  TaskFilters['sortBy'],
  'creationTime' | 'dueDate' | 'followUpDate' | 'completionTime' | 'priority'
> = {
  creation: 'creationTime',
  due: 'dueDate',
  'follow-up': 'followUpDate',
  completion: 'completionTime',
  priority: 'priority',
};

const getQueryVariables = (
  filters: Omit<
    TaskFilters,
    | 'assignee'
    | 'pageSize'
    | 'searchBefore'
    | 'searchBeforeOrEqual'
    | 'searchAfter'
    | 'searchAfterOrEqual'
  >,
  {
    assignee,
    pageSize,
    searchBefore,
    searchBeforeOrEqual,
    searchAfter,
    searchAfterOrEqual,
  }: Pick<
    TasksSearchBody,
    | 'assignee'
    | 'pageSize'
    | 'searchBefore'
    | 'searchBeforeOrEqual'
    | 'searchAfter'
    | 'searchAfterOrEqual'
  >,
): TasksSearchBody => {
  const {filter, sortBy, sortOrder, ...remainingFilters} = filters;
  const BASE_QUERY_VARIABLES: TasksSearchBody = {
    sort: [
      {
        field: SORT_BY_FIELD[sortBy],
        order: sortOrder.toUpperCase() as 'ASC' | 'DESC',
      },
    ],
    pageSize,
    searchBefore,
    searchBeforeOrEqual,
    searchAfter,
    searchAfterOrEqual,
  };
  const {taskVariables, ...parsedFilters} = convertFiltersToQueryVariables({
    ...remainingFilters,
    filter,
  });

  switch (filter) {
    case 'assigned-to-me': {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: true,
        assignee: assignee!,
        state: 'CREATED',
        ...parsedFilters,
      };
    }
    case 'unassigned': {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: false,
        state: 'CREATED',
        ...parsedFilters,
      };
    }
    case 'completed': {
      return {
        ...BASE_QUERY_VARIABLES,
        state: 'COMPLETED',
        ...parsedFilters,
      };
    }
    case 'all-open': {
      return {
        ...BASE_QUERY_VARIABLES,
        state: 'CREATED',
        ...parsedFilters,
      };
    }
    case 'custom':
    default: {
      return {
        ...BASE_QUERY_VARIABLES,
        ...parsedFilters,
        taskVariables,
      };
    }
  }
};

function convertFiltersToQueryVariables(
  filters: Omit<TaskFilters, 'sortBy' | 'sortOrder'>,
): TasksSearchBody {
  const {
    filter,
    dueDateFrom,
    dueDateTo,
    followUpDateFrom,
    followUpDateTo,
    ...restFilters
  } = filters;
  const updatedFilters: TasksSearchBody = restFilters;
  const customFilters = getStateLocally('customFilters')?.[filter];

  if (customFilters !== undefined && Array.isArray(customFilters?.variables)) {
    updatedFilters.taskVariables = customFilters.variables.map<{
      name: string;
      value: string;
      operator: 'eq';
    }>(({name, value}) => ({
      name: name!,
      value: value!,
      operator: 'eq',
    }));
  }

  if (filters.dueDateFrom !== undefined) {
    updatedFilters.dueDate = {
      from: formatRFC3339(filters.dueDateFrom),
    };
  }

  if (filters.dueDateTo !== undefined) {
    updatedFilters.dueDate = {
      ...updatedFilters.dueDate,
      to: formatRFC3339(filters.dueDateTo),
    };
  }

  if (filters.followUpDateFrom !== undefined) {
    updatedFilters.followUpDate = {
      from: formatRFC3339(filters.followUpDateFrom),
    };
  }

  if (filters.followUpDateTo !== undefined) {
    updatedFilters.followUpDate = {
      ...updatedFilters.followUpDate,
      to: formatRFC3339(filters.followUpDateTo),
    };
  }

  return updatedFilters;
}

export {getQueryVariables};
