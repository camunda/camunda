/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {MetaDataDto} from 'modules/api/processInstances/fetchFlowNodeMetaData';

const FLOW_NODE_ID = 'StartEvent_1'; // this need to match the id from mockProcessXML
const CALL_ACTIVITY_FLOW_NODE_ID = 'Activity_0zqism7'; // this need to match the id from mockCallActivityProcessXML
const USER_TASK_FLOW_NODE_ID = 'UserTask';
const FLOW_NODE_INSTANCE_ID = '2251799813699889';
const PROCESS_INSTANCE_ID = '2251799813685591';

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

const multiInstanceMetadata = {
  ...baseMetadata,
  breadcrumb: [
    {
      flowNodeId: 'Task',
      flowNodeType: 'MULTI_INSTANCE_BODY',
    },
    {
      flowNodeId: 'Task',
      flowNodeType: 'SERVICE_TASK',
    },
  ],
  instanceMetadata: {
    ...baseInstanceMetadata,
    flowNodeId: 'Task',
    flowNodeInstanceId: FLOW_NODE_INSTANCE_ID,
    flowNodeType: 'SERVICE_TASK',
  },
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

export {
  baseMetadata as singleInstanceMetadata,
  incidentFlowNodeMetaData,
  calledDecisionMetadata,
  calledFailedDecisionMetadata,
  calledUnevaluatedDecisionMetadata,
  calledInstanceMetadata,
  calledInstanceWithIncidentMetadata,
  multiInstancesMetadata,
  multiInstanceMetadata,
  multiInstanceCallActivityMetadata,
  rootIncidentFlowNodeMetaData,
  userTaskFlowNodeMetaData,
  retriesLeftFlowNodeMetaData,
  PROCESS_INSTANCE_ID,
  CALL_ACTIVITY_FLOW_NODE_ID,
  FLOW_NODE_ID,
  USER_TASK_FLOW_NODE_ID,
};
