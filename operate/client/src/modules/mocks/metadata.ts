/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Job} from '@camunda/camunda-api-zod-schemas/8.8';

const FLOW_NODE_ID = 'StartEvent_1'; // this need to match the id from mockProcessXML
const CALL_ACTIVITY_FLOW_NODE_ID = 'Activity_0zqism7'; // this need to match the id from mockCallActivityProcessXML
const USER_TASK_FLOW_NODE_ID = 'UserTask';
const FLOW_NODE_INSTANCE_ID = '2251799813699889';
const PROCESS_INSTANCE_ID = '2251799813685591';
const BUSINESS_RULE_FLOW_NODE_ID = 'BusinessRuleTask';

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

const calledDecisionInstanceMetadata = {
  decisionEvaluationKey: '9876543210',
  decisionEvaluationInstanceKey: '9876543210',
  decisionDefinitionName: 'Approval Rules',
  decisionDefinitionId: 'approval-decision',
  decisionDefinitionKey: '123456',
  decisionDefinitionVersion: 1,
  decisionDefinitionType: 'DECISION_TABLE' as const,
  processDefinitionKey: '2',
  processInstanceKey: PROCESS_INSTANCE_ID,
  elementInstanceKey: '2251799813699889',
  state: 'EVALUATED' as const,
  evaluationDate: '2023-01-15T10:05:00.000Z',
  evaluationFailure: '',
  tenantId: '<default>',
  result: '',
  rootDecisionDefinitionKey: '123456',
};

export {
  jobMetadata,
  calledDecisionInstanceMetadata,
  PROCESS_INSTANCE_ID,
  CALL_ACTIVITY_FLOW_NODE_ID,
  FLOW_NODE_ID,
  USER_TASK_FLOW_NODE_ID,
  BUSINESS_RULE_FLOW_NODE_ID,
};
