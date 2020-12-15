/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

interface VariableEntity {
  hasActiveOperation: boolean;
  id?: string;
  name: string;
  scopeId?: string;
  value: string;
  workflowInstanceId: WorkflowInstanceEntity['id'];
}

type OperationEntityType =
  | 'RESOLVE_INCIDENT'
  | 'CANCEL_WORKFLOW_INSTANCE'
  | 'UPDATE_VARIABLE';

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
  sortValues: [string, string];
}

interface InstanceOperationEntity {
  id: string;
  type: OperationEntityType;
  state: 'SENT' | 'COMPLETED';
  errorMessage: null | string;
}

interface WorkflowInstanceEntity {
  id: string;
  workflowId: string;
  workflowName: string;
  workflowVersion: number;
  startDate: string;
  endDate: null | string;
  state: InstanceEntityState;
  bpmnProcessId: string;
  hasActiveOperation: boolean;
  operations: ReadonlyArray<InstanceOperationEntity>;
  sortValues: ReadonlyArray<string>;
}
