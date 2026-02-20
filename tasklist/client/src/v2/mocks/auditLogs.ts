/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryUserTaskAuditLogsResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';
import {uniqueId} from './utils';

type AuditLogItem = QueryUserTaskAuditLogsResponseBody['items'][number];

const auditLog = (customFields: Partial<AuditLogItem> = {}): AuditLogItem => ({
  auditLogKey: String(uniqueId.next().value),
  entityKey: '456',
  entityType: 'USER_TASK',
  operationType: 'CREATE',
  batchOperationKey: 'batchOperationKey0',
  batchOperationType: null,
  timestamp: '2024-01-01T00:00:00.000Z',
  actorId: 'demo',
  actorType: 'USER',
  tenantId: null,
  result: 'SUCCESS',
  annotation: null,
  category: 'USER_TASKS',
  processDefinitionId: null,
  processDefinitionKey: '',
  processInstanceKey: '',
  rootProcessInstanceKey: null,
  elementInstanceKey: '',
  jobKey: '',
  userTaskKey: '',
  decisionRequirementsId: null,
  decisionRequirementsKey: '',
  decisionDefinitionId: null,
  decisionDefinitionKey: '',
  decisionEvaluationKey: '',
  deploymentKey: '',
  formKey: '',
  resourceKey: '',
  relatedEntityKey: '',
  relatedEntityType: null,
  entityDescription: null,
  agentElementId: null,
  ...customFields,
});

const auditLogs: AuditLogItem[] = [
  auditLog({operationType: 'CREATE', timestamp: '2024-01-01T00:00:00.000Z'}),
  auditLog({operationType: 'ASSIGN', timestamp: '2024-01-01T01:00:00.000Z'}),
];

function getQueryUserTaskAuditLogsResponseMock(
  items: AuditLogItem[] = auditLogs,
  totalItems: number = items.length,
): QueryUserTaskAuditLogsResponseBody {
  return {
    items,
    page: {
      totalItems,
      hasMoreTotalItems: false,
      endCursor: null,
      startCursor: null,
    },
  };
}

function getAuditLogResponseMock(
  customFields: Partial<AuditLogItem> = {},
): AuditLogItem {
  return auditLog(customFields);
}

export {
  auditLog,
  auditLogs,
  getQueryUserTaskAuditLogsResponseMock,
  getAuditLogResponseMock,
};
