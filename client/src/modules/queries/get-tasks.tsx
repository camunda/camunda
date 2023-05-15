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
  tasksAssignedToDemoUser,
  unassignedTasks,
  completedTasks,
} from 'modules/mock-schema/mocks/tasks';
import {GraphqlTask} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';

type QueryTask = Pick<
  GraphqlTask,
  | 'id'
  | 'name'
  | 'assignee'
  | 'processName'
  | 'creationTime'
  | 'taskState'
  | 'sortValues'
  | 'isFirst'
  | 'followUpDate'
  | 'dueDate'
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
  sort?: readonly [{field: string; order: string}];
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
    $processDefinitionKey: String
    $sort: [TaskOrderBy!]
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
        processDefinitionId: $processDefinitionKey
        sort: $sort
      }
    ) {
      id
      name
      processName
      assignee
      creationTime
      followUpDate
      dueDate
      taskState
      sortValues
      isFirst
    }
  }
`;

const mockGetAllOpenTasks: ReadonlyArray<QueryTask> = tasks;
const mockGetAllOpenTasksUnassigned: ReadonlyArray<QueryTask> = unassignedTasks;
const mockGetEmptyTasks: ReadonlyArray<QueryTask> = [];
const mockGetClaimedByMe: ReadonlyArray<QueryTask> = tasksAssignedToDemoUser;
const mockGetUnassigned: ReadonlyArray<QueryTask> = unassignedTasks;
const mockGetCompleted: ReadonlyArray<QueryTask> = completedTasks;

export type {GetTasks, GetTasksVariables, QueryTask};
export {
  GET_TASKS,
  mockGetAllOpenTasks,
  mockGetEmptyTasks,
  mockGetClaimedByMe,
  mockGetUnassigned,
  mockGetCompleted,
  mockGetAllOpenTasksUnassigned,
};
