/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Task} from 'modules/types';
import {currentUser} from 'modules/mock-schema/mocks/current-user';
import {DEFAULT_TENANT_ID} from 'modules/constants/multiTenancy';

function* getUniqueId(): Generator<number> {
  let id = 0;

  while (true) {
    yield id++;
  }
}

const unassignedTask = (
  id: string = getUniqueId().next().value.toString(),
): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: null,
  creationDate: '2019-01-01T00:00:00.000Z',
  completionDate: null,
  taskState: 'CREATED',
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  formVersion: null,
  formId: null,
  isFormEmbedded: null,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: ['accounting candidate'],
  candidateUsers: ['jane candidate'],
  tenantId: DEFAULT_TENANT_ID,
});

const unassignedTaskWithForm = (
  id: string = getUniqueId().next().value.toString(),
): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: null,
  creationDate: '2019-01-01T00:00:00.000Z',
  completionDate: null,
  taskState: 'CREATED',
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  formVersion: null,
  formId: null,
  isFormEmbedded: true,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
  tenantId: DEFAULT_TENANT_ID,
});

const unassignedTaskWithFormDeployed = (
  id: string = getUniqueId().next().value.toString(),
): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: null,
  creationDate: '2019-01-01T00:00:00.000Z',
  completionDate: null,
  taskState: 'CREATED',
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: '234234432',
  formVersion: 2,
  formId: 'form-deployed-id',
  isFormEmbedded: false,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
  tenantId: DEFAULT_TENANT_ID,
});

const completedTask = (
  id: string = getUniqueId().next().value.toString(),
): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationDate: '2019-01-01T00:00:00.000Z',
  completionDate: '2020-01-01T00:00:00.000Z',
  taskState: 'COMPLETED',
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  formVersion: null,
  formId: null,
  isFormEmbedded: null,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
  tenantId: DEFAULT_TENANT_ID,
});

const completedTaskWithForm = (
  id: string = getUniqueId().next().value.toString(),
): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationDate: '2019-01-01T00:00:00.000Z',
  completionDate: null,
  taskState: 'COMPLETED',
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  formId: null,
  formVersion: null,
  isFormEmbedded: true,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
  tenantId: DEFAULT_TENANT_ID,
});

const completedTaskWithFormDeployed = (
  id: string = getUniqueId().next().value.toString(),
): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationDate: '2019-01-01T00:00:00.000Z',
  completionDate: null,
  taskState: 'COMPLETED',
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: '234324324',
  formId: 'form-deployed-id',
  formVersion: 1,
  isFormEmbedded: false,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
  tenantId: DEFAULT_TENANT_ID,
});

const assignedTask = (
  id: string = getUniqueId().next().value.toString(),
): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationDate: '2019-01-01T00:00:00.000Z',
  completionDate: null,
  taskState: 'CREATED',
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: null,
  formId: null,
  formVersion: null,
  isFormEmbedded: null,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
  tenantId: DEFAULT_TENANT_ID,
});

const assignedTaskWithForm = (
  id: string = getUniqueId().next().value.toString(),
): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationDate: '2019-01-01T00:00:00.000Z',
  completionDate: null,
  taskState: 'CREATED',
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: 'camunda-forms:bpmn:form-0',
  formId: null,
  formVersion: null,
  isFormEmbedded: true,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
  tenantId: DEFAULT_TENANT_ID,
});

const assignedTaskWithFormDeployed = (
  id: string = getUniqueId().next().value.toString(),
): Task => ({
  id,
  name: 'My Task',
  processName: 'Nice Process',
  assignee: currentUser.userId,
  creationDate: '2019-01-01T00:00:00.000Z',
  completionDate: null,
  taskState: 'CREATED',
  isFirst: false,
  sortValues: ['1', '2'],
  formKey: '324234234342',
  formId: 'form-deployed-id',
  formVersion: 1,
  isFormEmbedded: false,
  taskDefinitionId: 'task-0',
  processDefinitionKey: 'process',
  processInstanceKey: '123',
  followUpDate: null,
  dueDate: null,
  candidateGroups: [],
  candidateUsers: [],
  tenantId: DEFAULT_TENANT_ID,
});

export {
  unassignedTask,
  completedTask,
  assignedTask,
  unassignedTaskWithForm,
  unassignedTaskWithFormDeployed,
  assignedTaskWithForm,
  assignedTaskWithFormDeployed,
  completedTaskWithForm,
  completedTaskWithFormDeployed,
};
