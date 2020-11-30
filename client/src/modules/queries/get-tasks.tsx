/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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

interface GetTasks {
  tasks: ReadonlyArray<{
    id: Task['id'];
    name: Task['name'];
    assignee: Task['assignee'];
    workflowName: Task['workflowName'];
    creationTime: Task['creationTime'];
    taskState: Task['taskState'];
  }>;
}

interface GetAllOpenVariables {}

interface GetClaimedByMeVariables {
  assignee: string;
  state: typeof TaskStates.Created;
}

interface GetUnclaimedVariables {
  assigned: false;
}

interface GetCompletedVariables {
  state: typeof TaskStates.Completed;
}

type GetTasksVariables =
  | GetAllOpenVariables
  | GetClaimedByMeVariables
  | GetUnclaimedVariables
  | GetCompletedVariables;

const GET_TASKS = gql`
  query GetTasks($assignee: String, $assigned: Boolean, $state: TaskState) {
    tasks(query: {assignee: $assignee, assigned: $assigned, state: $state}) {
      id
      name
      workflowName
      assignee {
        username
        firstname
        lastname
      }
      creationTime
      taskState
    }
  }
`;

const mockGetAllOpenTasks = {
  request: {
    query: GET_TASKS,
    variables: {
      state: TaskStates.Created,
    },
  },
  result: {
    data: {
      tasks,
    },
  },
} as const;

const mockGetAllOpenTasksUnclaimed = {
  request: {
    query: GET_TASKS,
    variables: {
      state: TaskStates.Created,
    },
  },
  result: {
    data: {
      tasks: unclaimedTasks,
    },
  },
} as const;

const mockGetEmptyTasks = {
  request: {
    query: GET_TASKS,
    variables: {
      state: TaskStates.Created,
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
};
