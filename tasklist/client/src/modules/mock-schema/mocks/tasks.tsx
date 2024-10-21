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
} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {currentUser} from './current-user';
import {assignedTask} from './task';

const tasks: UserTask[] = [
  assignedTask({userTaskKey: 0}),
  assignedTask({userTaskKey: 1, assignee: 'mustermann'}),
  assignedTask({userTaskKey: 2}),
];

const tasksAssignedToDemoUser: UserTask[] = tasks.map((task) => ({
  ...task,
  assignee: currentUser.userId,
}));

const unassignedTasks: UserTask[] = tasks.map((task) => ({
  ...task,
}));

const completedTasks: UserTask[] = tasks.map((task) => ({
  ...task,
  assignee: task.assignee === null ? currentUser.userId : task.assignee,
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
      firstSortValues: [0, 1],
      lastSortValues: [2, 3],
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
