/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {gql} from '@apollo/client';
import {TaskStates} from 'modules/constants/taskStates';
import {Task} from 'modules/types';

interface GetNewTasks {
  tasks: ReadonlyArray<Pick<Task, 'id' | 'name' | 'processName'>>;
}

interface GetNewTasksVariables {
  state: typeof TaskStates.Created;
  processInstanceId: string;
  pageSize: number;
}

const GET_NEW_TASKS = gql`
  query GetNewTasks(
    $state: TaskState
    $pageSize: Int
    $processInstanceId: String
    $processDefinitionId: String
  ) {
    tasks(
      query: {
        state: $state
        pageSize: $pageSize
        processInstanceId: $processInstanceId
        processDefinitionId: $processDefinitionId
      }
    ) {
      id
      name
      processName
    }
  }
`;

export type {GetNewTasks, GetNewTasksVariables};
export {GET_NEW_TASKS};
