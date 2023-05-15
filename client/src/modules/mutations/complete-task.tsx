/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import gql from 'graphql-tag';
import {GraphqlTask, Variable} from 'modules/types';
import {completedTask} from 'modules/mock-schema/mocks/task';
import {convertToGraphqlTask} from 'modules/utils/convertToGraphqlTask';

type Variables = Pick<Variable, 'name' | 'value'>[];

interface CompleteTask {
  id: GraphqlTask['id'];
  variables: Variables;
}

interface CompleteTaskVariables {
  id: GraphqlTask['id'];
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

const mockCompleteTask = () => ({
  request: {
    query: COMPLETE_TASK,
    variables: {
      id: '0',
      variables: [],
    },
  },
  result: {
    data: {
      completeTask: {
        ...convertToGraphqlTask(completedTask()),
        variables: [],
      },
    },
  },
});

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
      completeTask: convertToGraphqlTask(completedTask()),
    },
  },
};

export type {CompleteTask, CompleteTaskVariables};
export {COMPLETE_TASK, mockCompleteTask, mockCompleteTaskWithAddedVariable};
