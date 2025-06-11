/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen, waitFor} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  createInstance,
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {useEffect, act} from 'react';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessInstanceListeners} from 'modules/mocks/api/processInstances/fetchProcessInstanceListeners';
import {noListeners} from 'modules/mocks/mockProcessInstanceListeners';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {init} from 'modules/utils/flowNodeMetadata';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));
jest.mock('modules/feature-flags', () => ({
  ...jest.requireActual('modules/feature-flags'),
  IS_PROCESS_INSTANCE_V2_ENABLED: false,
}));

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
        variablesStore.reset();
        flowNodeSelectionStore.reset();
        flowNodeMetaDataStore.reset();
        modificationsStore.reset();
        processInstanceDetailsStore.reset();
      };
    }, []);

    return (
      <ProcessDefinitionKeyContext.Provider value="123">
        <QueryClientProvider client={getMockQueryClient()}>
          <MemoryRouter initialEntries={initialEntries}>
            <Routes>
              <Route path={Paths.processInstance()} element={children} />
            </Routes>
          </MemoryRouter>
        </QueryClientProvider>
      </ProcessDefinitionKeyContext.Provider>
    );
  };
  return Wrapper;
};

describe('VariablePanel', () => {
  beforeEach(() => {
    const statistics = [
      {
        elementId: 'TEST_FLOW_NODE',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        elementId: 'Activity_0qtp1k6',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ];

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });

    mockFetchVariables().withSuccess([createVariable()]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    init('process-instance', statistics);
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );
  });

  afterEach(async () => {
    jest.clearAllMocks();
    jest.clearAllTimers();
    await new Promise(process.nextTick);
  });

  it.each([true, false])(
    'should show multiple scope placeholder when multiple nodes are selected - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      mockFetchFlowNodeMetadata().withSuccess({
        ...singleInstanceMetadata,
        flowNodeInstanceId: null,
        instanceCount: 2,
        instanceMetadata: null,
      });

      render(<VariablePanel />, {wrapper: getWrapper()});

      await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeInTheDocument();
      mockFetchProcessInstanceListeners().withSuccess(noListeners);

      act(() => {
        flowNodeSelectionStore.setSelection({
          flowNodeId: 'TEST_FLOW_NODE',
        });
      });

      expect(
        await screen.findByText(
          'To view the Variables, select a single Flow Node Instance in the Instance History.',
        ),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();
    },
  );

  it.each([true, false])(
    'should show failed placeholder if server error occurs while fetching variables - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      render(<VariablePanel />, {wrapper: getWrapper()});

      await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeInTheDocument();

      mockFetchVariables().withServerError();

      act(() => {
        variablesStore.fetchVariables({
          fetchType: 'initial',
          instanceId: 'invalid_instance',
          payload: {pageSize: 10, scopeId: '1'},
        });
      });

      expect(
        await screen.findByText('Variables could not be fetched'),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();
    },
  );

  it.each([true, false])(
    'should show failed placeholder if network error occurs while fetching variables - modification mode: %p',
    async (enableModificationMode) => {
      const consoleErrorMock = jest
        .spyOn(global.console, 'error')
        .mockImplementation();

      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      render(<VariablePanel />, {wrapper: getWrapper()});

      await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));
      expect(
        screen.getByRole('button', {name: /add variable/i}),
      ).toBeInTheDocument();

      mockFetchVariables().withNetworkError();

      act(() => {
        variablesStore.fetchVariables({
          fetchType: 'initial',
          instanceId: 'invalid_instance',
          payload: {pageSize: 10, scopeId: '1'},
        });
      });

      expect(
        await screen.findByText('Variables could not be fetched'),
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();

      consoleErrorMock.mockRestore();
    },
  );
});
