/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';
import {Task} from 'modules/types';

import {
  completedTask,
  unclaimedTask,
  claimedTask,
} from 'modules/mock-schema/mocks/task-details';

type TaskDetailsQueryVariables = {
  id: Task['id'];
};
interface GetTaskDetails {
  task: {
    id: Task['id'];
    name: Task['name'];
    assignee: Task['assignee'];
    workflowName: Task['workflowName'];
    creationTime: Task['creationTime'];
    completionTime: Task['completionTime'];
    taskState: Task['taskState'];
  };
}

const GET_TASK_DETAILS = gql`
  query GetTask($id: String!) {
    task(id: $id) {
      id
      name
      workflowName
      assignee {
        username
        firstname
        lastname
      }
      creationTime
      completionTime
      taskState
    }
  }
`;

const mockGetTaskUnclaimed = {
  request: {
    query: GET_TASK_DETAILS,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: unclaimedTask,
    },
  },
};

const mockGetTaskClaimed = {
  request: {
    query: GET_TASK_DETAILS,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: claimedTask,
    },
  },
};

const mockGetTaskCompleted = {
  request: {
    query: GET_TASK_DETAILS,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: completedTask,
    },
  },
};

export type {GetTaskDetails, TaskDetailsQueryVariables};
export {
  GET_TASK_DETAILS,
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
  mockGetTaskClaimed,
};
