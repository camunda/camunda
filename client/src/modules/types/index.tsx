/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type User = Readonly<{
  username: string;
  firstname: string | null;
  lastname: string | null;
}>;

type Variable = Readonly<{
  name: string;
  value: string;
}>;

type TaskState = 'CREATED' | 'COMPLETED';

type Task = Readonly<{
  __typename: string;
  id: string;
  name: string;
  processName: string;
  creationTime: string;
  completionTime: string | null;
  assignee: User | null;
  variables: ReadonlyArray<Variable>;
  taskState: TaskState;
  sortValues: [string, string];
  isFirst: boolean;
  formKey: string | null;
  processDefinitionId: string | null;
}>;

type Form = Readonly<{
  __typename: string;
  id: string;
  processDefinitionId: string;
  schema: string;
}>;

export type {User, Variable, Task, TaskState, Form};
