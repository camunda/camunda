/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

type ToGraphqlEntity<T, K extends string> = T & {
  __typename: K;
  variables: Variable[];
};

type NonEmptyArray<T> = [T, ...T[]];

type Permissions = NonEmptyArray<'read' | 'write'>;

type User = Readonly<{
  userId: string;
  displayName: string | null;
  permissions: Permissions;
  roles: ReadonlyArray<string> | null;
  salesPlanType: string | null;
  c8Links: ReadonlyArray<{
    name: 'console' | 'modeler' | 'tasklist' | 'operate' | 'optimize';
    link: string;
  }>;
  __typename: string;
}>;

type Variable = Readonly<{
  id: string;
  name: string;
  value: string;
  previewValue: string;
  isValueTruncated: boolean;
}>;

type TaskState = 'CREATED' | 'COMPLETED' | 'CANCELED';

type Task = {
  id: string;
  name: string;
  taskDefinitionId: string;
  processName: string;
  creationTime: string;
  followUpDate: string | null;
  dueDate: string | null;
  completionTime: string | null;
  assignee: string | null;
  taskState: TaskState;
  sortValues: [string, string];
  isFirst: boolean;
  formKey: string | null;
  processInstanceKey: string;
  processDefinitionKey: string;
  candidateGroups: string[];
  candidateUsers: string[];
};

type GraphqlTask = ToGraphqlEntity<
  Omit<Task, 'processDefinitionKey'> & {
    processDefinitionId: string;
  },
  'Task'
>;

type Form = Readonly<{
  __typename: string;
  id: string;
  processDefinitionId: string;
  schema: string;
}>;

type Process = {
  id: string;
  name: string | null;
  processDefinitionKey: string;
  sortValues: [string];
  version: number;
};

type ProcessInstance = {
  __typename: string;
  id: string;
};

type DateSearch = {
  from: string;
  to: string;
};

type PaginationSearchPair = [string, string];

type TasksSearchBody = {
  state?: TaskState;
  followUpDate?: DateSearch;
  dueDate?: DateSearch;
  assigned?: boolean;
  assignee?: string;
  taskDefinitionId?: string;
  candidateGroup?: string;
  candidateUser?: string;
  processDefinitionKey?: string;
  processInstanceKey?: string;
  pageSize?: number;
  sort?: Array<{
    field: 'completionTime' | 'creationTime' | 'followUpDate' | 'dueDate';
    order: 'ASC' | 'DESC';
  }>;
  searchAfter?: PaginationSearchPair;
  searchAfterOrEqual?: PaginationSearchPair;
  searchBefore?: PaginationSearchPair;
  searchBeforeOrEqual?: PaginationSearchPair;
};

export type {
  User,
  Variable,
  Task,
  TaskState,
  Form,
  Permissions,
  Process,
  ProcessInstance,
  TasksSearchBody,
  GraphqlTask,
};
