/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {operationsStore} from 'modules/stores/operations';
import {
  mockInstanceWithActiveOperation,
  mockInstanceWithoutOperations,
  mockInstanceWithParentInstance,
  mockOperationCreated,
  mockCanceledInstance,
} from './index.setup';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MOCK_TIMESTAMP} from 'modules/utils/date/__mocks__/formatDate';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {
  createBatchOperation,
  createOperation,
  createVariable,
  mockCallActivityProcessXML,
  mockProcessXML,
} from 'modules/testUtils';
import {authenticationStore} from 'modules/stores/authentication';
import {panelStatesStore} from 'modules/stores/panelStates';
import {LocationLog} from 'modules/utils/LocationLog';
import {mockFetchProcessInstance} from 'modules/mocks/api/processInstances/fetchProcessInstance';
import {mockFetchVariables} from 'modules/mocks/api/processInstances/fetchVariables';
import {mockFetchProcessXML} from 'modules/mocks/api/processes/fetchProcessXML';
import {mockApplyOperation} from 'modules/mocks/api/processInstances/operations';
import {mockGetOperation} from 'modules/mocks/api/getOperation';
import * as operationApi from 'modules/api/getOperation';

const getOperationSpy = jest.spyOn(operationApi, 'getOperation');

const mockDisplayNotification = jest.fn();

jest.mock('modules/notifications', () => {
  return {
    useNotifications: () => {
      return {displayNotification: mockDisplayNotification};
    },
  };
});

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes/1']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
          <Route path="/processes" element={children} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('InstanceHeader', () => {
  beforeAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = false;
  });

  afterAll(() => {
    //@ts-ignore
    IS_REACT_ACT_ENVIRONMENT = true;
  });

  afterEach(() => {
    operationsStore.reset();
    variablesStore.reset();
    processInstanceDetailsStore.reset();
    processInstanceDetailsDiagramStore.reset();
    authenticationStore.reset();
    window.clientConfig = undefined;
  });

  it('should show skeleton before instance data is available', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
  });

  it('should render instance data', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    const processName = getProcessName(mockInstanceWithActiveOperation);

    expect(screen.getByText(processName)).toBeInTheDocument();
    expect(
      screen.getByText(mockInstanceWithActiveOperation.id)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        description: `View process ${getProcessName(
          mockInstanceWithActiveOperation
        )} version ${mockInstanceWithActiveOperation.processVersion} instances`,
      })
    ).toHaveTextContent(
      mockInstanceWithActiveOperation.processVersion.toString()
    );
    expect(screen.getByText(MOCK_TIMESTAMP)).toBeInTheDocument();
    expect(screen.getByText('--')).toBeInTheDocument();
    expect(
      screen.getByTestId(`${mockInstanceWithActiveOperation.state}-icon`)
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
      screen.queryByRole('link', {name: /view all/i})
    ).not.toBeInTheDocument();
  });

  it('should render "View All" link for call activity process', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );
    expect(
      await screen.findByRole('link', {name: /view all/i})
    ).toBeInTheDocument();
  });

  it('should navigate to Instances Page and expand Filters Panel on "View All" click', async () => {
    jest.useFakeTimers();
    panelStatesStore.toggleFiltersPanel();

    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/1$/
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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({id: mockInstanceWithParentInstance.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.getByRole('link', {
        description: `View parent instance ${mockInstanceWithParentInstance.parentInstanceId}`,
      })
    ).toBeInTheDocument();
  });

  it('should show spinner based on instance having active operations', async () => {
    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    jest.useFakeTimers();
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);

    jest.runOnlyPendingTimers();

    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should show spinner when operation is applied', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockApplyOperation().withSuccess(mockOperationCreated);

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
  });

  it('should show spinner when variables is added', async () => {
    jest.useFakeTimers();
    const mockVariable = createVariable();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchVariables().withSuccess([mockVariable]);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    mockApplyOperation().withSuccess(
      createBatchOperation({id: 'batch-operation-id', type: 'ADD_VARIABLE'})
    );

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    variablesStore.addVariable({
      id: mockInstanceWithoutOperations.id,
      name: mockVariable.name,
      value: mockVariable.value,
      onSuccess: () => {},
      onError: () => {},
    });

    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);

    mockGetOperation().withSuccess([createOperation({state: 'COMPLETED'})]);

    jest.runOnlyPendingTimers();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);

    await waitForElementToBeRemoved(screen.queryByTestId('operation-spinner'));

    expect(getOperationSpy).toHaveBeenCalledWith('batch-operation-id');

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should remove spinner when operation fails', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockApplyOperation().withServerError();

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: /Apply/}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));
  });

  it('should display error notification when operation fails', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockApplyOperation().withServerError();

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: /Apply/}));

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Operation could not be created',
      })
    );
  });

  it('should display error notification when operation fails with auth error', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockApplyOperation().withServerError(403);

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: /Apply/}));

    await waitFor(() =>
      expect(mockDisplayNotification).toHaveBeenCalledWith('error', {
        headline: 'Operation could not be created',
        description: 'You do not have permission',
      })
    );
  });

  it('should show operation buttons for running process instance when user has permission', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.getByRole('button', {name: /Cancel Instance/})
    ).toBeInTheDocument();

    expect(
      screen.getByRole('button', {name: /Modify Instance/})
    ).toBeInTheDocument();
  });

  it('should show operation buttons for finished process instance when user has permission', async () => {
    mockFetchProcessInstance().withSuccess(mockCanceledInstance);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockCanceledInstance.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.getByRole('button', {name: /Delete Instance/})
    ).toBeInTheDocument();
  });

  it('should hide delete operation button when user has no resource based permission for delete process instance', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    mockFetchProcessInstance().withSuccess(mockCanceledInstance);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockCanceledInstance.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.queryByRole('button', {name: /Delete Instance/})
    ).not.toBeInTheDocument();
  });

  it('should hide operation buttons when user has no resource based permission for update process instance', async () => {
    window.clientConfig = {
      resourcePermissionsEnabled: true,
    };

    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.queryByRole('button', {name: /Cancel Instance/})
    ).not.toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /Modify Instance/})
    ).not.toBeInTheDocument();
  });

  it('should hide operation buttons when user has no permission', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithActiveOperation);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });

    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(
      screen.queryByRole('button', {name: /Cancel Instance/})
    ).not.toBeInTheDocument();

    expect(
      screen.queryByRole('button', {name: /Modify Instance/})
    ).not.toBeInTheDocument();
  });

  it('should call onPollingFailure if delete operation is performed', async () => {
    jest.useFakeTimers();

    mockFetchProcessInstance().withSuccess(mockCanceledInstance);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    const onPollingFailure = jest.fn();

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();

    processInstanceDetailsStore.init({
      id: mockInstanceWithoutOperations.id,
      onPollingFailure,
    });
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton')
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/1$/
    );

    await user.click(screen.getByRole('button', {name: /Delete Instance/i}));
    expect(
      await screen.findByText(/About to delete Instance/)
    ).toBeInTheDocument();

    mockApplyOperation().withSuccess(mockOperationCreated);

    await user.click(screen.getByTestId('delete-button'));
    await waitForElementToBeRemoved(
      screen.getByText(/About to delete Instance/)
    );

    mockFetchProcessInstance().withServerError(404);

    jest.runOnlyPendingTimers();

    await waitFor(() => expect(onPollingFailure).toHaveBeenCalled());

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
