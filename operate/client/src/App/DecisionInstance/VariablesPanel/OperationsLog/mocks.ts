/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLogEntry} from 'modules/api/v2/auditLog/searchAuditLog';

export type MockDecisionAuditLogEntry = AuditLogEntry & {
  errorMessage?: string;
};

export const mockDecisionOperationLog: MockDecisionAuditLogEntry[] = [
  {
    id: '1',
    decisionDefinitionName: 'Discount Decision',
    decisionDefinitionVersion: 1,
    decisionEvaluationInstanceKey: '123',
    tenantId: 'default',
    operationType: 'EVALUATE_DECISION',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 10000).toISOString(),
    user: 'decision-engine-client',
  },
  {
    id: '2',
    decisionDefinitionName: 'Discount Decision',
    decisionDefinitionVersion: 1,
    decisionEvaluationInstanceKey: '123',
    tenantId: 'default',
    operationType: 'EVALUATE_DECISION',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 20000).toISOString(),
    user: 'order-service-client',
  },
  {
    id: '3',
    decisionDefinitionName: 'Discount Decision',
    decisionDefinitionVersion: 1,
    decisionEvaluationInstanceKey: '123',
    tenantId: 'default',
    operationType: 'EVALUATE_DECISION',
    operationState: 'fail',
    startTimestamp: new Date(Date.now() - 30000).toISOString(),
    user: 'pricing-service-client',
    errorMessage: 'Failed to evaluate decision: Invalid input variable "orderAmount". Expected number, got string.',
  },
  {
    id: '4',
    decisionDefinitionName: 'Discount Decision',
    decisionDefinitionVersion: 1,
    decisionEvaluationInstanceKey: '123',
    tenantId: 'default',
    operationType: 'EVALUATE_DECISION',
    operationState: 'success',
    startTimestamp: new Date(Date.now() - 40000).toISOString(),
    user: 'api-gateway-client',
  },
  {
    id: '5',
    decisionDefinitionName: 'Discount Decision',
    decisionDefinitionVersion: 1,
    decisionEvaluationInstanceKey: '123',
    tenantId: 'default',
    operationType: 'EVALUATE_DECISION',
    operationState: 'fail',
    startTimestamp: new Date(Date.now() - 50000).toISOString(),
    user: 'test-client',
    errorMessage: 'Failed to evaluate decision: Decision definition not found or has errors in the DMN table.',
  },
];

