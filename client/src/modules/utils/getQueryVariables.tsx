/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FilterValues} from 'modules/constants/filterValues';
import {TaskStates} from 'modules/constants/taskStates';

const getQueryVariables = (
  filter: string,
  {
    username,
    pageSize,
    searchBefore,
    searchAfter,
    searchAfterOrEqual,
  }: {
    username?: string;
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
        assignee: username,
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
