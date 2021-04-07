/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {Task, Variable} from 'modules/types';
import {
  completedTask,
  completedTaskWithEditedVariables,
} from 'modules/mock-schema/mocks/task';

type Variables = ReadonlyArray<Variable>;

interface CompleteTask {
  id: Task['id'];
  variables: Variables;
}

interface CompleteTaskVariables {
  id: Task['id'];
  variables: Variables;
}

const COMPLETE_TASK = gql`
  mutation CompleteTask($id: String!, $variables: [VariableInput!]!) {
    completeTask(taskId: $id, variables: $variables) {
      id
      taskState
      variables {
        name
        value
      }
      completionTime
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
      completeTask: completedTask(),
    },
  },
};

const mockCompleteTaskWithEditedVariable = {
  request: {
    query: COMPLETE_TASK,
    variables: {id: '0', variables: [{name: 'myVar', value: '"newValue"'}]},
  },
  result: {
    data: {
      completeTask: completedTaskWithEditedVariables(),
    },
  },
};

const mockCompleteTaskWithAddedVariable = {
  request: {
    query: COMPLETE_TASK,
    variables: {
      id: '0',
      variables: [{name: 'newVariableName', value: '"newVariableValue"'}],
    },
  },
  result: {
    data: {
      completeTask: completedTask(),
    },
  },
};

export type {CompleteTask, CompleteTaskVariables};
export {
  COMPLETE_TASK,
  mockCompleteTask,
  mockCompleteTaskWithEditedVariable,
  mockCompleteTaskWithAddedVariable,
};
