/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {MemoryRouter} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelectionV2';
import {batchModificationStore} from 'modules/stores/batchModification';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {createProcessDefinition} from 'modules/testUtils';
import {SelectedProcessDefinitionContext} from 'App/Processes/ListView/selectedProcessDefinitionContext';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';

const PROCESS_DEFINITION_ID = 'eventBasedGatewayProcess';
const PROCESS_DEFINITION_KEY = '2251799813685249';

const selectedProcessDefinition = createProcessDefinition({
  processDefinitionId: PROCESS_DEFINITION_ID,
  version: 1,
  processDefinitionKey: PROCESS_DEFINITION_KEY,
});

const mockProcessInstancesV2: ProcessInstance[] = [
  {
    processInstanceKey: '1',
    processDefinitionKey: PROCESS_DEFINITION_KEY,
    processDefinitionId: PROCESS_DEFINITION_ID,
    processDefinitionName: 'Test Process',
    processDefinitionVersion: 1,
    state: 'ACTIVE',
    startDate: '2023-01-01T00:00:00.000+0000',
    hasIncident: false,
    tenantId: '<default>',
  },
  {
    processInstanceKey: '2',
    processDefinitionKey: PROCESS_DEFINITION_KEY,
    processDefinitionId: PROCESS_DEFINITION_ID,
    processDefinitionName: 'Test Process',
    processDefinitionVersion: 1,
    state: 'ACTIVE',
    startDate: '2023-01-01T00:00:00.000+0000',
    hasIncident: true,
    tenantId: '<default>',
  },
  {
    processInstanceKey: '3',
    processDefinitionKey: PROCESS_DEFINITION_KEY,
    processDefinitionId: PROCESS_DEFINITION_ID,
    processDefinitionName: 'Test Process',
    processDefinitionVersion: 1,
    state: 'TERMINATED',
    startDate: '2023-01-01T00:00:00.000+0000',
    endDate: '2023-01-02T00:00:00.000+0000',
    hasIncident: false,
    tenantId: '<default>',
  },
  {
    processInstanceKey: '4',
    processDefinitionKey: PROCESS_DEFINITION_KEY,
    processDefinitionId: PROCESS_DEFINITION_ID,
    processDefinitionName: 'Test Process',
    processDefinitionVersion: 1,
    state: 'COMPLETED',
    startDate: '2023-01-01T00:00:00.000+0000',
    endDate: '2023-01-02T00:00:00.000+0000',
    hasIncident: false,
    tenantId: '<default>',
  },
];

const setupSelectionStoreWithInstances = (
  instances: ProcessInstance[],
): void => {
  const visibleIds = instances.map((instance) => instance.processInstanceKey);
  const visibleRunningIds = instances
    .filter((instance) => instance.state === 'ACTIVE' || instance.hasIncident)
    .map((instance) => instance.processInstanceKey);

  processInstancesSelectionStore.setRuntime({
    totalProcessInstancesCount: instances.length,
    visibleIds,
    visibleRunningIds,
  });
};

const getProcessInstance = (
  state: ProcessInstance['state'],
  mockData: ProcessInstance[],
): ProcessInstance => {
  const instance = mockData.find((instance) => instance.state === state);
  if (instance === undefined) {
    throw new Error(
      `No instance with state "${state}" found in mock data. Available states: ${mockData.map((i) => i.state).join(', ')}`,
    );
  }
  return instance;
};

function createWrapper(
  options: {
    initialPath?: string;
    withTestButtons?: boolean;
  } = {},
): React.FC<{children?: React.ReactNode}> {
  const {initialPath = '/processes', withTestButtons = false} = options;

  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      processInstancesSelectionStore.init();

      return () => {
        processInstancesSelectionStore.reset();
        batchModificationStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider
        value={selectedProcessDefinition.processDefinitionKey}
      >
        <SelectedProcessDefinitionContext.Provider
          value={selectedProcessDefinition}
        >
          <QueryClientProvider client={getMockQueryClient()}>
            <MemoryRouter initialEntries={[initialPath]}>
              {children}
              {withTestButtons && (
                <>
                  <button
                    onClick={
                      processInstancesSelectionStore.selectAllProcessInstances
                    }
                  >
                    Select all instances
                  </button>
                  <button onClick={batchModificationStore.enable}>
                    Enter batch modification mode
                  </button>
                  <button onClick={batchModificationStore.reset}>
                    Exit batch modification mode
                  </button>
                </>
              )}
            </MemoryRouter>
          </QueryClientProvider>
        </SelectedProcessDefinitionContext.Provider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };

  return Wrapper;
}

export {
  PROCESS_DEFINITION_ID,
  PROCESS_DEFINITION_KEY,
  mockProcessInstancesV2,
  setupSelectionStoreWithInstances,
  getProcessInstance,
  createWrapper,
};
