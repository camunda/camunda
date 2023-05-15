/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {GraphqlTask} from 'modules/types';
import {TaskStates} from 'modules/constants/taskStates';
import {currentUser} from './current-user';

const tasks: ReadonlyArray<GraphqlTask> = [
  {
    __typename: 'Task',
    id: '0',
    name: 'name',
    processName: 'processName',
    creationTime: '2020-05-28 10:11:12',
    completionTime: new Date().toISOString(),
    assignee: currentUser.userId,
    variables: [],
    taskState: TaskStates.Created,
    sortValues: ['0', '1'],
    isFirst: true,
    formKey: null,
    processDefinitionId: 'process',
    taskDefinitionId: 'task-0',
    processInstanceKey: '123',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
  },
  {
    __typename: 'Task',
    id: '1',
    name: 'name',
    processName: 'processName',
    creationTime: '2020-05-29 13:14:15',
    completionTime: new Date().toISOString(),
    assignee: 'mustermann',
    variables: [
      {
        id: '0-myVar',
        name: 'myVar',
        value: '"0001"',
        previewValue: '"0001"',
        isValueTruncated: false,
      },
      {
        id: '0-isCool',
        name: 'isCool',
        value: '"yes"',
        previewValue: '"yes"',
        isValueTruncated: false,
      },
    ],
    taskState: TaskStates.Created,
    sortValues: ['1', '2'],
    isFirst: false,
    formKey: null,
    processDefinitionId: 'process',
    taskDefinitionId: 'task-0',
    processInstanceKey: '123',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
  },
  {
    __typename: 'Task',
    id: '2',
    name: 'name',
    processName: 'processName',
    creationTime: '2020-05-30 16:17:18',
    completionTime: new Date().toISOString(),
    assignee: null,
    variables: [],
    taskState: TaskStates.Created,
    sortValues: ['2', '3'],
    isFirst: false,
    formKey: null,
    processDefinitionId: 'process',
    taskDefinitionId: 'task-0',
    processInstanceKey: '123',
    followUpDate: null,
    dueDate: null,
    candidateGroups: [],
    candidateUsers: [],
  },
];

const tasksAssignedToDemoUser: ReadonlyArray<GraphqlTask> = tasks.map(
  (task) => ({
    ...task,
    assignee: currentUser.userId,
  }),
);

const unassignedTasks: ReadonlyArray<GraphqlTask> = tasks.map((task) => ({
  ...task,
  assignee: null,
}));

const completedTasks: ReadonlyArray<GraphqlTask> = tasks.map((task) => ({
  ...task,
  assignee: task.assignee === null ? currentUser.userId : task.assignee,
  taskState: TaskStates.Completed,
}));

const generateTask = (id: string, name?: string): GraphqlTask => {
  return {
    id,
    __typename: 'Task',
    name: name ?? `TASK ${id}`,
    processName: 'Flight registration',
    assignee: 'demo',
    creationTime: '2021-01-13T12:13:18.655Z',
    taskState: 'CREATED',
    sortValues: [id, id],
    followUpDate: null,
    dueDate: null,
    isFirst: false,
    processDefinitionId: 'process',
    taskDefinitionId: 'task-0',
    processInstanceKey: '123',
    completionTime: null,
    formKey: null,
    variables: [],
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
