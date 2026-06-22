/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  ProcessInstance,
  ProcessDefinition,
  Variable,
  ElementInstance,
  Job,
} from '@camunda/camunda-api-zod-schemas/8.10';

import {
  MOCK_AGENT_INSTANCE_KEY_ACTIVE,
  MOCK_AGENT_DEFINITION_KEY_ACTIVE,
  MOCK_AGENT_DEFINITION_ID_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_1_KEY_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_2_KEY_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_3_KEY_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_4_KEY_ACTIVE,
  MOCK_AGENT_INNER_INSTANCE_5_KEY_ACTIVE,
  MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_ACTIVE,
  MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
  MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
  MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
  MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
  MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  MOCK_AGENT_DEFINITION_ID_COMPLETED,
  MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED,
  MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED,
  MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_COMPLETED,
  MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_ID_MULTIPLE,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_1_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_2_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_3_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_1,
  MOCK_AGENT_INNER_INSTANCE_5_KEY_MULTIPLE_1,
  MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_MULTIPLE_1,
  MOCK_USER_FEEDBACK_KEY_MULTIPLE,
  MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
  MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_2,
  MOCK_AGENT_INSTANCE_KEY_FLAT,
  MOCK_AGENT_DEFINITION_KEY_FLAT,
  MOCK_AGENT_DEFINITION_ID_FLAT,
  MOCK_AGENT_SUBPROCESS_KEY_FLAT,
} from './constants';

// Extended type for mock data — flowScopeKey is not in the API type but needed for scope filtering
type MockElementInstance = ElementInstance & {flowScopeKey: string};

export const MOCK_AGENT_PROCESS_INSTANCE_ACTIVE: ProcessInstance = {
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
  processDefinitionName: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  tenantId: '<default>',
  state: 'ACTIVE',
  startDate: '2026-03-26T14:30:00.000Z',
  endDate: null,
  hasIncident: false,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
};

export const MOCK_AGENT_PROCESS_DEFINITION_ACTIVE: ProcessDefinition = {
  name: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
  resourceName: 'ai-agent-chat-with-tools.bpmn',
  version: 1,
  versionTag: null,
  tenantId: '<default>',
  hasStartForm: false,
};

export const MOCK_AGENT_ELEMENT_INSTANCES_ACTIVE: MockElementInstance[] = [
  {
    elementInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementName: 'AI Agent Chat With Tools',
    type: 'PROCESS',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.000Z',
    endDate: null,
    incidentKey: null,
  },
  {
    elementInstanceKey: '4451799813685001',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'StartEvent_1',
    elementName: 'Task to perform received',
    type: 'START_EVENT',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.100Z',
    endDate: '2026-03-26T14:30:00.200Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '4451799813685005',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'Gateway_0z6ctwk',
    elementName: null,
    type: 'EXCLUSIVE_GATEWAY',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.250Z',
    endDate: '2026-03-26T14:30:00.280Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'AI_Agent',
    elementName: 'AI Agent',
    type: 'AD_HOC_SUB_PROCESS',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.300Z',
    endDate: null,
    incidentKey: null,
  },
  // Tool activation 1: ListUsers — wrapped in AI_Agent#innerInstance
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_1_KEY_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:01.200Z',
    endDate: '2026-03-26T14:30:02.800Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '4451799813685020',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'ListUsers',
    elementName: 'List users',
    type: 'SERVICE_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_1_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:01.200Z',
    endDate: '2026-03-26T14:30:02.800Z',
    incidentKey: null,
  },
  // Tool activation 2: LoadUserByID — wrapped in AI_Agent#innerInstance
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_2_KEY_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.000Z',
    endDate: '2026-03-26T14:30:04.100Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '4451799813685025',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'LoadUserByID',
    elementName: 'Load user by ID',
    type: 'SERVICE_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_2_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.000Z',
    endDate: '2026-03-26T14:30:04.100Z',
    incidentKey: null,
  },
  // Tool activation 3: GetDateAndTime — wrapped in AI_Agent#innerInstance (parallel with LoadUserByID)
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_3_KEY_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.050Z',
    endDate: '2026-03-26T14:30:04.150Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '4451799813685030',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'GetDateAndTime',
    elementName: 'Get Date and Time',
    type: 'SCRIPT_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_3_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.050Z',
    endDate: '2026-03-26T14:30:04.150Z',
    incidentKey: null,
  },
  // Tool activation 4: AskHumanToSendEmail — wrapped in AI_Agent#innerInstance (in flight)
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_4_KEY_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.300Z',
    endDate: null,
    incidentKey: null,
  },
  {
    elementInstanceKey: '4451799813685035',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'AskHumanToSendEmail',
    elementName: 'Ask human to send email',
    type: 'USER_TASK',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_4_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.300Z',
    endDate: null,
    incidentKey: null,
  },
  // Tool activation 5: AI_Task_Agent — nested agent task, in flight (Thinking…)
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_5_KEY_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.320Z',
    endDate: null,
    incidentKey: null,
  },
  {
    elementInstanceKey: MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    elementId: 'AI_Task_Agent',
    elementName: 'AI task agent',
    type: 'TASK',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_5_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.320Z',
    endDate: null,
    incidentKey: null,
  },
];

