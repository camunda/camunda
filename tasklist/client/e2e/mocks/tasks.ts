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

function* getUniqueId(): Generator<number> {
  let id = 0;

  while (true) {
    yield id++;
  }
}

const getTask = (customFields: Partial<UserTask> = {}): UserTask => ({
  userTaskKey: getUniqueId().next().value,
  creationDate: '2024-01-01T00:00:00.000Z',
  priority: 50,
  state: 'CREATED',
  candidateGroups: ['accounting candidate'],
  candidateUsers: ['jane candidate'],
  tenantId: '<default>',
  processDefinitionId: 'process-1',
  processDefinitionVersion: 1,
  processName: 'Nice Process',
  elementName: 'My Task',
  elementId: 'element-1',
  elementInstanceKey: 1,
  formKey: 1,
  processInstanceKey: 0,
  processDefinitionKey: 0,
  ...customFields,
});

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

type Task = {
  id: string;
  name: string;
  taskDefinitionId: string;
  processName: string;
  creationDate: string;
  followUpDate: string | null;
  dueDate: string | null;
  completionDate: string | null;
  priority: number | null;
  assignee: string | null;
  taskState: string;
  sortValues: [string, string];
  isFirst: boolean;
  formKey: string | null;
  formVersion: number | null | undefined;
  formId: string | null;
  isFormEmbedded: boolean | null;
  processInstanceKey: string;
  processDefinitionKey: string;
  candidateGroups: string[] | null;
  candidateUsers: string[] | null;
  tenantId: string | '<default>' | null;
  context: string | null;
};

const taskWithoutForm: Task = {
  id: '2251799813687061',
  formKey: null,
  formId: null,
  formVersion: null,
  isFormEmbedded: null,
  processDefinitionKey: '2251799813685259',
  taskDefinitionId: 'Activity_1ygafd4',
  processInstanceKey: '4503599627371080',
  assignee: null,
  name: 'Activity_1ygafd4',
  taskState: 'CREATED',
  processName: 'TwoUserTasks',
  creationDate: '2023-04-13T16:57:41.482+0000',
  completionDate: null,
  priority: 50,
  candidateGroups: ['demo group'],
  candidateUsers: ['demo'],
  followUpDate: null,
  dueDate: null,
  sortValues: ['1684881752515', '4503599627371089'],
  isFirst: true,
  tenantId: null,
  context: null,
};

const taskWithForm: Task = {
  id: '2251799813687045',
  formKey: 'camunda-forms:bpmn:userTaskForm_1',
  formId: null,
  formVersion: null,
  isFormEmbedded: true,
  processDefinitionKey: '2251799813685255',
  assignee: 'demo',
  name: 'Big form task',
  taskState: 'CREATED',
  processName: 'Big form process',
  creationDate: '2023-03-03T14:16:18.441+0100',
  completionDate: null,
  priority: 50,
  taskDefinitionId: 'Activity_0aecztp',
  processInstanceKey: '4503599627371425',
  dueDate: null,
  followUpDate: null,
  candidateGroups: [],
  candidateUsers: [],
  sortValues: ['1684881752515', '4503599627371089'],
  isFirst: true,
  tenantId: null,
  context: null,
};

export {taskWithoutForm, taskWithForm, getQueryTasksResponseMock, getTask};
export type {Task};
