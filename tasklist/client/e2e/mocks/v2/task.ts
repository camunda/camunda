/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {UserTask} from '@camunda/camunda-api-zod-schemas/8.9';
import {uniqueId} from '@/mocks/uniqueId';

const unassignedTask = (customFields: Partial<UserTask> = {}): UserTask => ({
  userTaskKey: uniqueId.next().value,
  name: 'Register the passenger',
  processName: 'Flight registration',
  creationDate: '2024-04-13T16:57:41.025+0000',
  completionDate: null,
  followUpDate: '2024-04-19T16:57:41.000Z',
  dueDate: '2024-04-18T16:57:41.000Z',
  state: 'CREATED',
  assignee: null,
  elementId: 'registerPassenger',
  priority: 50,
  elementInstanceKey: '4503599627371528',
  processDefinitionKey: '2251799813685251',
  processInstanceKey: '4503599627371064',
  rootProcessInstanceKey: null,
  processDefinitionId: 'twoUserTasks',
  processDefinitionVersion: 1,
  tenantId: '<default>',
  customHeaders: {},
  formKey: null,
  externalFormReference: null,
  candidateGroups: [],
  candidateUsers: [],
  tags: [],
  ...customFields,
});

export {unassignedTask};
