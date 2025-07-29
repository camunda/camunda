/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';
import type {Job} from '@vzeta/camunda-api-zod-schemas/8.8';

const FLOW_NODE_ID = 'StartEvent_1'; // this need to match the id from mockProcessXML
const CALL_ACTIVITY_FLOW_NODE_ID = 'Activity_0zqism7'; // this need to match the id from mockCallActivityProcessXML
const USER_TASK_FLOW_NODE_ID = 'UserTask';
const FLOW_NODE_INSTANCE_ID = '2251799813699889';
const PROCESS_INSTANCE_ID = '2251799813685591';
const BUSSINESS_RULE_FLOW_NODE_ID = 'BusinessRuleTask';

const baseInstanceMetadata: MetaDataDto['instanceMetadata'] = {
  flowNodeId: FLOW_NODE_ID,
  flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
  flowNodeType: 'START_EVENT',
  startDate: '2018-12-12 00:00:00',
  endDate: '2018-12-12 00:00:00',
  calledProcessInstanceId: null,
  calledProcessDefinitionName: null,
  calledDecisionInstanceId: null,
  calledDecisionDefinitionName: null,
  eventId: '2251799813695565_2251799813695582',
  jobType: null,
  jobRetries: null,
  jobWorker: null,
  jobDeadline: '2018-12-12 00:00:00',
  jobCustomHeaders: null,
  jobId: null,
};

const baseMetadata: MetaDataDto = {
  flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
  flowNodeId: null,
  flowNodeType: null,
  instanceCount: null,
  incidentCount: 0,
  incident: null,
  instanceMetadata: baseInstanceMetadata,
};

const incidentFlowNodeMetaData = {
  ...baseMetadata,
  instanceMetadata: {
    ...baseInstanceMetadata,
    endDate: null,
  },
  incident: {
    id: '4503599627375678',
    errorType: {id: 'JOB_NO_RETRIES', name: 'No more retries left.'},
    errorMessage: 'There are no more retries left.',
    rootCauseInstance: {
      instanceId: '00000000000000',
      processDefinitionId: '111111111111111',
      processDefinitionName: 'Called Process',
    },
    flowNodeId: 'call-order-process',
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    jobId: null,
    creationTime: '2022-02-03T16:44:06.981+0000',
    hasActiveOperation: false,
    lastOperation: null,
    rootCauseDecision: null,
  },
  incidentCount: 1,
};

const calledInstanceMetadata = {
  ...baseMetadata,
  instanceMetadata: {
    ...baseInstanceMetadata,
    flowNodeId: CALL_ACTIVITY_FLOW_NODE_ID,
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    flowNodeType: 'TASK_CALL_ACTIVITY',
    calledProcessInstanceId: '229843728748927482',
    calledProcessDefinitionName: 'Called Process',
  },
};

const calledInstanceWithIncidentMetadata = {
  ...calledInstanceMetadata,
  incident: {
    id: '4503599627375678',
    errorType: {
      id: 'CALLED_ELEMENT_ERROR',
      name: 'Called Element Error',
    },
    errorMessage:
      "Expected process with BPMN process id 'called-process' to be deployed, but not found.",
    flowNodeId: 'call-order-process',
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    jobId: null,
    creationTime: '2022-02-03T16:44:06.981+0000',
    hasActiveOperation: false,
    lastOperation: null,
    rootCauseInstance: null,
    rootCauseDecision: null,
  },
  incidentCount: 1,
};

const multiInstancesMetadata = {
  ...baseMetadata,
  flowNodeInstanceId: null,
  instanceCount: 10,
  incidentCount: 3,
  instanceMetadata: null,
};

const multiInstanceCallActivityMetadata = {
  ...baseMetadata,
  flowNodeType: 'MULTI_INSTANCE_BODY',
};

const calledDecisionMetadata = {
  ...baseMetadata,
  instanceMetadata: {
    ...baseInstanceMetadata,
    flowNodeType: 'TASK_BUSINESS_RULE',
    calledDecisionInstanceId: '750893257230984',
    calledDecisionDefinitionName: 'Take decision',
  },
};

const calledUnevaluatedDecisionMetadata = {
  ...baseMetadata,
  instanceMetadata: {
    ...baseInstanceMetadata,
    endDate: null,
    flowNodeType: 'TASK_BUSINESS_RULE',
    calledDecisionInstanceId: null,
    calledDecisionDefinitionName: 'Take decision',
  },
};

