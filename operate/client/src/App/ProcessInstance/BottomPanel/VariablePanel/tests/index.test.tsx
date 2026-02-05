/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {VariablePanel} from '../index';
import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
  within,
} from 'modules/testing-library';
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
import {notificationsStore} from 'modules/stores/notifications';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {type ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {init} from 'modules/utils/flowNodeMetadata';
import {mockFetchProcessInstance} from 'modules/mocks/api/v2/processInstances/fetchProcessInstance';
import {mockFetchProcessInstance as mockFetchProcessInstanceDeprecated} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockSearchVariables} from 'modules/mocks/api/v2/variables/searchVariables';
import {mockSearchJobs} from 'modules/mocks/api/v2/jobs/searchJobs';
import {mockUpdateElementInstanceVariables} from 'modules/mocks/api/v2/elementInstances/updateElementInstanceVariables';

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

describe('VariablePanel', () => {
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

  beforeEach(() => {
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

    mockFetchFlownodeInstancesStatistics().withSuccess({
      items: statistics,
    });
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
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );
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

  afterEach(() => {
    vi.clearAllTimers();
  });

  it('should render variables', async () => {
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
  });

  it('should add new variable', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});
    mockUpdateElementInstanceVariables(':instance_id').withDelay(null);

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /add variable/i,
        }),
      ).toBeEnabled(),
    );

    await user.click(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    );

    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'foo',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '"bar"',
    );

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    mockSearchVariables().withSuccess({
      items: [
        createvariable(),
        createvariable({
          variableKey: '2251799813725337-foo',
          name: 'foo',
          value: '"bar"',
        }),
      ],
      page: {totalItems: 2},
    });
    mockSearchVariables().withSuccess({
      items: [
        createvariable(),
        createvariable({
          variableKey: '2251799813725337-foo',
          name: 'foo',
          value: '"bar"',
        }),
      ],
      page: {totalItems: 2},
    });

    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    expect(screen.getByTestId('full-variable-loader')).toBeInTheDocument();

    const withinVariablesList = within(screen.getByTestId('variables-list'));
    expect(
      withinVariablesList.queryByTestId('variable-foo'),
    ).not.toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.queryByTestId('full-variable-loader'),
    );

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'success',
      title: 'Variable added',
    });

    expect(
      await withinVariablesList.findByTestId('variable-foo'),
    ).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it.skip('should remove pending variable if scope id changes', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '2251799813686104',
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /add variable/i,
        }),
      ).toBeEnabled(),
    );

    await user.click(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'foo',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '"bar"',
    );

    mockSearchVariables().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockUpdateElementInstanceVariables(':instance_id').withSuccess(null);

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );
    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    expect(screen.getByTestId('full-variable-loader')).toBeInTheDocument();

    mockSearchVariables().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'TEST_FLOW_NODE',
        flowNodeInstanceId: '2',
      });
    });

    expect(screen.getByTestId('variables-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('variables-spinner'),
    );
    expect(
      screen.queryByTestId('full-variable-loader'),
    ).not.toBeInTheDocument();

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();

    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('should display validation error if backend validation fails while adding variable', async () => {
    vi.useFakeTimers({shouldAdvanceTime: true});

    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {totalItems: 1},
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /add variable/i,
        }),
      ).toBeEnabled(),
    );

    await user.click(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    );
    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      'foo',
    );
    await user.type(
      screen.getByRole('textbox', {
        name: /value/i,
      }),
      '"bar"',
    );

    mockUpdateElementInstanceVariables('instance_id').withServerError(400);

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );
    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );

    await waitFor(() =>
      expect(
        screen.queryByTestId('full-variable-loader'),
      ).not.toBeInTheDocument(),
    );

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: 'error',
        title: 'Variable could not be saved',
      }),
    );
  });

  it.skip('should select correct tab when navigating between flow nodes', async () => {
    mockFetchProcessInstance().withSuccess(mockProcessInstance);
    mockSearchVariables().withSuccess({
      items: [createvariable()],
      page: {totalItems: 1},
    });

    const {user} = render(
      <VariablePanel setListenerTabVisibility={vi.fn()} />,
      {wrapper: getWrapper()},
    );
    await waitFor(() => {
      expect(screen.getByTestId('variables-list')).toBeInTheDocument();
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [createvariable({name: 'test2'})],
      page: {totalItems: 1},
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockFetchProcessDefinitionXml().withSuccess('');

    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'Activity_0qtp1k6',
        flowNodeInstanceId: '2',
      });
    });

    expect(await screen.findByText('test2')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Input Mappings'}));

    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockSearchVariables().withSuccess({
      items: [createvariable({name: 'test2'})],
      page: {totalItems: 1},
    });
    mockFetchProcessDefinitionXml().withSuccess('');

    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'Event_0bonl61',
      });
    });

    await waitFor(() =>
      expect(flowNodeSelectionStore.state.selection?.flowNodeId).toBe(
        'Event_0bonl61',
      ),
    );

    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    mockSearchVariables().withSuccess({
      items: [createvariable({name: 'test2'})],
      page: {totalItems: 1},
    });
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    act(() => {
      flowNodeSelectionStore.clearSelection();
    });

    expect(
      screen.queryByText('No Input Mappings defined'),
    ).not.toBeInTheDocument();
    expect(screen.getByRole('tab', {name: 'Variables'})).toBeInTheDocument();
    expect(
      screen.queryByRole('tab', {name: 'Input Mappings'}),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('tab', {name: 'Output Mappings'}),
    ).not.toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockSearchVariables().withSuccess({items: [], page: {totalItems: 0}});
    mockSearchJobs().withSuccess({items: [], page: {totalItems: 0}});

    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'StartEvent_1',
      });
    });

    expect(
      await screen.findByText('No Input Mappings defined'),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('heading', {name: 'Variables'}),
    ).not.toBeInTheDocument();

    expect(screen.getByRole('tab', {name: 'Variables'})).toBeInTheDocument();
    expect(
      screen.getByRole('tab', {name: 'Input Mappings'}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('tab', {name: 'Output Mappings'}),
    ).toBeInTheDocument();
  });
});
