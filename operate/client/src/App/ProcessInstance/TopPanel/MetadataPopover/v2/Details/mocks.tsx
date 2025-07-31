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
import {type V2MetaDataDto} from '../types';

const baseMetaData: V2MetaDataDto = {
  flowNodeInstanceId: '123456789',
  flowNodeId: 'Task_1',
  flowNodeType: 'SERVICE_TASK',
  instanceCount: 1,
  instanceMetadata: {
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
    incidentKey: undefined,
    tenantId: '<default>',
    calledProcessInstanceId: '987654321',
    calledProcessDefinitionName: 'Called Process',
    calledDecisionInstanceId: null,
    calledDecisionDefinitionName: null,
    jobRetries: 3,
    flowNodeInstanceId: '123456789',
    flowNodeId: 'Task_1',
    flowNodeType: 'SERVICE_TASK',
    eventId: undefined,
    jobType: 'httpService',
    jobWorker: 'worker-1',
    jobDeadline: '2023-01-15T10:10:00.000Z',
    jobCustomHeaders: {timeout: '30s'},
    jobKey: '555666777',
  },
  incident: null,
  incidentCount: 0,
};

const TestWrapper: React.FC<{children: React.ReactNode}> = ({children}) => (
  <ProcessDefinitionKeyContext.Provider value="test-process-key">
    <QueryClientProvider client={getMockQueryClient()}>
      {children}
    </QueryClientProvider>
  </ProcessDefinitionKeyContext.Provider>
);

export {TestWrapper, baseMetaData};
