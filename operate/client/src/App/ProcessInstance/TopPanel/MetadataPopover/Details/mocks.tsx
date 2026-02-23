/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import type {
  ElementInstance,
  Job,
  ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {MemoryRouter} from 'react-router-dom';

const mockElementInstance: ElementInstance = {
  elementInstanceKey: '123456789',
  elementId: 'Task_1',
  elementName: 'Service Task',
  type: 'SERVICE_TASK',
  state: 'COMPLETED',
  startDate: '2023-01-15T10:00:00.000Z',
  endDate: '2023-01-15T10:05:00.000Z',
  processDefinitionId: 'process-def-1',
  processInstanceKey: '111222333',
  processDefinitionKey: '444555666',
  hasIncident: false,
  tenantId: '<default>',
};

const mockJob: Job = {
  jobKey: '555666777',
  processInstanceKey: '111222333',
  processDefinitionKey: '444555666',
  processDefinitionId: 'process-def-1',
  elementId: 'Task_1',
  elementInstanceKey: '123456789',
  type: 'httpService',
  worker: 'worker-1',
  retries: 3,
  deadline: '2023-01-15T10:10:00.000Z',
  customHeaders: {timeout: '30s'},
  state: 'CREATED',
  tenantId: '<default>',
  kind: 'BPMN_ELEMENT',
  listenerEventType: 'UNSPECIFIED',
  isDenied: false,
  deniedReason: '',
  hasFailedWithRetriesLeft: false,
  errorCode: '',
  errorMessage: '',
  endTime: '',
};

const mockCalledProcessInstance: ProcessInstance = {
  processInstanceKey: '987654321',
  processDefinitionId: 'called-process-def',
  processDefinitionKey: '888999000',
  processDefinitionName: 'Called Process',
  processDefinitionVersion: 1,
  state: 'ACTIVE',
  startDate: '2023-01-15T10:00:00.000Z',
  tenantId: '<default>',
  parentProcessInstanceKey: '111222333',
  parentElementInstanceKey: '123456789',
  hasIncident: false,
};

const mockBusinessObject: BusinessObject = {
  id: 'Task_1',
  name: 'Service Task',
  $type: 'bpmn:ServiceTask',
};

const mockJobWorkerUserTaskBusinessObject: BusinessObject = {
  id: 'UserTask_1',
  name: 'Job Worker User Task',
  $type: 'bpmn:UserTask',
};

const mockCamundaUserTaskBusinessObject: BusinessObject = {
  id: 'UserTask_2',
  name: 'Camunda User Task',
  $type: 'bpmn:UserTask',
  extensionElements: {
    values: [
      {
        $type: 'zeebe:userTask',
      },
    ],
  },
};

const TestWrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
  <MemoryRouter>
    <ProcessDefinitionKeyContext.Provider value="test-process-key">
      <QueryClientProvider client={getMockQueryClient()}>
        {children}
      </QueryClientProvider>
    </ProcessDefinitionKeyContext.Provider>
  </MemoryRouter>
);

export {
  TestWrapper,
  mockElementInstance,
  mockJob,
  mockCalledProcessInstance,
  mockBusinessObject,
  mockJobWorkerUserTaskBusinessObject,
  mockCamundaUserTaskBusinessObject,
};
