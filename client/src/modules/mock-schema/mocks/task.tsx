/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Task} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';
import {currentUser} from 'modules/mock-schema/mocks/current-user';

const unassignedTask = (id = '0'): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: null,
  creationTime: '2019-01-01T00:00:00.000Z',
  completionTime: null,
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: ['accounting candidate'],
  candidateUsers: ['jane candidate'],
});

const unassignedTaskWithForm = (id = '0'): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: null,
  creationTime: '2019-01-01T00:00:00.000Z',
  completionTime: null,
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
});

const completedTask = (id = '0'): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationTime: '2019-01-01T00:00:00.000Z',
  completionTime: '2020-01-01T00:00:00.000Z',
  taskState: TaskStates.Completed,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
});

const completedTaskWithForm = (id = '0'): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationTime: '2019-01-01T00:00:00.000Z',
  completionTime: null,
  taskState: TaskStates.Completed,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
});

const assignedTask = (id = '0'): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationTime: '2019-01-01T00:00:00.000Z',
  completionTime: null,
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
});

const assignedTaskWithForm = (id = '0'): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationTime: '2019-01-01T00:00:00.000Z',
  completionTime: null,
  taskState: TaskStates.Created,
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
});

export {
  unassignedTask,
  completedTask,
  assignedTask,
  unassignedTaskWithForm,
  assignedTaskWithForm,
  completedTaskWithForm,
};
