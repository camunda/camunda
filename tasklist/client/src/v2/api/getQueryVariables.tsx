/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type QueryUserTasksRequestBody} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {getStateLocally} from 'common/local-storage';
import {
  numberFiltersSchema,
  type TaskFilters,
} from 'v2/features/tasks/filters/useTaskFilters';

const SORT_BY_FIELD: Record<
  TaskFilters['sortBy'],
  'creationDate' | 'dueDate' | 'followUpDate' | 'completionDate' | 'priority'
> = {
  creation: 'creationDate',
  due: 'dueDate',
  'follow-up': 'followUpDate',
  completion: 'completionDate',
  priority: 'priority',
};

const getQueryVariables = (
  filters: Omit<TaskFilters, 'assignee'>,
  {currentUserId, pageSize}: {pageSize: number; currentUserId?: string},
): QueryUserTasksRequestBody => {
  const {filter, sortBy, sortOrder, ...remainingFilters} = filters;
  const BASE_QUERY_VARIABLES: QueryUserTasksRequestBody = {
    sort: [
      {
        field: SORT_BY_FIELD[sortBy],
        order: sortOrder.toLocaleLowerCase() as 'asc' | 'desc',
      },
    ],
    page: {
      limit: pageSize,
    },
  };
  const {variables = [], ...parsedFilters} = convertFiltersToQueryVariables({
    ...remainingFilters,
    filter,
  });

  switch (filter) {
    case 'assigned-to-me': {
      return {
        ...BASE_QUERY_VARIABLES,
        filter: {
          assignee: currentUserId!,
          state: 'CREATED',
          ...parsedFilters,
        },
      };
    }
    case 'unassigned': {
      return {
        ...BASE_QUERY_VARIABLES,
        filter: {
          state: 'CREATED',
          ...parsedFilters,
        },
      };
    }
    case 'completed': {
      return {
        ...BASE_QUERY_VARIABLES,
        filter: {
          state: 'COMPLETED',
          ...parsedFilters,
        },
      };
    }
    case 'all-open': {
      return {
        ...BASE_QUERY_VARIABLES,
        filter: {
          state: 'CREATED',
          ...parsedFilters,
        },
      };
    }
    case 'custom':
    default: {
      return {
        ...BASE_QUERY_VARIABLES,
        filter:
          variables.length === 0
            ? parsedFilters
            : {
                ...parsedFilters,
                variables,
              },
      };
    }
  }
};

function convertFiltersToQueryVariables(
  filters: Omit<TaskFilters, 'sortBy' | 'sortOrder'>,
): NonNullable<QueryUserTasksRequestBody['filter']> {
  const {
    filter,
    candidateGroup,
    candidateUser,
    processInstanceKey,
    processDefinitionKey,
    userTaskKey,
    ...restFilters
  } = filters;
  const numberFilters =
    numberFiltersSchema.safeParse({
      processInstanceKey,
      processDefinitionKey,
      userTaskKey,
    }).data ?? {};
  const updatedFilters: QueryUserTasksRequestBody['filter'] = {
    ...restFilters,
    ...numberFilters,
  };
  const customFilters = getStateLocally('customFilters')?.[filter];

  if (customFilters !== undefined && Array.isArray(customFilters?.variables)) {
    updatedFilters.variables = customFilters.variables.map(({name, value}) => ({
      name: name!,
      value: value!,
    }));
  }

  if (candidateGroup !== undefined) {
    updatedFilters.candidateGroups = [candidateGroup];
  }

  if (candidateUser !== undefined) {
    updatedFilters.candidateUsers = [candidateUser];
  }

  return updatedFilters;
}

export {getQueryVariables};
