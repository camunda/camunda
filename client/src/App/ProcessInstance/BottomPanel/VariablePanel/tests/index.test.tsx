/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ThemeProvider} from 'modules/theme/ThemeProvider';
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
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {modificationsStore} from 'modules/stores/modifications';
import {processInstanceDetailsStatisticsStore} from 'modules/stores/processInstanceDetailsStatistics';
import {mockFetchProcessInstanceDetailStatistics} from 'modules/mocks/api/processInstances/fetchProcessInstanceDetailStatistics';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchFlowNodeMetadata} from 'modules/mocks/api/processInstances/fetchFlowNodeMetaData';
import {singleInstanceMetadata} from 'modules/mocks/metadata';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockGetOperation} from 'modules/mocks/api/getOperation';
import * as operationApi from 'modules/api/getOperation';

const getOperationSpy = jest.spyOn(operationApi, 'getOperation');

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('VariablePanel', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

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

    flowNodeMetaDataStore.init();
    flowNodeSelectionStore.init();
    processInstanceDetailsStore.setProcessInstance(
      createInstance({
        id: 'instance_id',
        state: 'ACTIVE',
      })
    );

    processInstanceDetailsStatisticsStore.fetchFlowNodeStatistics(
      'instance_id'
    );
  });

  afterEach(() => {
    variablesStore.reset();
    flowNodeSelectionStore.reset();
    flowNodeMetaDataStore.reset();
    processInstanceDetailsDiagramStore.reset();
    modificationsStore.reset();
    processInstanceDetailsStatisticsStore.reset();
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

      render(<VariablePanel />, {wrapper: Wrapper});

      await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));

      expect(
        screen.getByRole('button', {name: /add variable/i})
      ).toBeInTheDocument();

      flowNodeSelectionStore.setSelection({
        flowNodeId: 'TEST_FLOW_NODE',
      });

      expect(
        await screen.findByText(
          'To view the Variables, select a single Flow Node Instance in the Instance History.'
        )
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i})
      ).not.toBeInTheDocument();
    }
  );

  it.each([true, false])(
    'should show failed placeholder if server error occurs while fetching variables - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      render(<VariablePanel />, {wrapper: Wrapper});

      await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));
      expect(
        screen.getByRole('button', {name: /add variable/i})
      ).toBeInTheDocument();

      mockFetchVariables().withServerError();

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: 'invalid_instance',
        payload: {pageSize: 10, scopeId: '1'},
      });

      expect(
        await screen.findByText('Variables could not be fetched')
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i})
      ).not.toBeInTheDocument();
    }
  );

  it.each([true, false])(
    'should show failed placeholder if network error occurs while fetching variables - modification mode: %p',
    async (enableModificationMode) => {
      if (enableModificationMode) {
        modificationsStore.enableModificationMode();
      }

      render(<VariablePanel />, {wrapper: Wrapper});

      await waitFor(() => expect(variablesStore.state.status).toBe('fetched'));
      expect(
        screen.getByRole('button', {name: /add variable/i})
      ).toBeInTheDocument();

      mockFetchVariables().withNetworkError();

      variablesStore.fetchVariables({
        fetchType: 'initial',
        instanceId: 'invalid_instance',
        payload: {pageSize: 10, scopeId: '1'},
      });

      expect(
        await screen.findByText('Variables could not be fetched')
      ).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: /add variable/i})
      ).not.toBeInTheDocument();
    }
  );

  it('should render variables', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});

    expect(await screen.findByText('testVariableName')).toBeInTheDocument();
  });

  it('should add new variable', async () => {
    jest.useFakeTimers();

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));

    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockFetchVariables().withSuccess([createVariable()]);

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
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
      createBatchOperation({id: 'batch-operation-id'})
    );

    mockGetOperation().withSuccess([createOperation({state: 'COMPLETED'})]);

    await user.click(screen.getByTitle(/save variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    ).toBeInTheDocument();

    const withinVariablesList = within(screen.getByTestId('variables-list'));
    expect(withinVariablesList.queryByTestId('foo')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    );

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();
    expect(mockDisplayNotification).toHaveBeenCalledWith('success', {
      headline: 'Variable added',
    });

    expect(await withinVariablesList.findByTestId('foo')).toBeInTheDocument();

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

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockFetchVariables().withSuccess([]);
    mockApplyOperation().withSuccess(
      createBatchOperation({id: 'batch-operation-id'})
    );

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );
    await user.click(screen.getByTitle(/save variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    ).toBeInTheDocument();

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'TEST_FLOW_NODE',
      flowNodeInstanceId: '2',
    });

    expect(await screen.findByTestId('variables-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(() =>
      screen.getByTestId('variables-spinner')
    );
    expect(
      screen.queryByTestId('edit-variable-spinner')
    ).not.toBeInTheDocument();

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display validation error if backend validation fails while adding variable', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockApplyOperation().withServerError(400);

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );
    await user.click(screen.getByTitle(/save variable/i));

    await waitFor(() =>
      expect(
        screen.queryByTestId('edit-variable-spinner')
      ).not.toBeInTheDocument()
    );

    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(mockDisplayNotification).not.toHaveBeenCalledWith('error', {
      headline: 'Variable could not be saved',
    });

    expect(
      await screen.findByText('Name should be unique')
    ).toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), '2');
    await waitForElementToBeRemoved(() =>
      screen.getByText('Name should be unique')
    );

    await user.type(screen.getByTestId('add-variable-name'), '{backspace}');

    expect(
      await screen.findByText('Name should be unique')
    ).toBeInTheDocument();
  });

  it('should display error notification if add variable operation could not be created', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockApplyOperation().withServerError();

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/save variable/i));

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    );

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();

    expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
      headline: 'Variable could not be saved',
    });
  });

  it('should display error notification if add variable operation could not be created because of auth error', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockApplyOperation().withServerError(403);

    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/save variable/i));

    await waitForElementToBeRemoved(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    );

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();

    expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
      headline: 'Variable could not be saved',
      description: 'You do not have permission',
    });
  });

  it('should display error notification if add variable operation fails', async () => {
    jest.useFakeTimers();

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockFetchVariables().withSuccess([createVariable()]);

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    mockFetchVariables().withSuccess([createVariable()]);
    mockApplyOperation().withSuccess(
      createBatchOperation({id: 'batch-operation-id'})
    );

    mockGetOperation().withSuccess([createOperation({state: 'FAILED'})]);

    await user.click(screen.getByTitle(/save variable/i));

    expect(
      within(screen.getByTestId('foo')).getByTestId('edit-variable-spinner')
    ).toBeInTheDocument();

    jest.runOnlyPendingTimers();

    await waitForElementToBeRemoved(screen.getByTestId('foo'));

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();

    expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
      headline: 'Variable could not be saved',
    });

    expect(getOperationSpy).toHaveBeenCalledWith('batch-operation-id');

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should not fail if new variable is returned from next polling before add variable operation completes', async () => {
    jest.useFakeTimers();

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitFor(() =>
      expect(screen.getByTitle(/add variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/add variable/i));

    await user.type(screen.getByTestId('add-variable-name'), 'foo');
    await user.type(screen.getByTestId('add-variable-value'), '"bar"');

    mockFetchVariables().withSuccess([createVariable()]);
    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockApplyOperation().withSuccess(createBatchOperation());

    jest.runOnlyPendingTimers();
    await waitFor(() =>
      expect(screen.getByTitle(/save variable/i)).toBeEnabled()
    );

    await user.click(screen.getByTitle(/save variable/i));
    expect(screen.queryByTitle(/add variable/i)).not.toBeInTheDocument();

    expect(screen.getByTestId('edit-variable-spinner')).toBeInTheDocument();

    mockFetchVariables().withSuccess([
      createVariable(),
      createVariable({id: 'instance_id-foo', name: 'foo', value: 'bar'}),
    ]);

    mockGetOperation().withSuccess([createOperation()]);

    jest.runOnlyPendingTimers();
    await waitForElementToBeRemoved(
      screen.getByTestId('edit-variable-spinner')
    );
    expect(await screen.findByRole('cell', {name: 'foo'})).toBeInTheDocument();

    expect(screen.getByTitle(/add variable/i)).toBeInTheDocument();
    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display spinner on second variable fetch', async () => {
    render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    mockFetchVariables().withSuccess([createVariable()]);

    variablesStore.fetchVariables({
      fetchType: 'initial',
      instanceId: '1',
      payload: {pageSize: 10, scopeId: '1'},
    });

    expect(await screen.findByTestId('variables-spinner')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('variables-spinner')
    );
  });

  it('should select correct tab when navigating between flow nodes', async () => {
    mockFetchProcessXML().withSuccess(mockProcessWithInputOutputMappingsXML);

    await processInstanceDetailsDiagramStore.fetchProcessXml('processId');

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([createVariable({name: 'test2'})]);

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Activity_0qtp1k6',
      flowNodeInstanceId: '2',
    });

    expect(await screen.findByText('test2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Input Mappings'}));

    expect(screen.getByText('localVariable1')).toBeInTheDocument();
    expect(screen.getByText('localVariable2')).toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'Event_0bonl61',
    });

    expect(
      await screen.findByText('No Input Mappings defined')
    ).toBeInTheDocument();

    mockFetchVariables().withSuccess([createVariable({name: 'test2'})]);
    flowNodeSelectionStore.clearSelection();

    await waitForElementToBeRemoved(() =>
      screen.getByText('No Input Mappings defined')
    );

    expect(
      screen.getByRole('heading', {name: 'Variables'})
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Variables'})
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Input Mappings'})
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: 'Output Mappings'})
    ).not.toBeInTheDocument();

    mockFetchFlowNodeMetadata().withSuccess(singleInstanceMetadata);
    mockFetchVariables().withSuccess([]);

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'StartEvent_1',
    });

    expect(
      await screen.findByText('No Input Mappings defined')
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('heading', {name: 'Variables'})
    ).not.toBeInTheDocument();

    expect(screen.getByRole('button', {name: 'Variables'})).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Input Mappings'})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: 'Output Mappings'})
    ).toBeInTheDocument();
  });

  it('should display spinner for variables tab when switching between tabs', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([createVariable({name: 'test2'})]);

    flowNodeSelectionStore.setSelection({
      flowNodeInstanceId: 'another_flow_node',
      flowNodeId: 'TEST_FLOW_NODE',
    });

    expect(await screen.findByTestId('variables-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('variables-spinner'));
    expect(screen.getByText('test2')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Input Mappings'}));

    mockFetchVariables().withSuccess([createVariable({name: 'test2'})]);

    await user.click(screen.getByRole('button', {name: 'Variables'}));
    await waitForElementToBeRemoved(screen.getByTestId('variables-spinner'));
  });

  it('should not display spinner for variables tab when switching between tabs if scope does not exist', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    await waitForElementToBeRemoved(screen.getByTestId('skeleton-rows'));

    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    flowNodeSelectionStore.setSelection({
      flowNodeId: 'non-existing',
    });

    await waitForElementToBeRemoved(() => screen.getByText('testVariableName'));

    await user.dblClick(screen.getByRole('button', {name: 'Input Mappings'}));
    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Variables'}));
    expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();
  });

  it('should display correct state for a flow node that has no running or finished tokens on it', async () => {
    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    modificationsStore.enableModificationMode();
    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'flowNode-without-running-tokens',
    });

    // initial state
    await waitForElementToBeRemoved(() =>
      screen.getByRole('button', {name: /add variable/i})
    );
    expect(screen.queryByText('testVariableName')).not.toBeInTheDocument();
    expect(
      screen.queryByText('The Flow Node has no Variables')
    ).not.toBeInTheDocument();

    // one 'add token' modification is created
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

    expect(
      await screen.findByText('The Flow Node has no Variables')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();

    // go to input mappings and back, see the correct state
    await user.dblClick(screen.getByRole('button', {name: 'Input Mappings'}));
    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Variables'}));
    expect(
      await screen.findByText('The Flow Node has no Variables')
    ).toBeInTheDocument();
    expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();

    // second 'add token' modification is created

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

    expect(
      await screen.findByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      )
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i})
    ).not.toBeInTheDocument();

    // select only one of the scopes
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'flowNode-without-running-tokens',
      flowNodeInstanceId: 'some-new-scope-id-1',
      isPlaceholder: true,
    });

    expect(
      await screen.findByText('The Flow Node has no Variables')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();

    // select new parent scope
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'another-flownode-without-any-tokens',
      flowNodeInstanceId: 'some-new-parent-scope-id',
      isPlaceholder: true,
    });

    expect(
      screen.getByText('The Flow Node has no Variables')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
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

    const {user} = render(<VariablePanel />, {wrapper: Wrapper});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'TEST_FLOW_NODE',
    });

    await waitFor(() =>
      expect(flowNodeMetaDataStore.state.metaData).toEqual({
        ...singleInstanceMetadata,
        flowNodeInstanceId: null,
        instanceMetadata: {
          ...singleInstanceMetadata.instanceMetadata!,
          endDate: '2018-12-12 00:00:00',
        },
      })
    );

    // initial state
    expect(
      await screen.findByText('The Flow Node has no Variables')
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i})
    ).not.toBeInTheDocument();

    // one 'add token' modification is created
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

    expect(
      await screen.findByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      )
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i})
    ).not.toBeInTheDocument();

    // go to input mappings and back, see the correct state

    await user.dblClick(screen.getByRole('button', {name: 'Input Mappings'}));
    expect(screen.getByText('No Input Mappings defined')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: 'Variables'}));
    expect(
      await screen.findByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByTestId('variables-spinner')).not.toBeInTheDocument();

    // select only one of the scopes
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'TEST_FLOW_NODE',
      flowNodeInstanceId: 'some-new-scope-id',
      isPlaceholder: true,
    });

    expect(
      await screen.findByText('The Flow Node has no Variables')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
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

    modificationsStore.enableModificationMode();

    render(<VariablePanel />, {wrapper: Wrapper});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'Activity_0qtp1k6',
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
      })
    );

    // initial state
    expect(
      await screen.findByText('The Flow Node has no Variables')
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();

    modificationsStore.cancelAllTokens('Activity_0qtp1k6');

    await waitForElementToBeRemoved(() =>
      screen.getByRole('button', {name: /add variable/i})
    );
    expect(
      screen.getByText('The Flow Node has no Variables')
    ).toBeInTheDocument();

    // add a new token
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

    expect(
      await screen.findByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      )
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i})
    ).not.toBeInTheDocument();

    // remove cancel modification
    modificationsStore.removeFlowNodeModification({
      operation: 'CANCEL_TOKEN',
      flowNode: {
        id: 'Activity_0qtp1k6',
        name: 'Flow Node with running tokens',
      },
      affectedTokenCount: 1,
      visibleAffectedTokenCount: 1,
    });

    expect(
      screen.getByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      )
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i})
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

    // select existing scope
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'Activity_0qtp1k6',
      flowNodeInstanceId: '2251799813695856',
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
      })
    );

    expect(
      screen.queryByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      )
    ).not.toBeInTheDocument();

    expect(
      screen.getByText('The Flow Node has no Variables')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();

    // select new scope
    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'Activity_0qtp1k6',
      flowNodeInstanceId: 'some-new-scope-id',
      isPlaceholder: true,
    });

    expect(
      screen.queryByText(
        'To view the Variables, select a single Flow Node Instance in the Instance History.'
      )
    ).not.toBeInTheDocument();

    expect(
      screen.getByText('The Flow Node has no Variables')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
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

    render(<VariablePanel />, {wrapper: Wrapper});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
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
        endDate: '2022-09-15T12:44:45.406+0000',
      },
    });

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'TEST_FLOW_NODE',
    });

    expect(await screen.findByText('some-other-variable')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /add variable/i})
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /enter edit mode/i})
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

    modificationsStore.enableModificationMode();

    render(<VariablePanel />, {wrapper: Wrapper});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
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

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'Activity_0qtp1k6',
    });

    // initial state
    expect(await screen.findByText('some-other-variable')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();
    expect(screen.getByTestId('edit-variable-value')).toBeInTheDocument();

    modificationsStore.cancelAllTokens('Activity_0qtp1k6');

    await waitForElementToBeRemoved(() =>
      screen.getByRole('button', {name: /add variable/i})
    );
    expect(screen.getByText('some-other-variable')).toBeInTheDocument();

    expect(screen.queryByTestId('edit-variable-value')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /enter edit mode/i})
    ).not.toBeInTheDocument();
  });

  it('should be readonly if root node is selected and applying modifications will cancel the whole process', async () => {
    mockFetchFlowNodeMetadata().withSuccess({
      ...singleInstanceMetadata,
      flowNodeInstanceId: null,
      instanceMetadata: {
        ...singleInstanceMetadata.instanceMetadata!,
        endDate: null,
      },
    });

    modificationsStore.enableModificationMode();

    render(<VariablePanel />, {wrapper: Wrapper});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();
    expect(screen.getByTestId('edit-variable-value')).toBeInTheDocument();

    modificationsStore.cancelAllTokens('Activity_0qtp1k6');

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('edit-variable-value')
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

    modificationsStore.enableModificationMode();

    render(<VariablePanel />, {wrapper: Wrapper});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'Activity_0qtp1k6',
      flowNodeInstanceId: '2251799813695856',
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
      })
    );

    // initial state
    expect(
      await screen.findByText('The Flow Node has no Variables')
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();

    modificationsStore.cancelAllTokens('Activity_0qtp1k6');

    await waitForElementToBeRemoved(() =>
      screen.getByRole('button', {name: /add variable/i})
    );

    expect(
      screen.getByText('The Flow Node has no Variables')
    ).toBeInTheDocument();

    // add a new token
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

    expect(
      screen.queryByRole('button', {name: /add variable/i})
    ).not.toBeInTheDocument();
    expect(
      screen.getByText('The Flow Node has no Variables')
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

    render(<VariablePanel />, {wrapper: Wrapper});
    expect(await screen.findByText('testVariableName')).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /add variable/i})
    ).toBeInTheDocument();
    expect(screen.getByText('testVariableName')).toBeInTheDocument();

    mockFetchVariables().withSuccess([]);

    flowNodeSelectionStore.selectFlowNode({
      flowNodeId: 'Activity_0qtp1k6',
      flowNodeInstanceId: '2251799813695856',
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
      })
    );

    // initial state
    expect(
      await screen.findByText('The Flow Node has no Variables')
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /add variable/i})
    ).not.toBeInTheDocument();

    // add one new token
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

    expect(
      screen.queryByRole('button', {name: /add variable/i})
    ).not.toBeInTheDocument();
    expect(
      screen.getByText('The Flow Node has no Variables')
    ).toBeInTheDocument();
  });
});
