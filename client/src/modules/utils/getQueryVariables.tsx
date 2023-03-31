/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {TaskStates} from 'modules/constants/taskStates';
import {TaskFilters} from 'modules/hooks/useTaskFilters';
import {GetTasksVariables} from 'modules/queries/get-tasks';

const SORT_BY_FIELD: Record<TaskFilters['sortBy'], string> = {
  creation: 'creationTime',
  due: 'dueDate',
  'follow-up': 'followUpDate',
} as const;

const getQueryVariables = (
  filters: TaskFilters,
  {
    userId,
    pageSize,
    searchBefore,
    searchAfter,
    searchAfterOrEqual,
  }: {
    userId?: string;
    pageSize?: number;
    searchBefore?: string[];
    searchAfter?: string[];
    searchAfterOrEqual?: string[];
  },
): GetTasksVariables => {
  const {filter, sortBy, sortOrder} = filters;
  const BASE_QUERY_VARIABLES = {
    sort: [
      {
        field: SORT_BY_FIELD[sortBy],
        order: sortOrder.toUpperCase(),
      },
    ],
    sortOrder,
    pageSize,
    searchBefore,
    searchAfter,
    searchAfterOrEqual,
  } as const;

  switch (filter) {
    case 'assigned-to-me': {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: true,
        assignee: userId!,
        state: TaskStates.Created,
      };
    }
    case 'unassigned': {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: false,
        state: TaskStates.Created,
      };
    }
    case 'completed': {
      return {
        ...BASE_QUERY_VARIABLES,
        state: TaskStates.Completed,
      };
    }
    case 'all-open':
    default: {
      return {
        ...BASE_QUERY_VARIABLES,
        state: TaskStates.Created,
      };
    }
  }
};

export {getQueryVariables};
