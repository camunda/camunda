/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import {gql} from '@apollo/client';
import {
  tasks,
  tasksClaimedByDemoUser,
  unclaimedTasks,
  completedTasks,
} from 'modules/mock-schema/mocks/tasks';
import {Task} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';
import {MAX_TASKS_PER_REQUEST} from 'modules/constants/tasks';

interface GetTasks {
  tasks: ReadonlyArray<
    Pick<
      Task,
      | 'id'
      | 'name'
      | 'assignee'
      | 'processName'
      | 'creationTime'
      | 'taskState'
      | 'sortValues'
      | 'isFirst'
    >
  >;
}

interface GetAllOpenVariables {
  searchBefore?: string[];
  searchAfter?: string[];
  searchAfterOrEqual?: readonly string[];
  pageSize?: number;
  isPolling?: boolean;
}

interface GetClaimedByMeVariables {
  searchBefore?: string[];
  searchAfter?: string[];
  searchAfterOrEqual?: readonly string[];
  pageSize?: number;
  assignee: string;
  state: typeof TaskStates.Created;
  isPolling?: boolean;
}

interface GetUnclaimedVariables {
  searchBefore?: string[];
  searchAfter?: string[];
  searchAfterOrEqual?: readonly string[];
  pageSize?: number;
  assigned: false;
  state: typeof TaskStates.Created;
  isPolling?: boolean;
}

interface GetCompletedVariables {
  searchBefore?: string[];
  searchAfter?: string[];
  searchAfterOrEqual?: readonly string[];
  pageSize?: number;
  state: typeof TaskStates.Completed;
  isPolling?: boolean;
}

interface GetNewProcessInstanceTasksVariables {
  state: typeof TaskStates.Created;
  processInstanceId: string;
}

type GetTasksVariables =
  | GetAllOpenVariables
  | GetClaimedByMeVariables
  | GetUnclaimedVariables
  | GetCompletedVariables
  | GetNewProcessInstanceTasksVariables;

const GET_TASKS = gql`
  query GetTasks(
    $assignee: String
    $assigned: Boolean
    $state: TaskState
    $pageSize: Int
    $searchAfter: [String!]
    $searchBefore: [String!]
    $searchAfterOrEqual: [String!]
    $processInstanceId: String
    $processDefinitionId: String
  ) {
    tasks(
      query: {
        assignee: $assignee
        assigned: $assigned
        state: $state
        pageSize: $pageSize
        searchAfter: $searchAfter
        searchBefore: $searchBefore
        searchAfterOrEqual: $searchAfterOrEqual
        processInstanceId: $processInstanceId
        processDefinitionId: $processDefinitionId
      }
    ) {
      id
      name
      processName
      assignee
      creationTime
      taskState
      sortValues
      isFirst
    }
  }
`;

const mockGetAllOpenTasks = (isRunAfterMutation?: boolean) =>
  ({
    request: {
      query: GET_TASKS,
      variables: {
        state: TaskStates.Created,
        pageSize: MAX_TASKS_PER_REQUEST,
        isRunAfterMutation,
      },
    },
    result: {
      data: {
        tasks,
      },
    },
  } as const);

const mockFetchPreviousTasks = (sortValues = []) =>
  ({
    request: {
      query: GET_TASKS,
      variables: {
        state: TaskStates.Created,
        pageSize: MAX_TASKS_PER_REQUEST,
        searchBefore: sortValues,
      },
    },
    result: {
      data: {
        tasks,
      },
    },
  } as const);

const mockFetchNextTasks = (sortValues = []) =>
  ({
    request: {
      query: GET_TASKS,
      variables: {
        state: TaskStates.Created,
        pageSize: MAX_TASKS_PER_REQUEST,
        searchAfter: sortValues,
      },
    },
    result: {
      data: {
        tasks,
      },
    },
  } as const);

const mockGetAllOpenTasksUnclaimed = (isRunAfterMutation?: boolean) =>
  ({
    request: {
      query: GET_TASKS,
      variables: {
        state: TaskStates.Created,
        pageSize: MAX_TASKS_PER_REQUEST,
        isRunAfterMutation,
      },
    },
    result: {
      data: {
        tasks: unclaimedTasks,
      },
    },
  } as const);

const mockGetEmptyTasks = {
  request: {
    query: GET_TASKS,
    variables: {
      state: TaskStates.Created,
      pageSize: MAX_TASKS_PER_REQUEST,
    },
  },
  result: {
    data: {
      tasks: [],
    },
  },
} as const;

const mockGetClaimedByMe = {
  request: {
    query: GET_TASKS,
    variables: {
      assigned: true,
      assignee: 'demo',
      state: TaskStates.Created,
      pageSize: MAX_TASKS_PER_REQUEST,
    },
  },
  result: {
    data: {
      tasks: tasksClaimedByDemoUser,
    },
  },
} as const;

const mockGetUnclaimed = {
  request: {
    query: GET_TASKS,
    variables: {
      assigned: false,
      state: TaskStates.Created,
      pageSize: MAX_TASKS_PER_REQUEST,
    },
  },
  result: {
    data: {
      tasks: unclaimedTasks,
    },
  },
} as const;

const mockGetCompleted = {
  request: {
    query: GET_TASKS,
    variables: {
      state: TaskStates.Completed,
      pageSize: MAX_TASKS_PER_REQUEST,
    },
  },
  result: {
    data: {
      tasks: completedTasks,
    },
  },
} as const;

export type {GetTasks, GetTasksVariables};
export {
  GET_TASKS,
  mockGetAllOpenTasks,
  mockGetEmptyTasks,
  mockGetClaimedByMe,
  mockGetUnclaimed,
  mockGetCompleted,
  mockGetAllOpenTasksUnclaimed,
  mockFetchPreviousTasks,
  mockFetchNextTasks,
};
