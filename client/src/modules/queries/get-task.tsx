/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';
import {Task} from 'modules/types';

import {taskCreated, taskCompleted} from 'modules/mock-schema/mocks/task';

type TaskQueryVariables = {
  id: Task['id'];
};

interface GetTask {
  task: {
    assignee: Task['assignee'];
    taskState: Task['taskState'];
  };
}

const GET_TASK =
  process.env.NODE_ENV === 'test'
    ? gql`
        query GetTask($id: String!) {
          task(id: $id) {
            id
            assignee
            taskState
          }
        }
      `
    : gql`
        query GetTask($id: String!) {
          task(id: $id) {
            id
            assignee @client
            taskState @client
          }
        }
      `;

const mockGetTaskCreated = {
  request: {
    query: GET_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: taskCreated,
    },
  },
};

const mockGetTaskCompleted = {
  request: {
    query: GET_TASK,
    variables: {id: '0'},
  },
  result: {
    data: {
      task: taskCompleted,
    },
  },
};

export type {GetTask, TaskQueryVariables};
export {GET_TASK, mockGetTaskCreated, mockGetTaskCompleted};
