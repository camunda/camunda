/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {render, screen} from 'modules/testing-library';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  createInstance,
  createvariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {useEffect, act} from 'react';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {init} from 'modules/utils/flowNodeMetadata';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';

vi.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: vi.fn(() => () => {}),
  },
}));

const getWrapper = (
  initialEntries: React.ComponentProps<
    typeof MemoryRouter
  >['initialEntries'] = [Paths.processInstance('1')],
) => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => {
      return () => {
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

describe.skip('VariablePanel', () => {
  beforeEach(() => {
    const mockProcessInstance: ProcessInstance = {
      processInstanceKey: 'instance_id',
      state: 'ACTIVE',
      startDate: '2018-06-21',
      processDefinitionKey: '2',
      processDefinitionVersion: 1,
      processDefinitionId: 'someKey',
      tenantId: '<default>',
      processDefinitionName: 'someProcessName',
      hasIncident: false,
    };

    const mockProcessInstanceDeprecated = createInstance();

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    const statistics = [
      {
        elementId: 'TEST_FLOW_NODE',
        active: 1,
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

    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    init('process-instance', statistics);
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );
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

      render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
        wrapper: getWrapper(),
      });

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

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

      render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
        wrapper: getWrapper(),
      });

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      mockSearchVariables().withServerError();
      mockSearchVariables().withServerError();

      act(() => {
        flowNodeSelectionStore.setSelection({
          flowNodeId: 'TEST_FLOW_NODE',
          flowNodeInstanceId: '2',
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
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
        wrapper: getWrapper(),
      });

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      mockSearchVariables().withNetworkError();

      act(() => {
        flowNodeSelectionStore.setSelection({
          flowNodeId: 'TEST_FLOW_NODE',
          flowNodeInstanceId: '2',
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
    'should show failed placeholder if network error occurs while fetching process instance flow-node metadata - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
        wrapper: getWrapper(),
      });

      expect(await screen.findByTestId('variables-list')).toBeInTheDocument();

      mockFetchFlowNodeMetadata().withNetworkError();

      act(() => {
        flowNodeSelectionStore.setSelection({
          flowNodeId: 'TEST_FLOW_NODE',
          flowNodeInstanceId: '2',
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
});