export const MOCK_AGENT_ELEMENT_STATISTICS_ACTIVE = {
  items: [
    {
      elementId: 'StartEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'Gateway_0z6ctwk',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {elementId: 'AI_Agent', active: 1, canceled: 0, incidents: 0, completed: 0},
    {
      elementId: 'ListUsers',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'LoadUserByID',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'GetDateAndTime',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'AskHumanToSendEmail',
      active: 1,
      canceled: 0,
      incidents: 0,
      completed: 0,
    },
    {
      elementId: 'AI_Task_Agent',
      active: 1,
      canceled: 0,
      incidents: 0,
      completed: 0,
    },
  ],
};

export const MOCK_AGENT_SEQUENCE_FLOWS_ACTIVE = {
  items: [
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
      elementId: 'Flow_0pbzrme',
    },
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
      elementId: 'Flow_16otfp1',
    },
  ],
};

export const MOCK_AGENT_VARIABLES_ACTIVE: Variable[] = [
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_ACTIVE}-inputText`,
    name: 'inputText',
    value:
      '"Find the email address of user Leanne Graham and send her an invitation to the company offsite."',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    rootProcessInstanceKey: null,
  },
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_ACTIVE}-inputDocuments`,
    name: 'inputDocuments',
    value: 'null',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    rootProcessInstanceKey: null,
  },
  {
    variableKey: `${MOCK_AGENT_SUBPROCESS_KEY_ACTIVE}-agent`,
    name: 'agent',
    value: JSON.stringify({
      context: {
        state: 'WAITING_FOR_TOOL',
        metrics: {
          modelCalls: 3,
          tokenUsage: {
            inputTokenCount: 1959,
            outputTokenCount: 294,
          },
        },
        toolDefinitions: [
          {
            name: 'ListUsers',
            description:
              'Retrieves a list of all users from the user directory API',
            inputSchema: {type: 'object', properties: {}},
          },
          {
            name: 'LoadUserByID',
            description: 'Loads detailed user profile by numeric user ID',
            inputSchema: {
              required: ['id'],
              type: 'object',
              properties: {id: {type: 'number', description: 'The user ID'}},
            },
          },
          {
            name: 'GetDateAndTime',
            description: 'Returns the current date and time in ISO 8601 format',
            inputSchema: {type: 'object', properties: {}},
          },
          {
            name: 'AskHumanToSendEmail',
            description:
              'Requests a human operator to compose and send an email',
            inputSchema: {
              required: [
                'recipient_name',
                'recipient_email',
                'email_subject',
                'email_body',
              ],
              type: 'object',
              properties: {
                recipient_name: {type: 'string'},
                recipient_email: {type: 'string'},
                email_subject: {type: 'string'},
                email_body: {type: 'string'},
              },
            },
          },
          {
            name: 'FetchURL',
            description:
              'Fetches content from a given URL and returns the response body',
            inputSchema: {
              required: ['url'],
              type: 'object',
              properties: {
                url: {type: 'string', description: 'The URL to fetch'},
              },
            },
          },
        ],
      },
    }),
    isTruncated: true,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    scopeKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    rootProcessInstanceKey: null,
  },
  {
    variableKey: `${MOCK_AGENT_SUBPROCESS_KEY_ACTIVE}-toolCallResults`,
    name: 'toolCallResults',
    value: JSON.stringify([
      {
        id: 'tooluse_ListUsers_abc123',
        name: 'ListUsers',
        content: {status: 'success'},
      },
      {
        id: 'tooluse_LoadUserByID_def456',
        name: 'LoadUserByID',
        content: {status: 'success'},
      },
      {
        id: 'tooluse_GetDateAndTime_ghi789',
        name: 'GetDateAndTime',
        content: {status: 'success'},
      },
    ]),
    isTruncated: true,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    scopeKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    rootProcessInstanceKey: null,
  },
];

