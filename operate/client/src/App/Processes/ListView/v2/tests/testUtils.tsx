/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react';
import {MemoryRouter} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {Paths} from 'modules/Routes';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelectionV2';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {batchModificationStore} from 'modules/stores/batchModification';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';

const MOCK_PROCESS_DEFINITION_ID = '2251799813685249';
const MOCK_PROCESS_ID = 'TestProcess';

const mockProcessInstancesV2: ProcessInstance[] = [
  {
    processInstanceKey: '1',
    processDefinitionKey: MOCK_PROCESS_DEFINITION_ID,
    processDefinitionId: MOCK_PROCESS_DEFINITION_ID,
    processDefinitionName: 'Test Process',
    processDefinitionVersion: 1,
    state: 'ACTIVE',
    startDate: '2023-01-01T00:00:00.000+0000',
    hasIncident: false,
    tenantId: '<default>',
  },
  {
    processInstanceKey: '2',
    processDefinitionKey: MOCK_PROCESS_DEFINITION_ID,
    processDefinitionId: MOCK_PROCESS_DEFINITION_ID,
    processDefinitionName: 'Test Process',
    processDefinitionVersion: 1,
    state: 'ACTIVE',
    startDate: '2023-01-01T00:00:00.000+0000',
    hasIncident: true,
    tenantId: '<default>',
  },
  {
    processInstanceKey: '3',
    processDefinitionKey: MOCK_PROCESS_DEFINITION_ID,
    processDefinitionId: MOCK_PROCESS_DEFINITION_ID,
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
    processDefinitionKey: MOCK_PROCESS_DEFINITION_ID,
    processDefinitionId: MOCK_PROCESS_DEFINITION_ID,
    processDefinitionName: 'Test Process',
    processDefinitionVersion: 1,
    state: 'COMPLETED',
    startDate: '2023-01-01T00:00:00.000+0000',
    endDate: '2023-01-02T00:00:00.000+0000',
    hasIncident: false,
    tenantId: '<default>',
  },
];

const getProcessInstance = (
  state: ProcessInstance['state'],
  mockData: ProcessInstance[] = mockProcessInstancesV2,
): ProcessInstance => {
  const instance = mockData.find((instance) => instance.state === state);
  if (instance === undefined) {
    throw new Error(
      `No instance with state "${state}" found in mock data. Available states: ${mockData.map((i) => i.state).join(', ')}`,
    );
  }
  return instance;
};

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

type V2TestWrapperOptions = {
  initialPath?: string;
  processDefinitionKey?: string;
  withProcessInstances?: ProcessInstance[];
  withTestButtons?: boolean;
};

function createV2TestWrapper(
  options: V2TestWrapperOptions = {},
): React.FC<{children?: React.ReactNode}> {
  const {
    initialPath = Paths.processes(),
    processDefinitionKey = MOCK_PROCESS_DEFINITION_ID,
    withProcessInstances,
    withTestButtons = false,
  } = options;

  const Wrapper: React.FC<{children?: React.ReactNode}> = observer(
    ({children}) => {
      useEffect(() => {
        processInstancesSelectionStore.init();

        if (withProcessInstances) {
          setupSelectionStoreWithInstances(withProcessInstances);
        }

        return () => {
          processInstancesSelectionStore.reset();
          processInstanceMigrationStore.reset();
          batchModificationStore.reset();
        };
      }, []);

      return (
        <ProcessDefinitionKeyContext.Provider value={processDefinitionKey}>
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
        </ProcessDefinitionKeyContext.Provider>
      );
    },
  );

  return Wrapper;
}

function createSimpleV2TestWrapper(
  initialPath: string = Paths.processes(),
): React.FC<{children?: React.ReactNode}> {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        processInstancesSelectionStore.reset();
        batchModificationStore.reset();
      };
    }, []);

    return (
      <QueryClientProvider client={getMockQueryClient()}>
        <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };

  return Wrapper;
}

export {
  createSimpleV2TestWrapper,
  createV2TestWrapper,
  setupSelectionStoreWithInstances,
  getProcessInstance,
  mockProcessInstancesV2,
  MOCK_PROCESS_DEFINITION_ID,
  MOCK_PROCESS_ID,
};
