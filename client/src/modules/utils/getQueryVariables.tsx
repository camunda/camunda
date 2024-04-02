/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {TaskFilters} from 'modules/hooks/useTaskFilters';
import {TasksSearchBody} from 'modules/types';
import {getStateLocally} from './localStorage';
import {formatRFC3339} from 'date-fns';

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
    searchAfter,
    searchAfterOrEqual,
  };
  const {taskVariables, ...parsedFilters} =
    convertFiltersToQueryVariables(remainingFilters);

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
    case 'custom': {
      return {
        ...BASE_QUERY_VARIABLES,
        ...parsedFilters,
        taskVariables,
      };
    }
    case 'all-open':
    default: {
      return {
        ...BASE_QUERY_VARIABLES,
        state: 'CREATED',
        ...parsedFilters,
      };
    }
  }
};

function convertFiltersToQueryVariables(
  filters: Omit<TaskFilters, 'filter' | 'sortBy' | 'sortOrder'>,
): TasksSearchBody {
  const {
    dueDateFrom,
    dueDateTo,
    followUpDateFrom,
    followUpDateTo,
    ...restFilters
  } = filters;
  const updatedFilters: TasksSearchBody = restFilters;
  const customFilters = getStateLocally('customFilters')?.custom;

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
