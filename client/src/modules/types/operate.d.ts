/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

interface VariableEntity {
  isFirst: boolean;
  hasActiveOperation: boolean;
  id?: string;
  name: string;
  value: string;
  sortValues: [string] | null;
  isPreview: boolean;
}

type OperationEntityType =
  | 'RESOLVE_INCIDENT'
  | 'CANCEL_PROCESS_INSTANCE'
  | 'DELETE_PROCESS_INSTANCE'
  | 'UPDATE_VARIABLE'
  | 'ADD_VARIABLE'
  | 'MODIFY_PROCESS_INSTANCE'
  | 'DELETE_DECISION_DEFINITION'
  | 'DELETE_PROCESS_DEFINITION';

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
}

interface InstanceOperationEntity {
  id?: string;
  batchOperationId?: string;
  type: OperationEntityType;
  state: 'SENT' | 'COMPLETED' | 'SCHEDULED' | 'LOCKED' | 'FAILED';
  errorMessage: null | string;
}

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
}

interface DecisionInstanceEntity {
  id: string;
  decisionName: string;
  decisionVersion: number;
  evaluationDate: string;
  processInstanceId: string | null;
  state: DecisionInstanceEntityState;
  sortValues: [string, string];
}
