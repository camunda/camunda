/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FilterValues} from 'modules/constants/filterValues';
import {TaskStates} from 'modules/constants/taskStates';

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
) => {
  switch (filter) {
    case FilterValues.ClaimedByMe: {
      return {
        assigned: true,
        assignee: userId,
        state: TaskStates.Created,
        pageSize,
        searchBefore,
        searchAfter,
        searchAfterOrEqual,
      };
    }
    case FilterValues.Unclaimed: {
      return {
        assigned: false,
        state: TaskStates.Created,
        pageSize,
        searchBefore,
        searchAfter,
        searchAfterOrEqual,
      };
    }
    case FilterValues.Completed: {
      return {
        state: TaskStates.Completed,
        pageSize,
        searchBefore,
        searchAfter,
        searchAfterOrEqual,
      };
    }
    case FilterValues.AllOpen:
    default: {
      return {
        state: TaskStates.Created,
        pageSize,
        searchBefore,
        searchAfter,
        searchAfterOrEqual,
      };
    }
  }
};

export {getQueryVariables};