export const MOCK_AGENT_JOBS_ACTIVE: Job[] = [
  {
    jobKey: '4451799813685011',
    type: 'io.camunda.agenticai:aiagent-job-worker:1',
    worker: 'ai-agent-worker-1',
    state: 'CREATED',
    kind: 'BPMN_ELEMENT',
    listenerEventType: 'UNSPECIFIED',
    retries: 3,
    isDenied: null,
    deniedReason: null,
    hasFailedWithRetriesLeft: false,
    errorMessage: null,
    errorCode: null,
    customHeaders: {},
    deadline: null,
    endTime: null,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_ACTIVE,
    rootProcessInstanceKey: null,
    elementId: 'AI_Agent',
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_ACTIVE,
    creationTime: '2026-03-26T14:30:00.300Z',
    lastUpdateTime: null,
    tags: [],
    tenantId: '<default>',
  },
];

export const MOCK_AGENT_PROCESS_INSTANCE_NOT_ACTIVE: ProcessInstance = {
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
  processDefinitionName: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  tenantId: '<default>',
  state: 'ACTIVE',
  startDate: '2026-03-26T14:30:00.000Z',
  endDate: null,
  hasIncident: false,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
};

export const MOCK_AGENT_PROCESS_DEFINITION_NOT_ACTIVE: ProcessDefinition = {
  name: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
  resourceName: 'ai-agent-chat-with-tools.bpmn',
  version: 1,
  versionTag: null,
  tenantId: '<default>',
  hasStartForm: false,
};

export const MOCK_AGENT_ELEMENT_INSTANCES_NOT_ACTIVE: MockElementInstance[] = [
  {
    elementInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
    elementId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
    elementName: 'AI Agent Chat With Tools',
    type: 'PROCESS',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.000Z',
    endDate: null,
    incidentKey: null,
  },
  {
    elementInstanceKey: '5451799813685001',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
    elementId: 'StartEvent_1',
    elementName: 'Task to perform received',
    type: 'START_EVENT',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.100Z',
    endDate: '2026-03-26T14:30:00.200Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '5451799813685005',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_NOT_ACTIVE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_NOT_ACTIVE,
    elementId: 'Gateway_0z6ctwk',
    elementName: null,
    type: 'EXCLUSIVE_GATEWAY',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.250Z',
    endDate: null,
    incidentKey: null,
  },
];

export const MOCK_AGENT_ELEMENT_STATISTICS_NOT_ACTIVE = {
  items: [
    {
      elementId: 'StartEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'Gateway_0z6ctwk',
      active: 1,
      canceled: 0,
      incidents: 0,
      completed: 0,
    },
  ],
};

export const MOCK_AGENT_SEQUENCE_FLOWS_NOT_ACTIVE = {
  items: [
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
      elementId: 'Flow_0pbzrme',
    },
  ],
};

