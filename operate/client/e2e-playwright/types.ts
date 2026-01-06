/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type BatchOperationDto = {
  id: string;
  name: string | null;
  type: OperationEntityType;
  startDate: string;
  endDate: string | null;
  username: string;
  instancesCount: number;
  operationsTotalCount: number;
  operationsFinishedCount: number;
};

type ProcessVersionDto = {
  bpmnProcessId: string;
  id: string;
  name: string;
  version: number;
  versionTag: string | null;
};

type ProcessDto = {
  bpmnProcessId: string;
  name: string | null;
  processes: ProcessVersionDto[];
  permissions?: ResourceBasedPermissionDto[] | null;
  tenantId: string;
};

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
  failedOperationsCount?: number;
  completedOperationsCount?: number;
}

type InstanceEntityState =
  | 'ACTIVE'
  | 'COMPLETED'
  | 'CANCELED'
  | 'INCIDENT'
  | 'TERMINATED';

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

type ProcessInstancesDto = {
  processInstances: ProcessInstanceEntity[];
  totalCount: number;
};

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

type FlowNodeInstancesDto<T> = {
  [treePath: string]: {
    running: boolean | null;
    children: T[];
  };
};

type InstanceMetaDataDto = {
  flowNodeInstanceId: string;
  flowNodeId: string;
  flowNodeType: string;
  startDate: string;
  endDate: string | null;
  calledProcessInstanceId: string | null;
  calledProcessDefinitionName: string | null;
  calledDecisionInstanceId: string | null;
  calledDecisionDefinitionName: string | null;
  eventId: string;
  jobType: string | null;
  jobRetries: number | null;
  jobWorker: string | null;
  jobDeadline: string | null;
  jobCustomHeaders: {[key: string]: string} | null;
  jobId: string | null;
};

type MetaDataDto = {
  flowNodeInstanceId: string | null;
  flowNodeId: string | null;
  flowNodeType: string | null;
  instanceCount: number | null;
  instanceMetadata: InstanceMetaDataDto | null;
  incidentCount: number;
  incident: {
    id: string;
    errorType: {
      id: string;
      name: string;
    };
    errorMessage: string;
    flowNodeId: string;
    flowNodeInstanceId: string;
    jobId: string | null;
    creationTime: string;
    hasActiveOperation: boolean;
    lastOperation: boolean | null;
    rootCauseInstance: {
      instanceId: string;
      processDefinitionId: string;
      processDefinitionName: string;
    } | null;
    rootCauseDecision: {
      instanceId: string;
      decisionName: string;
    } | null;
  } | null;
};

type FlowNodeDto = {
  id: string;
  count: number;
};
type ErrorTypeDto = {
  id: string;
  name: string;
  count: number;
};
type IncidentDto = {
  id: string;
  errorType: {
    id: string;
    name: string;
  };
  errorMessage: string;
  flowNodeId: string;
  flowNodeInstanceId: string;
  jobId: string | null;
  creationTime: string;
  hasActiveOperation: boolean;
  lastOperation: null | unknown;
  rootCauseInstance: null | {
    instanceId: string;
    processDefinitionId: string;
    processDefinitionName: string;
  };
};

type ProcessInstanceIncidentsDto = {
  count: number;
  incidents: IncidentDto[];
  errorTypes: ErrorTypeDto[];
  flowNodes: FlowNodeDto[];
};

type SequenceFlowDto = {
  processInstanceId: string;
  activityId: string;
};

type SequenceFlowsDto = SequenceFlowDto[];

type ProcessStatisticsDto = {
  processId: string;
  tenantId: string;
  version: number;
  name: null | string;
  bpmnProcessId: string;
  errorMessage: string | null;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
};

type IncidentByErrorDto = {
  errorMessage: string;
  incidentErrorHashCode: number;
  instancesWithErrorCount: number;
  processes: ProcessStatisticsDto[];
};

type ProcessInstanceByNameDto = {
  bpmnProcessId: string;
  tenantId: string;
  processName: null | string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
  processes: ProcessStatisticsDto[];
};

type CoreStatisticsDto = {
  running: number;
  active: number;
  withIncidents: number;
};

type DecisionInstanceEntityState = 'EVALUATED' | 'FAILED';

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

type DecisionInstancesDto = {
  decisionInstances: DecisionInstanceEntity[];
  totalCount: number;
};

type DecisionDto = {
  decisionId: string;
  name: string | null;
  decisions: {
    id: string;
    version: number;
    decisionId: string;
  }[];
  permissions?: ResourceBasedPermissionDto[] | null;
  tenantId: string;
};

export type {
  BatchOperationDto,
  ProcessVersionDto,
  ProcessDto,
  OperationEntityType,
  OperationEntity,
  InstanceEntityState,
  InstanceOperationEntity,
  ProcessInstancesDto,
  ProcessInstanceEntity,
  FlowNodeInstanceDto,
  FlowNodeInstancesDto,
  InstanceMetaDataDto,
  MetaDataDto,
  FlowNodeDto,
  ErrorTypeDto,
  IncidentDto,
  ProcessInstanceIncidentsDto,
  SequenceFlowDto,
  SequenceFlowsDto,
  IncidentByErrorDto,
  ProcessInstanceByNameDto,
  CoreStatisticsDto,
  DecisionInstancesDto,
  DecisionDto,
};
