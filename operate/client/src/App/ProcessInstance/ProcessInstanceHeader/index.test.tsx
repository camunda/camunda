/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {useEffect} from 'react';
import {act} from 'react-dom/test-utils';
import {Paths} from 'modules/Routes';
import {notificationsStore} from 'modules/stores/notifications';

jest.mock('modules/stores/notifications', () => ({
  notificationsStore: {
    displayNotification: jest.fn(() => () => {}),
  },
}));

const getOperationSpy = jest.spyOn(operationApi, 'getOperation');

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  useEffect(() => {
    return () => {
      operationsStore.reset();
      variablesStore.reset();
      processInstanceDetailsStore.reset();
      processInstanceDetailsDiagramStore.reset();
      authenticationStore.reset();
    };
  }, []);

  return (
    <MemoryRouter initialEntries={[Paths.processInstance('1')]}>
      <Routes>
        <Route path={Paths.processInstance()} element={children} />
        <Route path={Paths.processes()} element={children} />
      </Routes>
      <LocationLog />
    </MemoryRouter>
  );
};

describe('InstanceHeader', () => {
  afterEach(() => {
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
      screen.getByTestId('instance-header-skeleton'),
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
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
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
    mockFetchProcessXML().withSuccess(mockCallActivityProcessXML);

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    processInstanceDetailsDiagramStore.init();
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
    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    jest.useFakeTimers();
    processInstanceDetailsDiagramStore.init();
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
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockApplyOperation().withSuccess(mockOperationCreated);

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    jest.useFakeTimers();

    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /Cancel Instance/}));
    await user.click(screen.getByRole('button', {name: 'Apply'}));

    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('operation-spinner'),
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should show spinner when variables is added', async () => {
    jest.useFakeTimers();
    const mockVariable = createVariable();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchVariables().withSuccess([mockVariable]);
    mockFetchProcessXML().withSuccess(mockProcessXML);

    mockApplyOperation().withSuccess(
      createBatchOperation({id: 'batch-operation-id', type: 'ADD_VARIABLE'}),
    );

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({
      id: mockInstanceWithActiveOperation.id,
    });
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

    expect(await screen.findByTestId('operation-spinner')).toBeInTheDocument();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);

    mockGetOperation().withSuccess([createOperation({state: 'COMPLETED'})]);

    jest.runOnlyPendingTimers();

    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('operation-spinner'),
    );

    expect(getOperationSpy).toHaveBeenCalledWith('batch-operation-id');

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  it('should remove spinner when operation fails', async () => {
    mockFetchProcessInstance().withSuccess(mockInstanceWithoutOperations);
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockApplyOperation().withDelayedServerError();

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});
    processInstanceDetailsDiagramStore.init();
    processInstanceDetailsStore.init({id: mockInstanceWithoutOperations.id});
    await waitForElementToBeRemoved(
      screen.getByTestId('instance-header-skeleton'),
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
    mockFetchProcessXML().withSuccess(mockProcessXML);
    mockApplyOperation().withServerError(403);

    const {user} = render(<ProcessInstanceHeader />, {wrapper: Wrapper});
    processInstanceDetailsDiagramStore.init();
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
        subtitle: 'You do not have permission',
      }),
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
      tenants: [],
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read', 'write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
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
    mockFetchProcessXML().withSuccess(mockProcessXML);

    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['write'],
      canLogout: true,
      userId: 'demo',
      roles: null,
      salesPlanType: null,
      c8Links: {},
      tenants: [],
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
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
      tenants: [],
    });

    render(<ProcessInstanceHeader />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    processInstanceDetailsDiagramStore.init();
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
      screen.getByTestId('instance-header-skeleton'),
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(
      /^\/processes\/1$/,
    );

    await user.click(screen.getByRole('button', {name: /Delete Instance/i}));
    expect(
      await screen.findByText(/About to delete Instance/),
    ).toBeInTheDocument();

    mockApplyOperation().withSuccess(mockOperationCreated);

    await user.click(screen.getByRole('button', {name: /danger delete/i}));
    expect(
      screen.queryByText(/About to delete Instance/),
    ).not.toBeInTheDocument();

    mockFetchProcessInstance().withServerError(404);

    jest.runOnlyPendingTimers();

    await waitFor(() => expect(onPollingFailure).toHaveBeenCalled());

    jest.clearAllTimers();
    jest.useRealTimers();
  });
});
