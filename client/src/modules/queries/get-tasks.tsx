/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import {gql} from 'apollo-boost';
import {tasks} from '../mock-schema/mocks/tasks';

import {Task} from 'modules/types';

interface GetTasks {
  tasks: ReadonlyArray<{
    key: Task['key'];
    name: Task['name'];
    assignee: Task['assignee'];
    workflowName: Task['workflowName'];
    creationTime: Task['creationTime'];
  }>;
}

interface GetAllOpenVariables {}

interface GetClaimedByMeVariables {
  assignee: string;
}

interface GetUnclaimedVariables {
  assigned: false;
}

interface GetCompletedVariables {
  state: 'COMPLETED';
}

type GetTasksVariables =
  | GetAllOpenVariables
  | GetClaimedByMeVariables
  | GetUnclaimedVariables
  | GetCompletedVariables;

const GET_TASKS =
  process.env.NODE_ENV === 'test'
    ? gql`
        query GetTasks($assignee: ID, $assigned: Boolean, $state: String) {
          tasks(assignee: $assignee, assigned: $assigned, state: $state) {
            key
            name
            workflowName
            assignee
            creationTime
          }
        }
      `
    : gql`
        query GetTasks($assignee: ID, $assigned: Boolean, $state: String) {
          tasks(assignee: $assignee, assigned: $assigned, state: $state)
            @client {
            key
            name
            workflowName
            assignee
            creationTime
          }
        }
      `;

const mockGetAllOpenTasks = {
  request: {
    query: GET_TASKS,
  },
  result: {
    data: {
      tasks,
    },
  },
} as const;

const mockGetEmptyTasks = {
  request: {
    query: GET_TASKS,
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
      assignee: 'demo',
    },
  },
  result: {
    data: {
      tasks,
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
      tasks,
    },
  },
} as const;

const mockGetCompleted = {
  request: {
    query: GET_TASKS,
    variables: {
      state: 'COMPLETED',
    },
  },
  result: {
    data: {
      tasks,
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
};
