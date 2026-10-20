/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10';
import {mapToCellEntityKeyData} from '.';

const userTaskItem = {
  auditLogKey: '123',
  entityKey: '123',
  operationType: 'UPDATE',
  entityType: 'USER_TASK',
  result: 'SUCCESS',
  actorId: 'user1',
  timestamp: '2024-01-01T00:00:00.000Z',
  actorType: 'USER',
  category: 'USER_TASKS',
  entityDescription: null,
  batchOperationKey: null,
  batchOperationType: null,
  relatedEntityType: null,
  resourceKey: null,
  jobKey: null,
  elementInstanceKey: null,
  tenantId: '<default>',
  decisionRequirementsId: null,
  decisionEvaluationKey: null,
  deploymentKey: null,
  decisionRequirementsKey: null,
  processDefinitionId: null,
  relatedEntityKey: null,
  processDefinitionKey: null,
  processInstanceKey: null,
  rootProcessInstanceKey: null,
  decisionDefinitionId: null,
  decisionDefinitionKey: null,
  userTaskKey: '123',
  formKey: null,
  agentElementId: null,
  inboundChannelType: null,
  inboundChannelToolName: null,
} satisfies AuditLog;

describe('mapToCellEntityKeyData', () => {
  it('should resolve a root-relative tasklist URL to an absolute link', () => {
    const {link} = mapToCellEntityKeyData(
      userTaskItem,
      null,
      null,
      '/camunda/tasklist',
    );

    expect(link).toBe(`${window.location.origin}/camunda/tasklist/123`);
  });

  it('should keep an absolute tasklist URL', () => {
    const {link} = mapToCellEntityKeyData(
      userTaskItem,
      null,
      null,
      'https://tasklist.example.com',
    );

    expect(link).toBe('https://tasklist.example.com/123');
  });

  it('should not create a link without a tasklist URL', () => {
    const {link} = mapToCellEntityKeyData(userTaskItem);

    expect(link).toBeUndefined();
  });
});
