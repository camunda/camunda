/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Task} from 'modules/types';
import {currentUser} from './current-user';

const tasks: Task[] = [
  {
    id: '0',
    name: 'name',
    processName: 'processName',
    creationDate: '2020-05-28 10:11:12',
    completionDate: new Date().toISOString(),
    assignee: currentUser.userId,
    taskState: 'CREATED',
    sortValues: ['0', '1'],
    isFirst: true,
    formKey: null,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
  },
  {
    id: '1',
    name: 'name',
    processName: 'processName',
    creationDate: '2020-05-29 13:14:15',
    completionDate: new Date().toISOString(),
    assignee: 'mustermann',
    taskState: 'CREATED',
    sortValues: ['1', '2'],
    isFirst: false,
    formKey: null,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
  },
  {
    id: '2',
    name: 'name',
    processName: 'processName',
    creationDate: '2020-05-30 16:17:18',
    completionDate: new Date().toISOString(),
    assignee: null,
    taskState: 'CREATED',
    sortValues: ['2', '3'],
    isFirst: false,
    formKey: null,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
  },
];

const tasksAssignedToDemoUser: Task[] = tasks.map((task) => ({
  ...task,
  assignee: currentUser.userId,
}));

const unassignedTasks: Task[] = tasks.map((task) => ({
  ...task,
  assignee: null,
}));

const completedTasks: Task[] = tasks.map((task) => ({
  ...task,
  assignee: task.assignee === null ? currentUser.userId : task.assignee,
  taskState: 'COMPLETED',
}));

const generateTask = (id: string, name?: string): Task => {
  return {
    id,
    name: name ?? `TASK ${id}`,
    processName: 'Flight registration',
    assignee: 'demo',
    creationDate: '2021-01-13T12:13:18.655Z',
    taskState: 'CREATED',
    sortValues: [id, id],
    followUpDate: null,
    dueDate: null,
    isFirst: false,
    processDefinitionKey: 'process-definition-id',
    taskDefinitionId: 'task-definition-id',
    processInstanceKey: 'process-instance-key',
    completionDate: null,
    formKey: null,
    candidateGroups: [],
    candidateUsers: [],
  };
};

export {
  tasks,
  tasksAssignedToDemoUser,
  unassignedTasks,
  completedTasks,
  generateTask,
};
