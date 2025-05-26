/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {uniqueId} from '@/mocks/uniqueId';

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

const nonFormTask = (customFields: Partial<Task> = {}): Task => ({
  id: uniqueId.next().value.toString(),
  formKey: null,
  formId: null,
  formVersion: null,
  isFormEmbedded: null,
  processDefinitionKey: '2251799813685259',
  taskDefinitionId: 'Activity_1ygafd4',
  processInstanceKey: '4503599627371080',
  assignee: null,
  name: 'Register the passenger',
  taskState: 'CREATED',
  processName: 'Flight registration',
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
  ...customFields,
});

const formTask = (customFields: Partial<Task> = {}): Task => ({
  id: uniqueId.next().value.toString(),
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
  ...customFields,
});

export {nonFormTask, formTask};
export type {Task};
