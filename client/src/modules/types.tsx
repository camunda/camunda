/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type User = Readonly<{
  username: string;
  firstname: string;
  lastname: string;
}>;

type Variable = Readonly<{
  name: string;
  value: string;
}>;

type TaskState = 'CREATED' | 'COMPLETED';

type Task = Readonly<{
  key: string;
  name: string;
  worflowName: string;
  creationTime: string;
  completionTime: string;
  assignee: User;
  variables: ReadonlyArray<Variable>;
  taskState: TaskState;
}>;

export type {User, Variable, Task};
