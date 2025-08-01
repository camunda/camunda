/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Task} from 'v1/api/types';
import {currentUser} from 'common/mocks/current-user';
import {DEFAULT_TENANT_ID} from 'common/multitenancy/constants';
import {formatRFC3339} from 'date-fns';

const tasks: Task[] = [
  {
    id: '0',
    name: 'name',
    processName: 'processName',
    creationDate: '2023-05-28 10:11:12',
    completionDate: formatRFC3339(new Date()),
    assignee: currentUser.username,
    priority: 50,
    taskState: 'CREATED',
    sortValues: ['0', '1'],
    isFirst: true,
    formKey: null,
    formId: null,
    formVersion: null,
    isFormEmbedded: null,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
    tenantId: DEFAULT_TENANT_ID,
    context: 'My Task',
  },
  {
    id: '1',
    name: 'name',
    processName: 'processName',
    creationDate: '2023-05-29 13:14:15',
    completionDate: formatRFC3339(new Date()),
    priority: 25,
    assignee: 'mustermann',
    taskState: 'CREATED',
    sortValues: ['1', '2'],
    isFirst: false,
    formKey: null,
    formId: null,
    formVersion: null,
    isFormEmbedded: null,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
    tenantId: DEFAULT_TENANT_ID,
    context: 'My Task',
  },
  {
    id: '2',
    name: 'name',
    processName: 'processName',
    creationDate: '2023-05-30 16:17:18',
    completionDate: formatRFC3339(new Date()),
    priority: 75,
    assignee: null,
    taskState: 'CREATED',
    sortValues: ['2', '3'],
    isFirst: false,
    formKey: null,
    formId: null,
    formVersion: null,
    isFormEmbedded: null,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
    tenantId: DEFAULT_TENANT_ID,
    context: 'My Task',
  },
];

const tasksAssignedToDemoUser: Task[] = tasks.map((task) => ({
  ...task,
  assignee: currentUser.username,
}));

const unassignedTasks: Task[] = tasks.map((task) => ({
  ...task,
  assignee: null,
}));

const completedTasks: Task[] = tasks.map((task) => ({
  ...task,
  assignee: task.assignee === null ? currentUser.username : task.assignee,
  taskState: 'COMPLETED',
}));

const generateTask = (id: string, name?: string): Task => {
  return {
    id,
    name: name ?? `TASK ${id}`,
    processName: 'Flight registration',
    assignee: 'demo',
    creationDate: '2024-01-13T12:13:18.655Z',
    taskState: 'CREATED',
    sortValues: [id, id],
    followUpDate: null,
    dueDate: null,
    isFirst: false,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    completionDate: null,
    priority: 50,
    formKey: null,
    formId: null,
    formVersion: null,
    isFormEmbedded: null,
    candidateGroups: [],
    candidateUsers: [],
    tenantId: DEFAULT_TENANT_ID,
    context: 'My Task',
  };
};

export {
  tasks,
  tasksAssignedToDemoUser,
  unassignedTasks,
  completedTasks,
  generateTask,
};
