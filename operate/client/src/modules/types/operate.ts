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

interface ProcessInstanceEntity {
  id: string;
  processId: string;
  processName: string;
  processVersion: number;
  processVersionTag?: string | null;
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
  tenantId: string;
}

type FlowNodeInstanceDto = {
  id: string;
  type: string;
  state?: InstanceEntityState;
  flowNodeId: string;
  startDate: string;
  endDate: null | string;
  treePath: string;
  sortValues: [string, string] | [];
};

type FlowNodeInstance = FlowNodeInstanceDto & {isPlaceholder?: boolean};

export type {
  OperationEntityType,
  FlowNodeState,
  InstanceEntityState,
  OperationEntity,
  InstanceOperationEntity,
  ProcessInstanceEntity,
  FlowNodeInstance,
};
