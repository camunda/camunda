/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
  waitFor,
} from 'modules/testing-library';
import {getProcessName} from 'modules/utils/instance';
import {ProcessInstanceHeader} from './index';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {variablesStore} from 'modules/stores/variables';
import {
  mockInstanceWithActiveOperation,
  mockInstanceWithoutOperations,
  mockInstanceWithParentInstance,
  mockOperationCreated,
  mockCanceledInstance,
  Wrapper,
  mockProcessInstance,
} from './index.setup';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';

import {
  createBatchOperation,
  createOperation,
  createVariable,
  mockCallActivityProcessXML,
  mockProcessXML,
} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {panelStatesStore} from 'modules/stores/panelStates';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockGetOperation} from 'modules/mocks/api/getOperation';
import * as operationApi from 'modules/api/getOperation';
import {act} from 'react';
import {notificationsStore} from 'modules/stores/notifications';
import {mockFetchProcessDefinitionXml} from 'modules/mocks/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {mockFetchCallHierarchy} from 'modules/mocks/api/v2/processInstances/fetchCallHierarchy';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

const getOperationSpy = jest.spyOn(operationApi, 'getOperation');

describe('InstanceHeader', () => {
  afterEach(() => {
    window.clientConfig = undefined;
  });

  it('should show skeleton before instance data is available', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader processInstance={mockProcessInstance} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );
  });

  it('should render instance data', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockProcessInstance,
          processInstanceKey: mockInstanceWithActiveOperation.id,
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    const processName = getProcessName(mockInstanceWithActiveOperation);

    expect(screen.getByText(processName)).toBeInTheDocument();
    expect(
      screen.getByText(mockInstanceWithActiveOperation.id),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        description: `View process "${getProcessName(
          mockInstanceWithActiveOperation,
        )} version ${
          mockInstanceWithActiveOperation.processVersion
        }" instances`,
      }),
    ).toHaveTextContent(
      mockInstanceWithActiveOperation.processVersion.toString(),
    );
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText('--')).toBeInTheDocument();
    expect(
      screen.getByTestId(`${mockInstanceWithActiveOperation.state}-icon`),
    ).toBeInTheDocument();
    expect(screen.getByText('Process Name')).toBeInTheDocument();
    expect(screen.getByText('Process Instance Key')).toBeInTheDocument();
    expect(screen.getByText('Version')).toBeInTheDocument();
    expect(screen.getByText('Start Date')).toBeInTheDocument();
    expect(screen.getByText('End Date')).toBeInTheDocument();
    expect(screen.getByText('Parent Process Instance Key')).toBeInTheDocument();
    expect(screen.getByText('Called Process Instances')).toBeInTheDocument();
    expect(screen.getAllByText('None').length).toBe(2);
    expect(
      screen.queryByRole('link', {name: /view all/i}),
    ).not.toBeInTheDocument();
  });

  it('should render "View All" link for call activity process', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessDefinitionXml().withSuccess(mockCallActivityProcessXML);

    render(<ProcessInstanceHeader processInstance={mockProcessInstance} />, {
      wrapper: Wrapper,
    });

    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );
    expect(
      await screen.findByRole('link', {name: /view all/i}),
    ).toBeInTheDocument();
  });

  it('should navigate to Instances Page and expand Filters Panel on "View All" click', async () => {
    jest.useFakeTimers();
    panelStatesStore.toggleFiltersPanel();

    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessDefinitionXml().withSuccess(mockCallActivityProcessXML);

    const {user} = render(
      <ProcessInstanceHeader processInstance={mockProcessInstance} />,
      {wrapper: Wrapper},
    );

    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/1$/,
    );
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(true);

    await user.click(await screen.findByRole('link', {name: /view all/i}));

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
    expect(panelStatesStore.state.isFiltersCollapsed).toBe(false);

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should render parent Process Instance Key', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithParentInstance);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockProcessInstance,
          parentProcessInstanceKey: '8724390842390124',
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );

    processInstanceDetailsStore.init({id: mockInstanceWithParentInstance.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(
      screen.getByRole('link', {
        description: `View parent instance ${mockInstanceWithParentInstance.parentInstanceId}`,
      }),
    ).toBeInTheDocument();
  });

  it('should show spinner based on instance having active operations', async () => {
    render(<ProcessInstanceHeader processInstance={mockProcessInstance} />, {
      wrapper: Wrapper,
    });

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    jest.useFakeTimers();
    processInstanceDetailsStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);

    jest.runOnlyPendingTimers();

    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should show spinner when operation is applied', async () => {
    mockFetchCallHierarchy().withSuccess([]);
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockApplyOperation().withSuccess(mockOperationCreated);

    const {user} = render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockProcessInstance,
          processInstanceKey: mockInstanceWithoutOperations.id,
        }}
      />,
      {wrapper: Wrapper},
    );

    jest.useFakeTimers();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should show spinner when variables is added', async () => {
    jest.useFakeTimers();
    const mockVariable = createVariable();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchVariables().withSuccess([mockVariable]);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    mockApplyOperation().withSuccess(
      createBatchOperation({id: 'batch-operation-id', type: 'ADD_VARIABLE'}),
    );

    render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockProcessInstance,
          processInstanceKey: mockInstanceWithoutOperations.id,
        }}
      />,
      {
        wrapper: Wrapper,
      },
    );
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    act(() => {
      variablesStore.addVariable({
        id: mockInstanceWithoutOperations.id,
        name: mockVariable.name,
        value: mockVariable.value,
        onSuccess: () => {},
        onError: () => {},
      });
    });

    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockGetOperation().withSuccess([createOperation({state: 'COMPLETED'})]);

    jest.runOnlyPendingTimers();
    expect(getOperationSpy).toHaveBeenCalledWith('batch-operation-id');

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should remove spinner when operation fails', async () => {
    mockFetchCallHierarchy().withSuccess([]);
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockApplyOperation().withDelayedServerError();

    const {user} = render(
      <ProcessInstanceHeader
        processInstance={{
          ...mockProcessInstance,
          processInstanceKey: mockInstanceWithoutOperations.id,
        }}
      />,
      {wrapper: Wrapper},
    );

    jest.useFakeTimers();

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: /Apply/}));
    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should display error notification when operation fails', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockApplyOperation().withServerError();

    const {user} = render(
      <ProcessInstanceHeader processInstance={mockProcessInstance} />,
      {wrapper: Wrapper},
    );
    processInstanceDetailsStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: /Apply/}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'error',
        title: 'Operation could not be created',
      }),
    );
  });

  it('should display error notification when operation fails with auth error', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);
    mockApplyOperation().withServerError(403);

    const {user} = render(
      <ProcessInstanceHeader processInstance={mockProcessInstance} />,
      {wrapper: Wrapper},
    );
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: /Apply/}));

    await waitFor(() =>
      expect(notificationsStore.displayNotification).toHaveBeenCalledWith({
        isDismissable: true,
        kind: 'error',
        title: 'Operation could not be created',
        subtitle: 'You do not have permission',
      }),
    );
  });

  it('should show operation buttons for running process instance when user has permission', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(<ProcessInstanceHeader processInstance={mockProcessInstance} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(
      screen.getByRole('button', {name: /Cancel Instance/}),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /Modify Instance/}),
    ).toBeInTheDocument();
  });

  it('should show operation buttons for finished process instance when user has permission', async () => {
    mockFetchProcessInstance().withSuccess(mockCanceledInstance);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(
      <ProcessInstanceHeader
        processInstance={{...mockProcessInstance, state: 'TERMINATED'}}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsStore.init({
      id: mockCanceledInstance.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(
      screen.getByRole('button', {name: /Delete Instance/}),
    ).toBeInTheDocument();
  });

  it('should hide delete operation button when user has no resource based permission for delete process instance', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    mockFetchProcessInstance().withSuccess(mockCanceledInstance);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(
      <ProcessInstanceHeader
        processInstance={{...mockProcessInstance, state: 'TERMINATED'}}
      />,
      {
        wrapper: Wrapper,
      },
    );

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsStore.init({
      id: mockCanceledInstance.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(
      screen.queryByRole('button', {name: /Delete Instance/}),
    ).not.toBeInTheDocument();
  });

  it('should hide operation buttons when user has no resource based permission for update process instance', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessDefinitionXml().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(<ProcessInstanceHeader processInstance={mockProcessInstance} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(
      screen.queryByRole('button', {name: /Cancel Instance/}),
    ).not.toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /Modify Instance/}),
    ).not.toBeInTheDocument();
  });
});
