/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {TaskFilters} from 'modules/hooks/useTaskFilters';
import {TasksSearchBody} from 'modules/types';

const SORT_BY_FIELD: Record<
  TaskFilters['sortBy'],
  'creationTime' | 'dueDate' | 'followUpDate' | 'completionTime'
> = {
  creation: 'creationTime',
  due: 'dueDate',
  'follow-up': 'followUpDate',
  completion: 'completionTime',
};

const getQueryVariables = (
  filters: TaskFilters,
  {
    assignee,
    pageSize,
    searchBefore,
    searchAfter,
    searchAfterOrEqual,
  }: Pick<
    TasksSearchBody,
    | 'assignee'
    | 'pageSize'
    | 'searchBefore'
    | 'searchAfter'
    | 'searchAfterOrEqual'
  >,
): TasksSearchBody => {
  const {filter, sortBy, sortOrder} = filters;
  const BASE_QUERY_VARIABLES: TasksSearchBody = {
    sort: [
      {
        field: SORT_BY_FIELD[sortBy],
        order: sortOrder.toUpperCase() as 'ASC' | 'DESC',
      },
    ],
    pageSize,
    searchBefore,
    searchAfter,
    searchAfterOrEqual,
  };

  switch (filter) {
    case 'assigned-to-me': {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: true,
        assignee: assignee!,
        state: 'CREATED',
      };
    }
    case 'unassigned': {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: false,
        state: 'CREATED',
      };
    }
    case 'completed': {
      return {
        ...BASE_QUERY_VARIABLES,
        state: 'COMPLETED',
      };
    }
    case 'all-open':
    default: {
      return {
        ...BASE_QUERY_VARIABLES,
        state: 'CREATED',
      };
    }
  }
};

export {getQueryVariables};
