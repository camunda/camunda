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
} from 'modules/mock-schema/mocks/task';

interface GetTask {
  task: {
    key: Task['key'];
    name: Task['name'];
    assignee: Task['assignee'];
    workflowName: Task['workflowName'];
    creationTime: Task['creationTime'];
    completionTime: Task['completionTime'];
  };
}

interface GetTaskVariables {
  key: Task['key'];
}

const GET_TASK =
  process.env.NODE_ENV === 'test'
    ? gql`
        query GetTask($key: ID!) {
          task(key: $key) {
            key
            name
            workflowName
            assignee
            creationTime
            completionTime
          }
        }
      `
    : gql`
        query GetTask($key: ID!) {
          task(key: $key) @client {
            key
            name
            workflowName
            assignee
            creationTime
            completionTime
          }
        }
      `;

const mockGetTaskUnclaimed = {
  request: {
    query: GET_TASK,
    variables: {key: '1'},
  },
  result: {
    data: {
      task: unclaimedTask,
    },
  },
};

const mockGetTaskClaimed = {
  request: {
    query: GET_TASK,
    variables: {key: '1'},
  },
  result: {
    data: {
      task: claimedTask,
    },
  },
};

const mockGetTaskCompleted = {
  request: {
    query: GET_TASK,
    variables: {key: '0'},
  },
  result: {
    data: {
      task: completedTask,
    },
  },
};

export type {GetTask, GetTaskVariables};
export {
  GET_TASK,
  mockGetTaskUnclaimed,
  mockGetTaskCompleted,
  mockGetTaskClaimed,
};
