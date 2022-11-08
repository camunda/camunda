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
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createBatchOperation, mockProcessInstances} from 'modules/testUtils';
import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';
import {ListPanel} from './index';
import {Link, MemoryRouter} from 'react-router-dom';
import {processInstancesStore} from 'modules/stores/processInstances';
import {NotificationProvider} from 'modules/notifications';
import {authenticationStore} from 'modules/stores/authentication';
import {panelStatesStore} from 'modules/stores/panelStates';
import {ListFooter} from './ListFooter';
import {mockFetchProcessInstances} from 'modules/mocks/api/processInstances/fetchProcessInstances';
import {
  mockApplyBatchOperation,
  mockApplyOperation,
} from 'modules/mocks/api/processInstances/operations';

function createWrapper(initialPath: string = '/') {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    return (
      <ThemeProvider>
        <NotificationProvider>
          <MemoryRouter initialEntries={[initialPath]}>
            {children}
            <ListFooter />
            <Link to="/processes?incidents=true&active=true&process=bigVarProcess">
              go to big var
            </Link>
          </MemoryRouter>
        </NotificationProvider>
      </ThemeProvider>
    );
  };
  return Wrapper;
}

describe('ListPanel', () => {
  afterEach(() => {
    processInstancesStore.reset();
    panelStatesStore.reset();
  });

  describe('messages', () => {
    it('should display a message for empty list when no filter is selected', async () => {
      mockFetchProcessInstances().withSuccess({
        processInstances: [],
        totalCount: 0,
      });

      processInstancesStore.fetchProcessInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

      expect(
        screen.getByText('There are no Instances matching this filter set')
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          'To see some results, select at least one Instance state'
        )
      ).toBeInTheDocument();
    });

    it('should display a message for empty list when at least one filter is selected', async () => {
      mockFetchProcessInstances().withSuccess({
        processInstances: [],
        totalCount: 0,
      });

      processInstancesStore.fetchProcessInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper('/processes?incidents=true&active=true'),
      });

      await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

      expect(
        screen.getByText('There are no Instances matching this filter set')
      ).toBeInTheDocument();
      expect(
        screen.queryByText(
          'To see some results, select at least one Instance state'
        )
      ).not.toBeInTheDocument();
    });
  });

  describe('display instances List', () => {
    afterEach(() => {
      authenticationStore.reset();
    });

    it('should render a skeleton', async () => {
      mockFetchProcessInstances().withSuccess({
        processInstances: [],
        totalCount: 0,
      });

      processInstancesStore.fetchProcessInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();
      await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
    });

    it('should render table body and footer', async () => {
      mockFetchProcessInstances().withSuccess({
        processInstances: [INSTANCE, ACTIVE_INSTANCE],
        totalCount: 2,
      });

      processInstancesStore.fetchProcessInstancesFromFilters();

      render(<ListPanel />, {wrapper: createWrapper()});
      await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

      expect(screen.getByLabelText('Select all instances')).toBeInTheDocument();
      expect(
        screen.getAllByRole('checkbox', {name: /select instance/i}).length
      ).toBe(2);
      expect(screen.getByText('Operations')).toBeInTheDocument();
      expect(
        screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
      ).toBeInTheDocument();
      expect(screen.getByText('2 results found')).toBeInTheDocument();
    });

    it('should render Footer when list is empty', async () => {
      mockFetchProcessInstances().withSuccess({
        processInstances: [],
        totalCount: 0,
      });

      processInstancesStore.fetchProcessInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

      expect(
        screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
      ).toBeInTheDocument();
      expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
    });

    it('should render for restricted users', async () => {
      authenticationStore.setUser({
        displayName: 'demo',
        permissions: ['read'],
        canLogout: true,
        userId: 'demo',
        roles: null,
        salesPlanType: null,
      });

      mockFetchProcessInstances().withSuccess({
        processInstances: [INSTANCE, ACTIVE_INSTANCE],
        totalCount: 2,
      });

      processInstancesStore.fetchProcessInstancesFromFilters();

      render(<ListPanel />, {wrapper: createWrapper()});
      await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

      expect(
        screen.queryByTitle('Select all instances')
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('checkbox', {name: /select instance/i})
      ).not.toBeInTheDocument();
      expect(screen.queryByText('Operations')).not.toBeInTheDocument();
    });
  });

  it('should start operation on an instance from list', async () => {
    jest.useFakeTimers();

    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE],
      totalCount: 1,
    });

    mockApplyOperation().withSuccess(
      createBatchOperation({type: 'CANCEL_PROCESS_INSTANCE'})
    );

    processInstancesStore.fetchProcessInstancesFromFilters();
    processInstancesStore.init();

    await waitFor(() =>
      expect(processInstancesStore.state.status).toBe('fetched')
    );

    const {user} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.queryByTitle(/has scheduled operations/i)
    ).not.toBeInTheDocument();
    await user.click(screen.getByTitle('Cancel Instance 1'));
    await user.click(screen.getByTitle('Apply'));
    expect(
      screen.getByTitle(/instance 1 has scheduled operations/i)
    ).toBeInTheDocument();
    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE],
      totalCount: 1,
    });

    jest.runOnlyPendingTimers();

    mockFetchProcessInstances().withSuccess({
      processInstances: [INSTANCE],
      totalCount: 1,
    });

    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  describe('spinner', () => {
    it('should display spinners on batch operation', async () => {
      jest.useFakeTimers();

      mockFetchProcessInstances().withSuccess(mockProcessInstances);

      mockApplyBatchOperation().withSuccess(createBatchOperation());

      processInstancesStore.fetchProcessInstancesFromFilters();

      const {user} = render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );

      await user.click(screen.getByLabelText(/select all instances/i));
      await user.click(screen.getByText(/apply operation on/i));
      await user.click(screen.getByText(/cancel/i));
      await user.click(screen.getByText(/^apply$/i));
      expect(screen.getAllByTestId('operation-spinner')).toHaveLength(3);

      mockFetchProcessInstances().withSuccess(mockProcessInstances);

      processInstancesStore.fetchProcessInstancesFromFilters();

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );
      expect(screen.queryAllByTestId('operation-spinner')).toHaveLength(0);
      expect(panelStatesStore.state.isOperationsCollapsed).toBe(false);

      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should remove spinners after batch operation if a server error occurs', async () => {
      mockFetchProcessInstances().withSuccess(mockProcessInstances);

      mockApplyBatchOperation().withServerError();

      processInstancesStore.fetchProcessInstancesFromFilters();

      const {user} = render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );

      mockFetchProcessInstances().withSuccess({
        ...mockProcessInstances,
        totalCount: 1000,
      });

      await user.click(screen.getByLabelText(/select all instances/i));
      await user.click(screen.getByText(/apply operation on/i));
      await user.click(screen.getByText(/cancel/i));
      await user.click(screen.getByText(/^apply$/i));
      expect(screen.getAllByTestId('operation-spinner')).toHaveLength(3);
      await waitFor(() =>
        expect(screen.queryAllByTestId('operation-spinner')).toHaveLength(0)
      );

      expect(panelStatesStore.state.isOperationsCollapsed).toBe(true);
    });

    it('should remove spinners after batch operation if a network error occurs', async () => {
      jest.useFakeTimers();

      mockApplyBatchOperation().withNetworkError();

      mockFetchProcessInstances().withSuccess(mockProcessInstances);

      processInstancesStore.fetchProcessInstancesFromFilters();

      const {user} = render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );

      mockFetchProcessInstances().withSuccess({
        ...mockProcessInstances,
        totalCount: 1000,
      });

      await user.click(screen.getByLabelText(/select all instances/i));
      await user.click(screen.getByText(/apply operation on/i));
      await user.click(screen.getByText(/cancel/i));

      await user.click(screen.getByText(/^apply$/i));
      expect(screen.getAllByTestId('operation-spinner')).toHaveLength(3);

      await waitFor(() =>
        expect(screen.queryAllByTestId('operation-spinner')).toHaveLength(0)
      );

      jest.clearAllTimers();
      jest.useRealTimers();
    });
  });

  it('should show an error message', async () => {
    mockFetchProcessInstances().withServerError();

    const {unmount} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    processInstancesStore.fetchProcessInstancesFromFilters();

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
    expect(
      screen.queryByText('There are no Instances matching this filter set')
    ).not.toBeInTheDocument();

    unmount();
    processInstancesStore.reset();

    mockFetchProcessInstances().withNetworkError();

    processInstancesStore.fetchProcessInstancesFromFilters();

    render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Data could not be fetched')
    ).toBeInTheDocument();
    expect(
      screen.queryByText('There are no Instances matching this filter set')
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/results found/)).not.toBeInTheDocument();
  });
});