const rootIncidentFlowNodeMetaData = {
  ...calledInstanceWithIncidentMetadata,
  incident: {
    ...calledInstanceWithIncidentMetadata.incident,
    rootCauseInstance: {
      processDefinitionId: 'called-process',
      processDefinitionName: 'Called Process',
      instanceId: PROCESS_INSTANCE_ID,
    },
  },
};

const calledFailedDecisionMetadata = {
  ...calledDecisionMetadata,
  incidentCount: 1,
  incident: {
    id: '753289475393927',
    flowNodeId: 'call-order-process',
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    jobId: null,
    creationTime: '2022-02-03T16:44:06.981+0000',
    hasActiveOperation: false,
    lastOperation: null,
    rootCauseInstance: {
      instanceId: '00000000000000',
      processDefinitionId: '111111111111111',
      processDefinitionName: 'Called Process',
    },
    errorType: {
      id: 'DECISION_EVALUALTION_ERROR',
      name: 'Decision Evaluation Error',
    },
    errorMessage:
      "Failed to evaluate expression 'paid = false': no variable found for name 'paid'",
    rootCauseDecision: {
      instanceId: '1435052978109345',
      decisionName: 'Take another decision',
    },
  },
};

const userTaskFlowNodeMetaData = {
  ...baseMetadata,
  instanceMetadata: {
    ...baseInstanceMetadata,
    flowNodeId: USER_TASK_FLOW_NODE_ID,
    flowNodeType: 'USER_TASK',
  },
};

const retriesLeftFlowNodeMetaData = {
  ...baseMetadata,
  instanceMetadata: {...baseInstanceMetadata, jobRetries: 2},
};

const incidentsByProcessKeyMetadata = {
  items: [
    {
      processDefinitionId: 'invoice',
      errorType: 'JOB_NO_RETRIES' as const,
      errorMessage: 'There are no more retries left.',
      elementId: 'call-order-process',
      creationTime: '2022-02-03T16:44:06.981+0000',
      state: 'PENDING' as const,
      tenantId: '<default>',
      incidentKey: '2251799814080730',
      processDefinitionKey: '2251799813686633',
      processInstanceKey: '2251799813685593',
      elementInstanceKey: FLOW_NODE_INSTANCE_ID,
      jobKey: '2251799814080730',
    },
  ],
  page: {totalItems: 1},
};

const processDefinitionMetadata = {
  name: 'DMN invoice',
  processDefinitionId: 'invoice',
  processDefinitionKey: '2251799813686633',
  resourceName: 'usertest/invoice.bpmn',
  tenantId: '<default>',
  version: 1,
};

const jobMetadata: Job = {
  customHeaders: {},
  elementId: 'Activity_0dex012',
  elementInstanceKey: FLOW_NODE_INSTANCE_ID,
  deadline: '2018-12-12 00:00:00',
  endTime: '2025-07-23T10:14:48.597Z',
  errorCode: '',
  errorMessage: '',
  hasFailedWithRetriesLeft: false,
  jobKey: '2251799813939822',
  kind: 'BPMN_ELEMENT',
  listenerEventType: 'UNSPECIFIED',
  processDefinitionId: 'signalEventProcess',
  processDefinitionKey: '2251799813686708',
  processInstanceKey: PROCESS_INSTANCE_ID,
  retries: 1,
  state: 'CANCELED',
  tenantId: '<default>',
  type: 'io.camunda.zeebe:userTask',
  worker: '',
  isDenied: false,
  deniedReason: '',
};

export {
  baseMetadata as singleInstanceMetadata,
  incidentFlowNodeMetaData,
  calledDecisionMetadata,
  calledFailedDecisionMetadata,
  calledUnevaluatedDecisionMetadata,
  calledInstanceMetadata,
  multiInstancesMetadata,
  multiInstanceCallActivityMetadata,
  rootIncidentFlowNodeMetaData,
  userTaskFlowNodeMetaData,
  retriesLeftFlowNodeMetaData,
  incidentsByProcessKeyMetadata,
  processDefinitionMetadata,
  jobMetadata,
  PROCESS_INSTANCE_ID,
  CALL_ACTIVITY_FLOW_NODE_ID,
  FLOW_NODE_ID,
  USER_TASK_FLOW_NODE_ID,
  BUSSINESS_RULE_FLOW_NODE_ID,
  FLOW_NODE_INSTANCE_ID,
};
