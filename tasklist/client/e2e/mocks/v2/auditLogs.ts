/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {uniqueId} from '@/mocks/uniqueId';

type AuditLogItem = {
  auditLogKey: string;
  entityKey: string;
  entityType: string;
  operationType: string;
  timestamp: string;
  actorId: string;
  actorType: string;
  result: 'SUCCESS' | 'FAIL';
  category: string;
  relatedEntityKey?: string;
};

type QueryUserTaskAuditLogsResponse = {
  items: AuditLogItem[];
  page: {
    totalItems: number;
  };
};

const auditLog = (customFields: Partial<AuditLogItem> = {}): AuditLogItem => ({
  auditLogKey: String(uniqueId.next().value),
  entityKey: '456',
  entityType: 'USER_TASK',
  operationType: 'CREATE',
  timestamp: '2024-01-01T00:00:00.000Z',
  actorId: 'demo',
  actorType: 'USER',
  result: 'SUCCESS',
  category: 'USER_TASKS',
  ...customFields,
});

const auditLogs: AuditLogItem[] = [
  auditLog({
    operationType: 'CREATE',
    timestamp: '2024-01-01T00:00:00.000Z',
    result: 'SUCCESS',
  }),
  auditLog({
    operationType: 'ASSIGN',
    timestamp: '2024-01-01T01:00:00.000Z',
    actorId: 'jane',
    result: 'SUCCESS',
    relatedEntityKey: 'demo',
  }),
  auditLog({
    operationType: 'COMPLETE',
    timestamp: '2024-01-01T02:00:00.000Z',
    result: 'FAIL',
  }),
];

function getQueryUserTaskAuditLogsResponseMock(
  items: AuditLogItem[] = auditLogs,
  totalItems: number = items.length,
): QueryUserTaskAuditLogsResponse {
  return {
    items,
    page: {
      totalItems,
    },
  };
}

export {
  auditLog,
  auditLogs,
  getQueryUserTaskAuditLogsResponseMock,
  type AuditLogItem,
  type QueryUserTaskAuditLogsResponse,
};
