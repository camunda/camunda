/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

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

type TaskState = 'CREATED' | 'COMPLETED';

type Task = Readonly<{
  __typename: string;
  id: string;
  name: string;
  processName: string;
  creationTime: string;
  followUpDate: string | null;
  dueDate: string | null;
  completionTime: string | null;
  assignee: string | null;
  variables: ReadonlyArray<Variable>;
  taskState: TaskState;
  sortValues: [string, string];
  isFirst: boolean;
  formKey: string | null;
  processDefinitionId: string | null;
  candidateGroups: ReadonlyArray<string>;
  candidateUsers: ReadonlyArray<string>;
}>;

type Form = Readonly<{
  __typename: string;
  id: string;
  processDefinitionId: string;
  schema: string;
}>;

type Process = Readonly<{
  __typename: string;
  id: string;
  name: string | null;
  processDefinitionId: string;
}>;

type ProcessInstance = Readonly<{
  __typename: string;
  id: string;
}>;

export type {
  User,
  Variable,
  Task,
  TaskState,
  Form,
  Permissions,
  Process,
  ProcessInstance,
};
