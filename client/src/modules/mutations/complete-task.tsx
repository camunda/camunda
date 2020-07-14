/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {completedTask} from 'modules/mock-schema/mocks/task-details';
import {Task, Variable} from 'modules/types';

type Variables = ReadonlyArray<Variable>;

interface CompleteTask {
  id: Task['id'];
  variables: Variables;
}

interface CompleteTaskVariables {
  id: Task['id'];
  variables: Variables;
}

const COMPLETE_TASK =
  process.env.NODE_ENV === 'test'
    ? gql`
        mutation CompleteTask($id: ID!, $variables: [Variable]) {
          completeTask(id: $id, variables: $variables) {
            id
            name
            workflowName
            assignee
            creationTime
            assignee
            variables
            completionTime
            taskState
          }
        }
      `
    : gql`
        mutation CompleteTask($id: ID!, $variables: [Variable]) {
          completeTask(id: $id, variables: $variables) @client {
            id
            name
            workflowName
            assignee
            creationTime
            assignee
            variables
            completionTime
            taskState
          }
        }
      `;

const mockCompleteTask = {
  request: {
    query: COMPLETE_TASK,
    variables: {id: '0', variables: []},
  },
  result: {
    data: {
      completeTask: completedTask,
    },
  },
};

const mockCompleteTaskWithVariable = {
  request: {
    query: COMPLETE_TASK,
    variables: {id: '0', variables: [{name: 'myVar', value: 'newValue'}]},
  },
  result: {
    data: {
      completeTask: completedTask,
    },
  },
};

export type {CompleteTask, CompleteTaskVariables};
export {COMPLETE_TASK, mockCompleteTask, mockCompleteTaskWithVariable};
