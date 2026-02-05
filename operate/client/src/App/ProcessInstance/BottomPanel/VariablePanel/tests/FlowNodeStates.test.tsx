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
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {init as initFlowNodeMetadata} from 'modules/utils/flowNodeMetadata';
import {cancelAllTokens} from 'modules/utils/modifications';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {
  init as initFlowNodeSelection,
  selectFlowNode,
} from 'modules/utils/flowNodeSelection';
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

describe.skip('VariablePanel', () => {
  beforeEach(() => {
    const mockProcessInstanceDeprecated = createInstance();

    mockFetchProcessInstance().withSuccess(mockProcessInstance);
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

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {totalItems: 1},
    });

    initFlowNodeMetadata('instance_id', statistics);
    initFlowNodeSelection(
      {flowNodeId: 'Activity_0qtp1k6', flowNodeInstanceId: 'instance_id'},
      'instance_id',
      true,
    );
  });

  it('should display correct state for a flow node that has only one running token on it', async () => {
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
    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '2251799813695856',
      instanceCount: 1,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

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
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'Activity_0qtp1k6',
        },
      );
    });

    // initial state
    act(() => {
      cancelAllTokens('Activity_0qtp1k6', 1, 1, {});
    });

    await waitFor(async () => {
      expect(
        await screen.findByText('The Flow Node has no Variables'),
      ).toBeInTheDocument();
    });

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
      await screen.findByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
      ),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    // remove cancel modification
    act(() => {
      modificationsStore.removeFlowNodeModification({
        operation: 'CANCEL_TOKEN',
        flowNode: {
          id: 'Activity_0qtp1k6',
          name: 'Flow Node with running tokens',
        },
        affectedTokenCount: 1,
        visibleAffectedTokenCount: 1,
      });
    });

    expect(
      screen.getByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
      ),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '2251799813695856',
      instanceCount: 1,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
        startDate: '2018-12-12 00:00:00',
      },
    });

    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    // select existing scope
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

    expect(
      screen.queryByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
      ),
    ).not.toBeInTheDocument();

    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    // select new scope
    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'Activity_0qtp1k6',
          flowNodeInstanceId: 'some-new-scope-id',
          isPlaceholder: true,
        },
      );
    });

    expect(
      screen.queryByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
      ),
    ).not.toBeInTheDocument();

    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
  });

  it('should display correct state for a flow node that has no running or finished tokens on it', async () => {
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
    modificationsStore.enableModificationMode();

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'flowNode-without-running-tokens',
        },
      );
    });

    // initial state
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('testVariableName')).not.toBeInTheDocument();
    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    // mocks for variables query with the new scope ID
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    // one 'add token' modification is created
    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'flowNode-without-running-tokens',
            name: 'Flow Node without running tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-new-scope-id',
          parentScopeIds: {
            'another-flownode-without-any-tokens': 'some-new-parent-scope-id',
          },
        },
      });
    });

    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    mockFetchProcessDefinitionXml().withSuccess('');

    // go to input mappings and back, see the correct state
    await user.click(screen.getByRole('tab', {name: 'Input Mappings'}));
    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    await user.click(screen.getByRole('tab', {name: 'Variables'}));
    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();

    // second 'add token' modification is created
    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'flowNode-without-running-tokens',
            name: 'Flow Node without running tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-new-scope-id-2',
          parentScopeIds: {},
        },
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

    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    // select only one of the scopes
    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'flowNode-without-running-tokens',
          flowNodeInstanceId: 'some-new-scope-id-1',
          isPlaceholder: true,
        },
      );
    });

    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    // select new parent scope
    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'another-flownode-without-any-tokens',
          flowNodeInstanceId: 'some-new-parent-scope-id',
          isPlaceholder: true,
        },
      );
    });

    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
  });

  it('should display correct state for a flow node that has only one finished token on it', async () => {
    const statistics = [
      {
        elementId: 'StartEvent_1',
        active: 1,
        canceled: 0,
        incidents: 0,
        completed: 0,
      },
      {
        elementId: 'Activity_0qtp1k6',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
    ];

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: null,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: '2022-09-08T12:44:45.406+0000',
      },
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
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {
        totalItems: 1,
      },
    });

    modificationsStore.enableModificationMode();

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
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
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'Activity_0qtp1k6',
        },
      );
    });

    await waitFor(() =>
      expect(flowNodeMetaDataStore.state.metaData).toEqual({
        ...singleInstanceMetadata,
        instanceMetadata: {
          ...singleInstanceMetadata.instanceMetadata!,
          endDate: '2018-12-12 00:00:00',
        },
      }),
    );

    await waitFor(() =>
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument(),
    );
    expect(
      await screen.findByText(/the flow node has no variables/i),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    // mocks for variables query with the new scope ID
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });
    mockSearchVariables().withSuccess({
      items: [],
      page: {
        totalItems: 0,
      },
    });

    // one 'add token' modification is created
    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'Activity_0qtp1k6',
            name: 'Flow Node with finished tokens',
          },
          affectedTokenCount: 1,
          visibleAffectedTokenCount: 1,
          scopeId: 'some-new-scope-id',
          parentScopeIds: {},
        },
      });
    });

    expect(
      await screen.findByText(
        /to view the variables, select a single flow node instance in the instance history/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Variables'}));
    await waitFor(() =>
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument(),
    );
    expect(
      await screen.findByText(
        /to view the variables, select a single flow node instance in the instance history/i,
      ),
    ).toBeInTheDocument();

    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    // select only one of the scopes
    mockSearchVariables().withSuccess({items: [], page: {totalItems: 0}});

    act(() => {
      selectFlowNode(
        {},
        {
          flowNodeId: 'Activity_0qtp1k6',
          flowNodeInstanceId: 'some-new-scope-id',
          isPlaceholder: true,
        },
      );
    });

    await waitFor(() =>
      expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument(),
    );

    expect(
      await screen.findByText(/the flow node has no variables/i),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
  });
});