export const MOCK_AGENT_VARIABLES_NOT_ACTIVE: Variable[] = [
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE}-inputText`,
    name: 'inputText',
    value:
      '"Find the email address of user Leanne Graham and send her an invitation to the company offsite."',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_NOT_ACTIVE,
    rootProcessInstanceKey: null,
  },
];

export const MOCK_AGENT_JOBS_NOT_ACTIVE: Job[] = [];

export const MOCK_AGENT_PROCESS_INSTANCE_COMPLETED: ProcessInstance = {
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
  processDefinitionName: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  tenantId: '<default>',
  state: 'ACTIVE',
  startDate: '2026-03-26T14:30:00.000Z',
  endDate: null,
  hasIncident: false,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
};

export const MOCK_AGENT_PROCESS_DEFINITION_COMPLETED: ProcessDefinition = {
  name: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
  resourceName: 'ai-agent-chat-with-tools.bpmn',
  version: 1,
  versionTag: null,
  tenantId: '<default>',
  hasStartForm: false,
};

const COMPLETED_END_DATE = '2026-03-26T14:30:05.500Z';

export const MOCK_AGENT_ELEMENT_INSTANCES_COMPLETED: MockElementInstance[] = [
  {
    elementInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementName: 'AI Agent Chat With Tools',
    type: 'PROCESS',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.000Z',
    endDate: null,
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685001',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'StartEvent_1',
    elementName: 'Task to perform received',
    type: 'START_EVENT',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.100Z',
    endDate: '2026-03-26T14:30:00.200Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685005',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'Gateway_0z6ctwk',
    elementName: null,
    type: 'EXCLUSIVE_GATEWAY',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.250Z',
    endDate: '2026-03-26T14:30:00.280Z',
    incidentKey: null,
  },
  // AI_Agent outer subprocess — COMPLETED.
  {
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent',
    type: 'AD_HOC_SUB_PROCESS',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.300Z',
    endDate: COMPLETED_END_DATE,
    incidentKey: null,
  },
  // Tool 1: ListUsers wrapper + child
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:01.200Z',
    endDate: '2026-03-26T14:30:02.800Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685020',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'ListUsers',
    elementName: 'List users',
    type: 'SERVICE_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:01.200Z',
    endDate: '2026-03-26T14:30:02.800Z',
    incidentKey: null,
  },
  // Tool 2: LoadUserByID
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.000Z',
    endDate: '2026-03-26T14:30:04.100Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685025',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'LoadUserByID',
    elementName: 'Load user by ID',
    type: 'SERVICE_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.000Z',
    endDate: '2026-03-26T14:30:04.100Z',
    incidentKey: null,
  },
  // Tool 3: GetDateAndTime
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.050Z',
    endDate: '2026-03-26T14:30:04.150Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685030',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'GetDateAndTime',
    elementName: 'Get Date and Time',
    type: 'SCRIPT_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:03.050Z',
    endDate: '2026-03-26T14:30:04.150Z',
    incidentKey: null,
  },
  // Tool 4: AskHumanToSendEmail (was ACTIVE in state 2 — now COMPLETED)
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.300Z',
    endDate: '2026-03-26T14:30:05.400Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: '6451799813685035',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AskHumanToSendEmail',
    elementName: 'Ask human to send email',
    type: 'USER_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.300Z',
    endDate: '2026-03-26T14:30:05.400Z',
    incidentKey: null,
  },
  // Tool 5: AI_Task_Agent (was ACTIVE in state 2 — now COMPLETED)
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.320Z',
    endDate: '2026-03-26T14:30:05.100Z',
    incidentKey: null,
  },
  {
    elementInstanceKey: MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'AI_Task_Agent',
    elementName: 'AI task agent',
    type: 'TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:04.320Z',
    endDate: '2026-03-26T14:30:05.100Z',
    incidentKey: null,
  },
  // User_Feedback — the now-ACTIVE element after the agent finished.
  {
    elementInstanceKey: '6451799813685050',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    elementId: 'User_Feedback',
    elementName: 'User Feedback',
    type: 'USER_TASK',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:05.600Z',
    endDate: null,
    incidentKey: null,
  },
];

export const MOCK_AGENT_ELEMENT_STATISTICS_COMPLETED = {
  items: [
    {
      elementId: 'StartEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'Gateway_0z6ctwk',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {elementId: 'AI_Agent', active: 0, canceled: 0, incidents: 0, completed: 1},
    {
      elementId: 'ListUsers',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'LoadUserByID',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'GetDateAndTime',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'AskHumanToSendEmail',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'AI_Task_Agent',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'User_Feedback',
      active: 1,
      canceled: 0,
      incidents: 0,
      completed: 0,
    },
  ],
};

export const MOCK_AGENT_SEQUENCE_FLOWS_COMPLETED = {
  items: [
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
      elementId: 'Flow_0pbzrme',
    },
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
      elementId: 'Flow_16otfp1',
    },
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
      elementId: 'Flow_0m7etfk',
    },
  ],
};

export const MOCK_AGENT_VARIABLES_COMPLETED: Variable[] = [
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_COMPLETED}-inputText`,
    name: 'inputText',
    value:
      '"Find the email address of user Leanne Graham and send her an invitation to the company offsite."',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
  },
];

