/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {UserTask} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {currentUser} from 'modules/mock-schema/mocks/current-user';
import {DEFAULT_TENANT_ID} from 'modules/constants/multiTenancy';

function* getUniqueId(): Generator<number> {
  let id = 0;

  while (true) {
    yield id++;
  }
}

const unassignedTask = (customFields: Partial<UserTask> = {}): UserTask => ({
  userTaskKey: getUniqueId().next().value,
  creationDate: '2024-01-01T00:00:00.000Z',
  priority: 50,
  state: 'CREATED',
  candidateGroups: ['accounting candidate'],
  candidateUsers: ['jane candidate'],
  tenantId: DEFAULT_TENANT_ID,
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

const assignedTask = (customFields: Partial<UserTask> = {}): UserTask => ({
  userTaskKey: getUniqueId().next().value,
  assignee: currentUser.userId,
  creationDate: '2024-01-01T00:00:00.000Z',
  priority: 50,
  state: 'CREATED',
  candidateGroups: [],
  candidateUsers: [],
  tenantId: DEFAULT_TENANT_ID,
  processDefinitionId: 'process-1',
  processDefinitionVersion: 1,
  processName: 'Nice Process',
  elementId: 'element-1',
  elementName: 'My Task',
  elementInstanceKey: 1,
  processDefinitionKey: 0,
  processInstanceKey: 0,
  formKey: 1,
  ...customFields,
});

export {unassignedTask, assignedTask};
