/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryUserTasksResponseBody,
  UserTask,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {currentUser} from 'common/mocks/current-user';
import {assignedTask} from './task';

const tasks: UserTask[] = [
  assignedTask({userTaskKey: '0', creationDate: '2024-01-01T00:00:00.000Z'}),
  assignedTask({
    userTaskKey: '1',
    assignee: 'mustermann',
    creationDate: '2024-01-01T01:00:00.000Z',
  }),
  assignedTask({userTaskKey: '2', creationDate: '2024-01-01T02:00:00.000Z'}),
];

const tasksAssignedToDemoUser: UserTask[] = tasks.map((task) => ({
  ...task,
  assignee: currentUser.username,
}));

const unassignedTasks: UserTask[] = tasks.map((task) => ({
  ...task,
}));

const completedTasks: UserTask[] = tasks.map((task) => ({
  ...task,
  assignee: task.assignee === null ? currentUser.username : task.assignee,
  taskState: 'COMPLETED',
}));

function getQueryTasksResponseMock(
  tasks: UserTask[],
  totalItems: number = tasks.length,
): QueryUserTasksResponseBody {
  return {
    items: tasks,
    page: {
      totalItems,
      startCursor: 'startCursor',
      endCursor: 'endCursor',
    },
  };
}

export {
  tasks,
  tasksAssignedToDemoUser,
  unassignedTasks,
  completedTasks,
  getQueryTasksResponseMock,
};
