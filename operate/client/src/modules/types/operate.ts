/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type OperationEntityType =
  | 'RESOLVE_INCIDENT'
  | 'CANCEL_PROCESS_INSTANCE'
  | 'DELETE_PROCESS_INSTANCE'
  | 'UPDATE_VARIABLE'
  | 'ADD_VARIABLE'
  | 'MODIFY_PROCESS_INSTANCE'
  | 'DELETE_DECISION_DEFINITION'
  | 'DELETE_PROCESS_DEFINITION'
  | 'MIGRATE_PROCESS_INSTANCE'
  | 'MOVE_TOKEN';

type FlowNodeState = 'active' | 'incidents' | 'canceled' | 'completed';

type InstanceEntityState =
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELED'
  | 'INCIDENT'
  | 'TERMINATED';

type DecisionInstanceEntityState = 'EVALUATED' | 'FAILED';

interface OperationEntity {
  id: string;
  name: null | string;
  type: OperationEntityType;
  startDate: string;
  endDate: null | string;
  instancesCount: number;
  operationsTotalCount: number;
  operationsFinishedCount: number;
  sortValues?: [string, string];
  failedOperationsCount?: number; // Should become required when BE issue #6294 gets resolved
  completedOperationsCount?: number; // Should become required when BE issue #6294 gets resolved
}

interface InstanceOperationEntity {
  id?: string;
  batchOperationId?: string;
  type: OperationEntityType;
  state: 'SENT' | 'COMPLETED' | 'SCHEDULED' | 'LOCKED' | 'FAILED';
  errorMessage: null | string;
  completedDate: null | string;
}

type ResourceBasedPermissionDto =
  | 'READ'
  | 'DELETE'
  | 'UPDATE_PROCESS_INSTANCE'
  | 'DELETE_PROCESS_INSTANCE';

interface ProcessInstanceEntity {
  id: string;
  processId: string;
  processName: string;
  processVersion: number;
  startDate: string;
  endDate: null | string;
  state: InstanceEntityState;
  bpmnProcessId: string;
  hasActiveOperation: boolean;
  operations: Array<InstanceOperationEntity>;
  sortValues: ReadonlyArray<string>;
  parentInstanceId: null | string;
  rootInstanceId: null | string;
  callHierarchy: ReadonlyArray<{
    instanceId: string;
    processDefinitionName: string;
  }>;
  permissions?: ResourceBasedPermissionDto[] | null;
  tenantId: string;
}

interface DecisionInstanceEntity {
  id: string;
  decisionName: string;
  decisionVersion: number;
  tenantId: string;
  evaluationDate: string;
  processInstanceId: string | null;
  state: DecisionInstanceEntityState;
  sortValues: [string, string];
}

type SortOrder = 'asc' | 'desc';

export type {
  OperationEntityType,
  FlowNodeState,
  InstanceEntityState,
  OperationEntity,
  InstanceOperationEntity,
  ResourceBasedPermissionDto,
  ProcessInstanceEntity,
  DecisionInstanceEntity,
  SortOrder,
};
