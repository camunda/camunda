/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';
import {currentUser} from './current-user';

const tasks: ReadonlyArray<Task> = [
  {
    __typename: 'Task',
    id: '0',
    name: 'name',
    workflowName: 'workflowName',
    creationTime: '2020-05-28 10:11:12',
    completionTime: new Date().toISOString(),
    assignee: currentUser,
    variables: [],
    taskState: TaskStates.Created,
  },
  {
    __typename: 'Task',
    id: '1',
    name: 'name',
    workflowName: 'workflowName',
    creationTime: '2020-05-29 13:14:15',
    completionTime: new Date().toISOString(),
    assignee: {
      username: 'mustermann',
      firstname: 'Otto',
      lastname: 'Mustermann',
    },
    variables: [
      {name: 'myVar', value: '"0001"'},
      {name: 'isCool', value: '"yes"'},
    ],
    taskState: TaskStates.Created,
  },
  {
    __typename: 'Task',
    id: '2',
    name: 'name',
    workflowName: 'workflowName',
    creationTime: '2020-05-30 16:17:18',
    completionTime: new Date().toISOString(),
    assignee: null,
    variables: [],
    taskState: TaskStates.Created,
  },
];

const tasksClaimedByDemoUser: ReadonlyArray<Task> = tasks.map((task) => ({
  ...task,
  assignee: currentUser,
}));

const unclaimedTasks: ReadonlyArray<Task> = tasks.map((task) => ({
  ...task,
  assignee: null,
}));

const completedTasks: ReadonlyArray<Task> = tasks.map((task) => ({
  ...task,
  assignee: task.assignee === null ? currentUser : task.assignee,
  taskState: TaskStates.Completed,
}));

export {tasks, tasksClaimedByDemoUser, unclaimedTasks, completedTasks};
