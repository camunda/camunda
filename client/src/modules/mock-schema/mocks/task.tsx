/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';
import {currentUser} from 'modules/mock-schema/mocks/current-user';

const unclaimedTask = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: null,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  variables: [],
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  processDefinitionId: null,
});

const unclaimedTaskWithForm = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: null,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  variables: [],
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
});

const unclaimedTaskWithVariables = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
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
  formKey: null,
  processDefinitionId: null,
});

const unclaimedTaskWithPrefilledForm = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
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
  formKey: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
});

const completedTask = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser,
  creationTime: new Date('2019').toISOString(),
  completionTime: new Date('2020').toISOString(),
  variables: [],
  taskState: TaskStates.Completed,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  processDefinitionId: null,
});

const completedTaskWithVariables = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
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
  formKey: null,
  processDefinitionId: null,
});

const completedTaskWithEditedVariables = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
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
  formKey: null,
  processDefinitionId: null,
});

const completedTaskWithForm = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  variables: [
    {name: 'myVar', value: '"0001"'},
    {name: 'isCool', value: '"yes"'},
  ],
  taskState: TaskStates.Completed,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
});

const claimedTask = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  variables: [],
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  processDefinitionId: null,
});

const claimedTaskWithForm = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser,
  creationTime: new Date('2019').toISOString(),
  completionTime: null,
  variables: [],
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
});

const claimedTaskWithVariables = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
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
  formKey: null,
  processDefinitionId: null,
});

const claimedTaskWithPrefilledForm = (id = '0'): Task => ({
  __typename: 'Task',
  id,
  name: 'My Task',
  processName: 'Nice Process',
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
  formKey: 'camunda-forms:bpmn:form-0',
  processDefinitionId: 'process',
});

export {
  unclaimedTask,
  completedTask,
  claimedTask,
  unclaimedTaskWithVariables,
  completedTaskWithVariables,
  claimedTaskWithVariables,
  completedTaskWithEditedVariables,
  unclaimedTaskWithForm,
  unclaimedTaskWithPrefilledForm,
  claimedTaskWithForm,
  claimedTaskWithPrefilledForm,
  completedTaskWithForm,
};