export const MOCK_AGENT_JOBS_COMPLETED: Job[] = [
  {
    jobKey: '6451799813685011',
    type: 'io.camunda.agenticai:aiagent-job-worker:1',
    worker: 'ai-agent-worker-1',
    state: 'COMPLETED',
    kind: 'BPMN_ELEMENT',
    listenerEventType: 'UNSPECIFIED',
    retries: 3,
    isDenied: null,
    deniedReason: null,
    hasFailedWithRetriesLeft: false,
    errorMessage: null,
    errorCode: null,
    customHeaders: {},
    deadline: null,
    endTime: '2026-03-26T14:30:05.500Z',
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_COMPLETED,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_COMPLETED,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_COMPLETED,
    rootProcessInstanceKey: null,
    elementId: 'AI_Agent',
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_COMPLETED,
    creationTime: '2026-03-26T14:30:00.300Z',
    lastUpdateTime: null,
    tags: [],
    tenantId: '<default>',
  },
];

// State 4 — Multiple element instances. Two AI_Agent runs separated by a
// User_Feedback step that returned userSatisfied = false.

export const MOCK_AGENT_PROCESS_INSTANCE_MULTIPLE: ProcessInstance = {
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  processDefinitionName: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  processDefinitionVersion: 1,
  processDefinitionVersionTag: null,
  tenantId: '<default>',
  state: 'ACTIVE',
  startDate: '2026-03-26T14:30:00.000Z',
  endDate: null,
  hasIncident: false,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
};

export const MOCK_AGENT_PROCESS_DEFINITION_MULTIPLE: ProcessDefinition = {
  name: 'AI Agent Chat With Tools',
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  resourceName: 'ai-agent-chat-with-tools.bpmn',
  version: 1,
  versionTag: null,
  tenantId: '<default>',
  hasStartForm: false,
};

// Rebinding helpers — point cloned element-instances at the MULTIPLE keyspace.
type RebindMap = Record<string, string>;
const rebindElementInstances = (
  source: MockElementInstance[],
  processKey: string,
  definitionKey: string,
  keyRebinds: RebindMap,
): MockElementInstance[] =>
  source.map((el) => ({
    ...el,
    processInstanceKey: processKey,
    processDefinitionKey: definitionKey,
    elementInstanceKey:
      keyRebinds[el.elementInstanceKey] ?? el.elementInstanceKey,
    flowScopeKey: keyRebinds[el.flowScopeKey] ?? el.flowScopeKey,
  }));

const RUN_1_REBINDS: RebindMap = {
  [MOCK_AGENT_INSTANCE_KEY_COMPLETED]: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  [MOCK_AGENT_SUBPROCESS_KEY_COMPLETED]: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_1_KEY_COMPLETED]:
    MOCK_AGENT_INNER_INSTANCE_1_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_2_KEY_COMPLETED]:
    MOCK_AGENT_INNER_INSTANCE_2_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_3_KEY_COMPLETED]:
    MOCK_AGENT_INNER_INSTANCE_3_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_4_KEY_COMPLETED]:
    MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_1,
  [MOCK_AGENT_INNER_INSTANCE_5_KEY_COMPLETED]:
    MOCK_AGENT_INNER_INSTANCE_5_KEY_MULTIPLE_1,
  [MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_COMPLETED]:
    MOCK_AGENT_TASK_AGENT_INSTANCE_KEY_MULTIPLE_1,
  // Keep child element-instance keys disjoint from other scenarios.
  '6451799813685001': '7451799813685001', // StartEvent
  '6451799813685005': '7451799813685005', // Gateway pass 1
  '6451799813685020': '7451799813685020', // ListUsers
  '6451799813685025': '7451799813685025', // LoadUserByID
  '6451799813685030': '7451799813685030', // GetDateAndTime
  '6451799813685035': '7451799813685035', // AskHumanToSendEmail
};

const run1Cloned = rebindElementInstances(
  MOCK_AGENT_ELEMENT_INSTANCES_COMPLETED.filter(
    (el) =>
      // drop the PROCESS row; handled at top
      el.elementId !== MOCK_AGENT_DEFINITION_ID_COMPLETED &&
      // drop StartEvent_1 + Gateway_0z6ctwk; MULTIPLE adds its own pass-1 rows above
      el.elementId !== 'StartEvent_1' &&
      el.elementId !== 'Gateway_0z6ctwk' &&
      // drop User_Feedback ACTIVE row from COMPLETED (Fix 2); MULTIPLE manages its
      // own User_Feedback row (COMPLETED) added inline below.
      el.elementId !== 'User_Feedback',
  ),
  MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
  MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
  RUN_1_REBINDS,
);

