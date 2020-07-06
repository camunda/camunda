/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';
import {Task} from 'modules/types';

import {
  taskWithVariables,
  taskWithoutVariables,
  taskCompletedWithVariables,
} from 'modules/mock-schema/mocks/task-variables';

type TaskVariablesQueryVariables = {
  id: Task['id'];
};
interface GetTaskVariables {
  task: {
    id: Task['id'];
    variables: Task['variables'];
  };
}

const GET_TASK_VARIABLES =
  process.env.NODE_ENV === 'test'
    ? gql`
        query GetTask($id: String!) {
          task(id: $id) {
            id
            variables
          }
        }
      `
    : gql`
        query GetTask($id: String!) {
          task(id: $id) {
            id
            variables {
              name
              value
            }
          }
        }
      `;

const mockTaskWithVariables = {
  request: {
    query: GET_TASK_VARIABLES,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: taskWithVariables,
    },
  },
};

const mockTaskWithoutVariables = {
  request: {
    query: GET_TASK_VARIABLES,
    variables: {id: '1'},
  },
  result: {
    data: {
      task: taskWithoutVariables,
    },
  },
};

const mockTaskCompletedWithVariables = {
  request: {
    query: GET_TASK_VARIABLES,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: taskCompletedWithVariables,
    },
  },
};

export type {GetTaskVariables, TaskVariablesQueryVariables};
export {
  GET_TASK_VARIABLES,
  mockTaskWithVariables,
  mockTaskWithoutVariables,
  mockTaskCompletedWithVariables,
};
