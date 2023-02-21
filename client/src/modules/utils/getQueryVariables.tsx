/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FilterValues} from 'modules/constants/filterValues';
import {TaskStates} from 'modules/constants/taskStates';
import {GetTasksVariables} from 'modules/queries/get-tasks';

const getQueryVariables = (
  filter: string,
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
  const BASE_QUERY_VARIABLES = {
    pageSize,
    searchBefore,
    searchAfter,
    searchAfterOrEqual,
  } as const;

  switch (filter) {
    case FilterValues.ClaimedByMe: {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: true,
        assignee: userId!,
        state: TaskStates.Created,
      };
    }
    case FilterValues.Unclaimed: {
      return {
        ...BASE_QUERY_VARIABLES,
        assigned: false,
        state: TaskStates.Created,
      };
    }
    case FilterValues.Completed: {
      return {
        ...BASE_QUERY_VARIABLES,
        state: TaskStates.Completed,
      };
    }
    case FilterValues.AllOpen:
    default: {
      return {
        ...BASE_QUERY_VARIABLES,
        state: TaskStates.Created,
      };
    }
  }
};

export {getQueryVariables};