// Run 2 — hand-written, matches the shorter Run-2 history:
// only the AskHumanToSendEmail tool is BPMN-resident (DraftEmailTemplate is
// agent-internal, no element-instance). Timestamps shifted to sit after
// User_Feedback (14:30:05.500Z) and Gateway pass 2 (14:30:14.100Z).
const run2Entries: MockElementInstance[] = [
  // AI_Agent #2 subprocess (ACTIVE)
  {
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'AI_Agent',
    elementName: 'AI Agent',
    type: 'AD_HOC_SUB_PROCESS',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:15.300Z',
    endDate: null,
    incidentKey: null,
  },
  // Tool activation: AskHumanToSendEmail — innerInstance wrapper (ACTIVE)
  {
    elementInstanceKey: MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_2,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'AI_Agent',
    elementName: 'AI Agent#innerInstance',
    type: 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:15.550Z',
    endDate: null,
    incidentKey: null,
  },
  // AskHumanToSendEmail user task (ACTIVE)
  {
    elementInstanceKey: '7451799813685135',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'AskHumanToSendEmail',
    elementName: 'Ask human to send email',
    type: 'USER_TASK',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INNER_INSTANCE_4_KEY_MULTIPLE_2,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:15.560Z',
    endDate: null,
    incidentKey: null,
  },
];

export const MOCK_AGENT_ELEMENT_INSTANCES_MULTIPLE: MockElementInstance[] = [
  // Top-level process
  {
    elementInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementName: 'AI Agent Chat With Tools',
    type: 'PROCESS',
    state: 'ACTIVE',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.000Z',
    endDate: null,
    incidentKey: null,
  },
  // StartEvent (just once)
  {
    elementInstanceKey: '7451799813685001',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'StartEvent_1',
    elementName: 'Task to perform received',
    type: 'START_EVENT',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.100Z',
    endDate: '2026-03-26T14:30:00.200Z',
    incidentKey: null,
  },
  // Gateway pass 1
  {
    elementInstanceKey: '7451799813685005',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'Gateway_0z6ctwk',
    elementName: null,
    type: 'EXCLUSIVE_GATEWAY',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:00.250Z',
    endDate: '2026-03-26T14:30:00.280Z',
    incidentKey: null,
  },
  // Run 1 — AI_Agent + inner instances + tools (all COMPLETED)
  ...run1Cloned,
  // User_Feedback — COMPLETED, userSatisfied = false
  {
    elementInstanceKey: MOCK_USER_FEEDBACK_KEY_MULTIPLE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'User_Feedback',
    elementName: 'User Feedback',
    type: 'USER_TASK',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:05.500Z',
    endDate: '2026-03-26T14:30:14.000Z',
    incidentKey: null,
  },
  // Gateway_1dcg4ha — "User satisfied?" gateway, COMPLETED (token routed back to AI_Agent)
  {
    elementInstanceKey: '7451799813685085',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'Gateway_1dcg4ha',
    elementName: 'User satisfied?',
    type: 'EXCLUSIVE_GATEWAY',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:14.050Z',
    endDate: '2026-03-26T14:30:14.080Z',
    incidentKey: null,
  },
  // Gateway pass 2 (after user-feedback loopback)
  {
    elementInstanceKey: '7451799813685006',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    elementId: 'Gateway_0z6ctwk',
    elementName: null,
    type: 'EXCLUSIVE_GATEWAY',
    state: 'COMPLETED',
    hasIncident: false,
    flowScopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    tenantId: '<default>',
    startDate: '2026-03-26T14:30:14.100Z',
    endDate: '2026-03-26T14:30:14.120Z',
    incidentKey: null,
  },
  // Run 2 — AI_Agent + inner instances + tools (active state)
  ...run2Entries,
];

export const MOCK_AGENT_ELEMENT_STATISTICS_MULTIPLE = {
  items: [
    {
      elementId: 'StartEvent_1',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'Gateway_0z6ctwk',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 2,
    },
    {elementId: 'AI_Agent', active: 1, canceled: 0, incidents: 0, completed: 1},
    {
      elementId: 'ListUsers',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'LoadUserByID',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'GetDateAndTime',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'AskHumanToSendEmail',
      active: 1,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'AI_Task_Agent',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'User_Feedback',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
    {
      elementId: 'Gateway_1dcg4ha',
      active: 0,
      canceled: 0,
      incidents: 0,
      completed: 1,
    },
  ],
};

export const MOCK_AGENT_SEQUENCE_FLOWS_MULTIPLE = {
  items: [
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
      elementId: 'Flow_0pbzrme',
    },
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
      elementId: 'Flow_16otfp1',
    },
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
      elementId: 'Flow_0m7etfk',
    },
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
      elementId: 'Flow_09y08ef',
    },
    {
      processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
      elementId: 'Flow_19gp461',
    },
  ],
};

