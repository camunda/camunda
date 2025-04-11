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
import {variablesStore} from 'modules/stores/variables';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {flowNodeMetaDataStore} from 'modules/stores/flowNodeMetaData';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {
  createBatchOperation,
  createInstance,
  createOperation,
  createVariable,
  mockProcessWithInputOutputMappingsXML,
} from 'modules/testUtils';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockGetOperation} from 'modules/mocks/api/getOperation';
import * as operationApi from 'modules/api/getOperation';
import {useEffect, act} from 'react';
import {Paths} from 'modules/Routes';
import {notificationsStore} from 'modules/stores/notifications';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchFlownodeInstancesStatistics} from 'modules/mocks/api/v2/flownodeInstances/fetchFlownodeInstancesStatistics';
import {GetProcessInstanceStatisticsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {ProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {mockFetchProcessInstanceListeners} from 'modules/mocks/api/processInstances/fetchProcessInstanceListeners';
import {noListeners} from 'modules/mocks/mockProcessInstanceListeners';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';

const getOperationSpy = jest.spyOn(operationApi, 'getOperation');

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

jest.mock('modules/feature-flags', () => ({
  ...jest.requireActual('modules/feature-flags'),
  IS_FLOWNODE_INSTANCE_STATISTICS_V2_ENABLED: true,
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
        processInstanceDetailsStatisticsStore.reset();
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
    mockFetchProcessInstanceDetailStatistics().withSuccess([
      {
        activityId: 'TEST_FLOW_NODE',
        active: 0,
        canceled: 0,
        incidents: 0,
        completed: 1,
      },
      {
        activityId: 'Activity_0qtp1k6',
        active: 0,
        canceled: 0,
        incidents: 1,
        completed: 0,
      },
    ]);

    mockFetchVariables().withSuccess([createVariable()]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      }),
    );

    processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      'instance_id',
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

  it('should render variables', async () => {
    render(<VariablePanel />, {wrapper: getWrapper()});

    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
  });

  it('should add new variable', async () => {
    jest.useFakeTimers();

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
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

    mockFetchVariables().withSuccess([createVariable()]);

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    mockFetchVariables().withSuccess([
      createVariable(),
      createVariable({
        id: '2251799813725337-foo',
        name: 'foo',
        value: '"bar"',
        isFirst: false,
        sortValues: ['foo'],
      }),
    ]);

    mockApplyOperation().withSuccess(
      createBatchOperation({id: 'batch-operation-id'}),
    );

    mockGetOperation().withSuccess([createOperation({state: 'COMPLETED'})]);

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

    expect(
      within(screen.getByTestId('foo')).getByTestId(
        'variable-operation-spinner',
      ),
    ).toBeInTheDocument();

    const withinVariablesList = within(screen.getByTestId('variables-list'));
    expect(withinVariablesList.queryByTestId('foo')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId(
        'variable-operation-spinner',
      ),
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

    expect(getOperationSpy).toHaveBeenCalledWith('batch-operation-id');

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should remove pending variable if scope id changes', async () => {
    jest.useFakeTimers();

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '2251799813686104',
      instanceCount: 1,
    });

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
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

    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);
    mockApplyOperation().withSuccess(
      createBatchOperation({id: 'batch-operation-id'}),
    );

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

    expect(
      within(screen.getByTestId('foo')).getByTestId(
        'variable-operation-spinner',
      ),
    ).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'TEST_FLOW_NODE',
        flowNodeInstanceId: '2',
      });
    });

    expect(await screen.findByTestId('variables-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(() =>
      screen.getByTestId('variables-spinner'),
    );
    expect(
      screen.queryByTestId('variable-operation-spinner'),
    ).not.toBeInTheDocument();

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display validation error if backend validation fails while adding variable', async () => {
    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
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

    mockApplyOperation().withServerError(400);

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
        screen.queryByTestId('variable-operation-spinner'),
      ).not.toBeInTheDocument(),
    );

    expect(
      screen.queryByRole('button', {
        name: /add variable/i,
      }),
    ).not.toBeInTheDocument();

    expect(notificationsStore.displayNotification).not.toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Variable could not be saved',
    });

    expect(
      await screen.findByText('Name should be unique'),
    ).toBeInTheDocument();

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      '2',
    );
    await waitFor(() =>
      expect(
        screen.queryByText('Name should be unique'),
      ).not.toBeInTheDocument(),
    );

    await user.type(
      screen.getByRole('textbox', {
        name: /name/i,
      }),
      '{backspace}',
    );

    expect(
      await screen.findByText('Name should be unique'),
    ).toBeInTheDocument();
  });

  it('should display error notification if add variable operation could not be created', async () => {
    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
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

    mockApplyOperation().withDelayedServerError();

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

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId(
        'variable-operation-spinner',
      ),
    );

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Variable could not be saved',
    });
  });

  it('should display error notification if add variable operation could not be created because of auth error', async () => {
    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
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

    mockApplyOperation().withDelayedServerError(403);

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

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId(
        'variable-operation-spinner',
      ),
    );

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Variable could not be saved',
      subtitle: 'You do not have permission',
    });
  });

  it('should display error notification if add variable operation fails', async () => {
    jest.useFakeTimers();

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
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

    mockFetchVariables().withSuccess([createVariable()]);

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: /save variable/i,
        }),
      ).toBeEnabled(),
    );

    mockFetchVariables().withSuccess([createVariable()]);
    mockApplyOperation().withSuccess(
      createBatchOperation({id: 'batch-operation-id'}),
    );

    mockGetOperation().withSuccess([createOperation({state: 'FAILED'})]);

    await user.click(
      screen.getByRole('button', {
        name: /save variable/i,
      }),
    );

    expect(
      within(screen.getByTestId('foo')).getByTestId(
        'variable-operation-spinner',
      ),
    ).toBeInTheDocument();

    jest.runOnlyPendingTimers();

    await waitForElementToBeRemoved(screen.getByTestId('foo'));

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();

    expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
      isDismissable: true,
      kind: 'error',
      title: 'Variable could not be saved',
    });

    expect(getOperationSpy).toHaveBeenCalledWith('batch-operation-id');

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not fail if new variable is returned from next polling before add variable operation completes', async () => {
    jest.useFakeTimers();

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
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

    mockFetchVariables().withSuccess([createVariable()]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockApplyOperation().withSuccess(createBatchOperation());

    jest.runOnlyPendingTimers();
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

    expect(
      screen.getByTestId('variable-operation-spinner'),
    ).toBeInTheDocument();

    mockFetchVariables().withSuccess([
      createVariable(),
      createVariable({id: 'instance_id-foo', name: 'foo', value: 'bar'}),
    ]);

    mockGetOperation().withSuccess([createOperation()]);

    jest.runOnlyPendingTimers();
    await waitForElementToBeRemoved(
      screen.getByTestId('variable-operation-spinner'),
    );
    expect(await screen.findByRole('cell', {name: 'foo'})).toBeInTheDocument();

    expect(
      screen.getByRole('button', {
        name: /add variable/i,
      }),
    ).toBeInTheDocument();
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display spinner on second variable fetch', async () => {
    render(<VariablePanel />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    mockFetchVariables().withDelay([createVariable()]);

    act(() => {
      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: '1',
        payload: {pageSize: 10, scopeId: '1'},
      });
    });

    expect(screen.getByTestId('variables-spinner')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('variables-spinner'),
    );
  });

  it('should select correct tab when navigating between flow nodes', async () => {
    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([createVariable({name: 'test2'})]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);
    mockFetchProcessDefinitionXml().withSuccess('');

    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'Activity_0qtp1k6',
        flowNodeInstanceId: '2',
      });
    });

    expect(await screen.findByText('test2')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Input Mappings'}));

    mockFetchProcessInstanceListeners().withSuccess(noListeners);
    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchVariables().withSuccess([createVariable({name: 'test2'})]);

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
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    mockFetchVariables().withSuccess([createVariable({name: 'test2'})]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

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
    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

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

    await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));
  });

  it('should display spinner for variables tab when switching between tabs', async () => {
    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchProcessInstanceListeners().withSuccess(noListeners);
    mockFetchVariables().withDelay([createVariable({name: 'test2'})]);

    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeInstanceId: 'another_flow_node',
        flowNodeId: 'TEST_FLOW_NODE',
      });
    });

    expect(screen.getByTestId('variables-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('variables-spinner'));
    expect(screen.getByText('test2')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Input Mappings'}));

    mockFetchVariables().withDelay([createVariable({name: 'test2'})]);

    await user.click(screen.getByRole('tab', {name: 'Variables'}));
    await waitForElementToBeRemoved(screen.getByTestId('variables-spinner'));
  });

  it('should not display spinner for variables tab when switching between tabs if scope does not exist', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
    await waitForElementToBeRemoved(screen.getByTestId('variables-skeleton'));

    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchProcessInstanceListeners().withSuccess(noListeners);
    act(() => {
      flowNodeSelectionStore.setSelection({
        flowNodeId: 'non-existing',
        isPlaceholder: true,
      });
    });

    expect(screen.queryByText('testVariableName')).not.toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Input Mappings'}));
    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Variables'}));
    expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();
  });

  it('should display correct state for a flow node that has no running or finished tokens on it', async () => {
    modificationsStore.enableModificationMode();

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      await screen.findByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchProcessInstanceListeners().withSuccess(noListeners);
    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'flowNode-without-running-tokens',
      });
    });

    // initial state
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(screen.queryByText('testVariableName')).not.toBeInTheDocument();
    expect(
      screen.queryByText('The Flow Node has no Variables'),
    ).not.toBeInTheDocument();

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

    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

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

    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    // select only one of the scopes
    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'flowNode-without-running-tokens',
        flowNodeInstanceId: 'some-new-scope-id-1',
        isPlaceholder: true,
      });
    });

    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    // select new parent scope
    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'another-flownode-without-any-tokens',
        flowNodeInstanceId: 'some-new-parent-scope-id',
        isPlaceholder: true,
      });
    });

    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
  });

  it('should display correct state for a flow node that has only one finished token on it', async () => {
    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: null,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: '2022-09-08T12:44:45.406+0000',
      },
    });

    modificationsStore.enableModificationMode();

    const {user} = render(<VariablePanel />, {wrapper: getWrapper()});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'TEST_FLOW_NODE',
      });
    });

    await waitFor(() =>
      expect(flowNodeMetaDataStore.state.metaData).toEqual({
        ...singleInstanceMetadata,
        flowNodeInstanceId: null,
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

    // one 'add token' modification is created
    act(() => {
      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'ADD_TOKEN',
          flowNode: {
            id: 'TEST_FLOW_NODE',
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
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();

    // go to input mappings and back, see the correct state

    await user.click(screen.getByRole('tab', {name: 'Input Mappings'}));
    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', {name: 'Variables'}));
    expect(
      await screen.findByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.',
      ),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();

    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    // select only one of the scopes
    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'TEST_FLOW_NODE',
        flowNodeInstanceId: 'some-new-scope-id',
        isPlaceholder: true,
      });
    });

    expect(
      await screen.findByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
  });

  it('should display correct state for a flow node that has only one running token on it', async () => {
    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '2251799813695856',
      instanceCount: 1,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    mockFetchProcessDefinitionXml().withSuccess('');
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    modificationsStore.enableModificationMode();

    render(<VariablePanel />, {wrapper: getWrapper()});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'Activity_0qtp1k6',
      });
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
      modificationsStore.cancelAllTokens('Activity_0qtp1k6');
    });

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
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
        startDate: '2022-09-30T15:00:31.772+0000',
      },
    });

    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    // select existing scope
    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'Activity_0qtp1k6',
        flowNodeInstanceId: '2251799813695856',
      });
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
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();

    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    // select new scope
    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'Activity_0qtp1k6',
        flowNodeInstanceId: 'some-new-scope-id',
        isPlaceholder: true,
      });
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

  it('should be readonly if flow node has variables but no running instances', async () => {
    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: null,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: '2022-09-08T12:44:45.406+0000',
      },
    });

    modificationsStore.enableModificationMode();

    render(<VariablePanel />, {wrapper: getWrapper()});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();
    expect(screen.getByTestId('edit-variable-value')).toBeInTheDocument();

    mockFetchProcessInstanceListeners().withSuccess(noListeners);
    mockFetchVariables().withSuccess([
      createVariable({name: 'some-other-variable'}),
    ]);

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '9007199254742797',
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: '2022-09-15T12:44:45.406+0000',
      },
    });

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'TEST_FLOW_NODE',
      });
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

  it('should be readonly if flow node has variables and running instances', async () => {
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

    render(<VariablePanel />, {wrapper: getWrapper()});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();
    expect(screen.getByTestId('edit-variable-value')).toBeInTheDocument();

    mockFetchVariables().withSuccess([
      createVariable({name: 'some-other-variable'}),
    ]);

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: '9007199254742797',
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'Activity_0qtp1k6',
      });
    });

    // initial state
    expect(await screen.findByText('some-other-variable')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByTestId('edit-variable-value')).toBeInTheDocument();

    act(() => {
      modificationsStore.cancelAllTokens('Activity_0qtp1k6');
    });

    await waitFor(() => {
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument();
    });
    expect(screen.getByText('some-other-variable')).toBeInTheDocument();

    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /edit variable/i}),
    ).not.toBeInTheDocument();
  });

  it('should be readonly if root node is selected and applying modifications will cancel the whole process', async () => {
    const mockData: GetProcessInstanceStatisticsResponseBody = {
      items: [
        {
          flowNodeId: 'TEST_FLOW_NODE',
          active: 0,
          canceled: 0,
          incidents: 0,
          completed: 1,
        },
        {
          flowNodeId: 'Activity_0qtp1k6',
          active: 0,
          canceled: 0,
          incidents: 1,
          completed: 0,
        },
      ],
    };
    mockFetchFlownodeInstancesStatistics().withSuccess(mockData);
    mockFetchProcessDefinitionXml().withSuccess(
      mockProcessWithInputOutputMappingsXML,
    );

    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: null,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    modificationsStore.enableModificationMode();

    render(<VariablePanel />, {
      wrapper: getWrapper([Paths.processInstance('processInstanceId123')]),
    });
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();
    expect(screen.getByTestId('edit-variable-value')).toBeInTheDocument();

    act(() => {
      modificationsStore.cancelAllTokens('Activity_0qtp1k6');
    });

    await waitFor(() =>
      expect(
        screen.queryByTestId('edit-variable-value'),
      ).not.toBeInTheDocument(),
    );
  });

  it('should display readonly state for existing node if cancel modification is applied on the flow node and one new token is added', async () => {
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

    render(<VariablePanel />, {wrapper: getWrapper()});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'Activity_0qtp1k6',
        flowNodeInstanceId: '2251799813695856',
      });
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
      modificationsStore.cancelAllTokens('Activity_0qtp1k6');
    });

    await waitFor(() =>
      expect(
        screen.queryByRole('button', {name: /add variable/i}),
      ).not.toBeInTheDocument(),
    );

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
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
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
        endDate: '2022-04-10T15:01:31.794+0000',
      },
    });

    modificationsStore.enableModificationMode();

    render(<VariablePanel />, {wrapper: getWrapper()});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);
    mockFetchProcessInstanceListeners().withSuccess(noListeners);

    act(() => {
      flowNodeSelectionStore.selectFlowNode({
        flowNodeId: 'Activity_0qtp1k6',
        flowNodeInstanceId: '2251799813695856',
      });
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
          scopeId: 'new-scope',
          parentScopeIds: {},
        },
      });
    });

    expect(
      screen.queryByRole('button', {name: /add variable/i}),
    ).not.toBeInTheDocument();
    expect(
      screen.getByText('The Flow Node has no Variables'),
    ).toBeInTheDocument();
  });
});
