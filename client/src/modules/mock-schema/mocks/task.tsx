/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';
import {currentUser} from 'modules/mock-schema/mocks/current-user';

const unclaimedTask: Task = {
  __typename: 'Task',
  id: '0',
  name: 'My Task',
  workflowName: 'Nice Workflow',
  assignee: null,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  variables: [],
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
};

const unclaimedTaskWithVariables: Task = {
  __typename: 'Task',
  id: '0',
  name: 'My Task',
  workflowName: 'Nice Workflow',
  assignee: null,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  variables: [
    {name: 'myVar', value: '"0001"'},
    {name: 'isCool', value: '"yes"'},
  ],
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
};

const completedTask: Task = {
  __typename: 'Task',
  id: '0',
  name: 'My Task',
  workflowName: 'Nice Workflow',
  assignee: currentUser,
  creationTime: new Date('2019').toISOString(),
  completionTime: new Date('2020').toISOString(),
  variables: [],
  taskState: TaskStates.Completed,
  isFirst: false,
  sortValues: ['1', '2'],
};

const completedTaskWithVariables: Task = {
  __typename: 'Task',
  id: '0',
  name: 'My Task',
  workflowName: 'Nice Workflow',
  assignee: currentUser,
  creationTime: new Date('2019').toISOString(),
  completionTime: new Date('2020').toISOString(),
  variables: [
    {name: 'myVar', value: '"0001"'},
    {name: 'isCool', value: '"yes"'},
  ],
  taskState: TaskStates.Completed,
  isFirst: false,
  sortValues: ['1', '2'],
};

const completedTaskWithEditedVariables: Task = {
  __typename: 'Task',
  id: '0',
  name: 'My Task',
  workflowName: 'Nice Workflow',
  assignee: currentUser,
  creationTime: new Date('2019').toISOString(),
  completionTime: new Date('2020').toISOString(),
  variables: [
    {name: 'myVar', value: '"newValue"'},
    {name: 'isCool', value: '"yes"'},
  ],
  taskState: TaskStates.Completed,
  isFirst: false,
  sortValues: ['1', '2'],
};

const claimedTask: Task = {
  __typename: 'Task',
  id: '0',
  name: 'My Task',
  workflowName: 'Nice Workflow',
  assignee: currentUser,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  variables: [],
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
};

const claimedTaskWithVariables: Task = {
  __typename: 'Task',
  id: '0',
  name: 'My Task',
  workflowName: 'Nice Workflow',
  assignee: currentUser,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  variables: [
    {name: 'myVar', value: '"0001"'},
    {name: 'isCool', value: '"yes"'},
  ],
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
};

export {
  unclaimedTask,
  completedTask,
  claimedTask,
  unclaimedTaskWithVariables,
  completedTaskWithVariables,
  claimedTaskWithVariables,
  completedTaskWithEditedVariables,
};