export const MOCK_AGENT_VARIABLES_MULTIPLE: Variable[] = [
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_MULTIPLE}-inputText`,
    name: 'inputText',
    value:
      '"Find the email address of user Leanne Graham and send her an invitation to the company offsite."',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
  },
  {
    variableKey: `${MOCK_AGENT_INSTANCE_KEY_MULTIPLE}-userSatisfied`,
    name: 'userSatisfied',
    value: 'false',
    isTruncated: false,
    tenantId: '<default>',
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    scopeKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
  },
];

export const MOCK_AGENT_JOBS_MULTIPLE: Job[] = [
  {
    jobKey: '7451799813685011',
    type: 'io.camunda.agenticai:aiagent-job-worker:1',
    worker: 'ai-agent-worker-1',
    state: 'CREATED',
    kind: 'BPMN_ELEMENT',
    listenerEventType: 'UNSPECIFIED',
    retries: 3,
    isDenied: null,
    deniedReason: null,
    hasFailedWithRetriesLeft: false,
    errorMessage: null,
    errorCode: null,
    customHeaders: {},
    deadline: null,
    endTime: null,
    processDefinitionId: MOCK_AGENT_DEFINITION_ID_MULTIPLE,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_MULTIPLE,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_MULTIPLE,
    rootProcessInstanceKey: null,
    elementId: 'AI_Agent',
    elementInstanceKey: MOCK_AGENT_SUBPROCESS_KEY_MULTIPLE_2,
    creationTime: '2026-03-26T14:30:14.200Z',
    lastUpdateTime: null,
    tags: [],
    tenantId: '<default>',
  },
];

// ----- Flat-trace short-term demo -----

export const MOCK_AGENT_PROCESS_INSTANCE_FLAT = {
  ...MOCK_AGENT_PROCESS_INSTANCE_COMPLETED,
  processInstanceKey: MOCK_AGENT_INSTANCE_KEY_FLAT,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_FLAT,
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_FLAT,
};

export const MOCK_AGENT_PROCESS_DEFINITION_FLAT = {
  ...MOCK_AGENT_PROCESS_DEFINITION_COMPLETED,
  processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_FLAT,
  processDefinitionId: MOCK_AGENT_DEFINITION_ID_FLAT,
};

export const MOCK_AGENT_ELEMENT_INSTANCES_FLAT =
  MOCK_AGENT_ELEMENT_INSTANCES_COMPLETED.map((el) => ({
    ...el,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_FLAT,
    processDefinitionKey: MOCK_AGENT_DEFINITION_KEY_FLAT,
    // Re-point the outer agent subprocess instance key so the Details tab
    // resolves agent data for this scenario.
    elementInstanceKey:
      el.elementInstanceKey === MOCK_AGENT_SUBPROCESS_KEY_COMPLETED
        ? MOCK_AGENT_SUBPROCESS_KEY_FLAT
        : el.elementInstanceKey,
    flowScopeKey:
      el.flowScopeKey === MOCK_AGENT_SUBPROCESS_KEY_COMPLETED
        ? MOCK_AGENT_SUBPROCESS_KEY_FLAT
        : el.flowScopeKey,
  }));

export const MOCK_AGENT_ELEMENT_STATISTICS_FLAT =
  MOCK_AGENT_ELEMENT_STATISTICS_COMPLETED;

export const MOCK_AGENT_SEQUENCE_FLOWS_FLAT = {
  items: MOCK_AGENT_SEQUENCE_FLOWS_COMPLETED.items.map((it) => ({
    ...it,
    processInstanceKey: MOCK_AGENT_INSTANCE_KEY_FLAT,
  })),
};

export const MOCK_AGENT_VARIABLES_FLAT = MOCK_AGENT_VARIABLES_COMPLETED;
export const MOCK_AGENT_JOBS_FLAT = MOCK_AGENT_JOBS_COMPLETED;
