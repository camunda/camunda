/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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

type QueryTask = Pick<
  Task,
  | 'id'
  | 'name'
  | 'assignee'
  | 'processName'
  | 'creationTime'
  | 'taskState'
  | 'sortValues'
  | 'isFirst'
>;

interface GetTasks {
  tasks: ReadonlyArray<QueryTask>;
}

type GetTasksVariables = {
  searchBefore?: string[];
  searchAfter?: string[];
  searchAfterOrEqual?: readonly string[];
  pageSize?: number;
  assigned?: boolean;
  assignee?: string;
  state?: (typeof TaskStates)[keyof typeof TaskStates];
  isPolling?: boolean;
};

const GET_TASKS = gql`
  query GetTasks(
    $assignee: String
    $assigned: Boolean
    $state: TaskState
    $pageSize: Int
    $searchAfter: [String!]
    $searchBefore: [String!]
    $searchAfterOrEqual: [String!]
    $processInstanceId: String
    $processDefinitionId: String
  ) {
    tasks(
      query: {
        assignee: $assignee
        assigned: $assigned
        state: $state
        pageSize: $pageSize
        searchAfter: $searchAfter
        searchBefore: $searchBefore
        searchAfterOrEqual: $searchAfterOrEqual
        processInstanceId: $processInstanceId
        processDefinitionId: $processDefinitionId
      }
    ) {
      id
      name
      processName
      assignee
      creationTime
      taskState
      sortValues
      isFirst
    }
  }
`;

const mockGetAllOpenTasks: ReadonlyArray<QueryTask> = tasks;
const mockGetAllOpenTasksUnclaimed: ReadonlyArray<QueryTask> = unclaimedTasks;
const mockGetEmptyTasks: ReadonlyArray<QueryTask> = [];
const mockGetClaimedByMe: ReadonlyArray<QueryTask> = tasksClaimedByDemoUser;
const mockGetUnclaimed: ReadonlyArray<QueryTask> = unclaimedTasks;
const mockGetCompleted: ReadonlyArray<QueryTask> = completedTasks;

export type {GetTasks, GetTasksVariables, QueryTask};
export {
  GET_TASKS,
  mockGetAllOpenTasks,
  mockGetEmptyTasks,
  mockGetClaimedByMe,
  mockGetUnclaimed,
  mockGetCompleted,
  mockGetAllOpenTasksUnclaimed,
};
