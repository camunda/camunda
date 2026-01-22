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
import {
  type GetProcessInstanceStatisticsResponseBody,
  type ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {init as initFlowNodeMetadata} from 'modules/utils/flowNodeMetadata';
import {selectFlowNode} from 'modules/utils/flowNodeSelection';
import {cancelAllTokens} from 'modules/utils/modifications';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
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

describe('VariablePanel', () => {
  beforeEach(() => {
    const mockProcessInstanceDeprecated = createInstance();

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
    mockFetchProcessInstanceDeprecated().withSuccess(
      mockProcessInstanceDeprecated,
    );
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

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    initFlowNodeMetadata('process-instance', statistics);
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );
  });

  it('should be readonly if root node is selected in modification mode', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
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
      ],
    };
    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);
    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: null,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    modificationsStore.enableModificationMode();

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper([Paths.processInstance('processInstanceId123')]),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
  });

  it('should display readonly state for existing node if cancel modification is applied on the flow node and one new token is added', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
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
      ],
    };
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });
    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);
    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '2251799813695856',
      instanceCount: 1,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    act(() => {
      modificationsStore.enableModificationMode();
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'Activity_0qtp1k6',
          flowNodeInstanceId: '2251799813695856',
        },
      );
    });

    await waitFor(() =>
      expect(flowNodeMetaDataStore.state.metaData).toEqual({
        ...singleInstanceMetadata,
        flowNodeInstanceId: '2251799813695856',
        instanceCount: 1,
        instanceMetadata: {
          ...singleInstanceMetadata.instanceMetadata!,
          endDate: null,
        },
      }),
    );

    // initial state
    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    act(() => {
      cancelAllTokens('Activity_0qtp1k6', 1, 1, {});
    });

    await waitFor(() => {
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();
    });

    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    // add a new token
    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'Activity_0qtp1k6',
            name: 'Flow Node with running tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-new-scope-id',
          parentScopeIds: {},
        },
      });
    });

    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();
  });

  it('should display readonly state for existing node if it has finished state and one new token is added on the same flow node', async () => {
    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '2251799813695856',
      instanceCount: 1,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: MOCK_TIMESTAMP,
      },
    });
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });

    modificationsStore.enableModificationMode();

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'Activity_0qtp1k6',
          flowNodeInstanceId: '2251799813695856',
        },
      );
    });

    await waitFor(() =>
      expect(flowNodeMetaDataStore.state.metaData).toEqual({
        ...singleInstanceMetadata,
        flowNodeInstanceId: '2251799813695856',
        instanceCount: 1,
        instanceMetadata: {
          ...singleInstanceMetadata.instanceMetadata!,
          endDate: '2018-12-12 00:00:00',
        },
      }),
    );

    // initial state
    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    // add one new token
    await act(async () => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'Activity_0qtp1k6',
            name: 'Flow Node with running tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'new-scope',
          parentScopeIds: {},
        },
      });
    });

    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();
  });

  it('should be readonly if flow node has variables and running instances', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          elementId: 'TEST_FLOW_NODE',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          elementId: 'Activity_0qtp1k6',
          active: 1,
          canceled: 0,
          incidents: 0,
          completed: 0,
        },
      ],
    };
    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);
    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: null,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    act(() => {
      modificationsStore.enableModificationMode();
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [
        createvariable({
          name: 'some-other-variable',
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [
        createvariable({
          name: 'some-other-variable',
        }),
      ],
      page: {
        totalItems: 1,
      },
    });

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '9007199254742797',
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'Activity_0qtp1k6',
        },
      );
    });

    // initial state
    expect(await screen.findByText('some-other-variable')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByTestId('edit-variable-value')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();

    act(() => {
      cancelAllTokens('Activity_0qtp1k6', 1, 1, {});
    });

    await waitFor(() => {
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();
    });
    expect(screen.getByText('some-other-variable')).toBeInTheDocument();

    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
  });

  it('should be readonly if flow node has variables but no running instances', async () => {
    mockFetchProcessInstance().withSuccess({
      ...mockProcessInstance,
      state: 'COMPLETED',
    });
    modificationsStore.enableModificationMode();
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });

    render(<VariablePanel setListenerTabVisibility={vi.fn()} />, {
      wrapper: getWrapper(),
    });
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchVariables().withSuccess({
      items: [
        createvariable({
          name: 'some-other-variable',
        }),
      ],
      page: {
        totalItems: 1,
      },
    });
    mockSearchVariables().withSuccess({
      items: [
        createvariable({
          name: 'some-other-variable',
        }),
      ],
      page: {
        totalItems: 1,
      },
    });

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '9007199254742797',
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: '2022-09-15T12:44:45.406+0000',
      },
    });

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'Activity_0qtp1k6',
        },
      );
    });

    expect(await screen.findByText('some-other-variable')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();
  });
});
