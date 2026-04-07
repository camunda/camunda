/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AuditLogEntityType,
  AuditLogOperationType,
} from '@camunda/camunda-api-zod-schemas/8.9';

type OperationsLogFilterField =
  | 'processDefinitionId'
  | 'processDefinitionVersion'
  | 'processInstanceKey'
  | 'operationType'
  | 'entityType'
  | 'actorId'
  | 'result'
  | 'timestampBefore'
  | 'timestampAfter'
  | 'tenantId';

type OperationsLogFilters = {
  processDefinitionId?: string;
  processDefinitionVersion?: string;
  processInstanceKey?: string;
  operationType?: string;
  entityType?: string;
  actorId?: string;
  result?: string;
  timestampBefore?: string;
  timestampAfter?: string;
  tenantId?: string;
};

const AUDIT_LOG_FILTER_FIELDS: (keyof OperationsLogFilters)[] = [
  'processDefinitionId',
  'processDefinitionVersion',
  'processInstanceKey',
  'operationType',
  'entityType',
  'actorId',
  'result',
  'timestampBefore',
  'timestampAfter',
  'tenantId',
];

const AUDIT_LOG_ENTITY_TYPE_FILTER_VALUES: AuditLogEntityType[] = [
  'USER_TASK',
  'BATCH',
  'RESOURCE',
  'CLIENT',
  'DECISION',
  'INCIDENT',
  'JOB',
  'PROCESS_INSTANCE',
  'VARIABLE',
];

const AUDIT_LOG_OPERATION_TYPE_FILTER_VALUES: AuditLogOperationType[] = [
  'CREATE',
  'UPDATE',
  'DELETE',
  'COMPLETE',
  'EVALUATE',
  'ASSIGN',
  'CANCEL',
  'MIGRATE',
  'MODIFY',
  'RESOLVE',
  'RESUME',
  'SUSPEND',
  'UNASSIGN',
];

export type {OperationsLogFilters, OperationsLogFilterField};
export {
  AUDIT_LOG_FILTER_FIELDS,
  AUDIT_LOG_ENTITY_TYPE_FILTER_VALUES,
  AUDIT_LOG_OPERATION_TYPE_FILTER_VALUES,
};
