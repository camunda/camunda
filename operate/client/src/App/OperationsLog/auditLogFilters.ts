/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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

export type {OperationsLogFilters, OperationsLogFilterField};
export {AUDIT_LOG_FILTER_FIELDS};
