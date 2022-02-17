/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {rest} from 'msw';
import {instancesStore} from 'modules/stores/instances';
import {Operations} from './index';
import {mockServer} from 'modules/mock-server/node';
import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';
import {groupedProcessesMock} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {createMemoryHistory} from 'history';
import {Router, Route} from 'react-router-dom';
import userEvent from '@testing-library/user-event';

const instanceMock: ProcessInstanceEntity = {
  id: 'instance_1',
  state: 'ACTIVE',
  operations: [],
  bpmnProcessId: '',
  startDate: '',
  endDate: null,
  hasActiveOperation: true,
  processId: '',
  processName: '',
  processVersion: 1,
  sortValues: ['', 'instance_1'],
  parentInstanceId: null,
  rootInstanceId: null,
  callHierarchy: [],
};

const mockDisplayNotification = jest.fn();
jest.mock('modules/notifications', () => ({
  useNotifications: () => ({
    displayNotification: mockDisplayNotification,
  }),
}));

const createWrapper = (
  history = createMemoryHistory({initialEntries: ['/instances']})
) => {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <Router history={history}>
          <Route path="/instances/:processInstanceId">{children} </Route>
          <Route exact path="/instances">
            {children}
          </Route>
        </Router>
      </ThemeProvider>
    );
  };

  return Wrapper;
};

describe('Operations', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('Operation Buttons', () => {
    it('should render retry and cancel button if instance is running and has an incident', () => {
      render(<Operations instance={{...instanceMock, state: 'INCIDENT'}} />, {
        wrapper: createWrapper(),
      });

      expect(
        screen.getByTitle(`Retry Instance instance_1`)
      ).toBeInTheDocument();
      expect(
        screen.getByTitle(`Cancel Instance instance_1`)
      ).toBeInTheDocument();
      expect(
        screen.queryByTitle(`Delete Instance instance_1`)
      ).not.toBeInTheDocument();
    });
    it('should render only cancel button if instance is running and does not have an incident', () => {
      render(<Operations instance={{...instanceMock, state: 'ACTIVE'}} />, {
        wrapper: createWrapper(),
      });

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.getByTitle(`Cancel Instance instance_1`)
      ).toBeInTheDocument();
      expect(
        screen.queryByTitle(`Delete Instance instance_1`)
      ).not.toBeInTheDocument();
    });
    it('should render delete button if instance is completed', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'COMPLETED',
          }}
        />,
        {wrapper: createWrapper()}
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTitle(`Cancel Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.getByTitle(`Delete Instance instance_1`)
      ).toBeInTheDocument();
    });
    it('should render delete button if instance is canceled', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'CANCELED',
          }}
        />,
        {wrapper: createWrapper()}
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTitle(`Cancel Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.getByTitle(`Delete Instance instance_1`)
      ).toBeInTheDocument();
    });
  });
  describe('Spinner', () => {
    it('should not display spinner', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
          }}
        />,
        {wrapper: createWrapper()}
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();
    });
    it('should display spinner if it is forced', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
          }}
          forceSpinner={true}
        />,
        {wrapper: createWrapper()}
      );

      expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();
    });

    it('should display spinner if incident id is included in instances with active operations', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(
            ctx.json({processInstances: [ACTIVE_INSTANCE], totalCount: 1})
          )
        ),
        rest.get('/api/processes/grouped', (_, res, ctx) =>
          res.once(ctx.json(groupedProcessesMock))
        )
      );

      instancesStore.init();
      instancesStore.fetchInstancesFromFilters();

      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
          }}
        />,
        {wrapper: createWrapper()}
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
      expect(
        await screen.findByTestId('operation-spinner')
      ).toBeInTheDocument();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [INSTANCE], totalCount: 1}))
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [INSTANCE], totalCount: 2}))
        )
      );

      jest.runOnlyPendingTimers();

      await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

      instancesStore.reset();

      jest.clearAllTimers();
      jest.useRealTimers();
    });
  });

  it('should not display notification and redirect if delete operation is performed on instances page', async () => {
    const MOCK_HISTORY = createMemoryHistory({
      initialEntries: ['/instances'],
    });

    render(
      <Operations
        instance={{
          ...instanceMock,
          state: 'COMPLETED',
        }}
        onError={() => {}}
      />,
      {
        wrapper: createWrapper(MOCK_HISTORY),
      }
    );

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    userEvent.click(screen.getByRole('button', {name: /Delete Instance/}));
    expect(screen.getByText(/About to delete Instance/)).toBeInTheDocument();

    mockServer.use(
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(ctx.json({}))
      )
    );

    userEvent.click(screen.getByTestId('delete-button'));
    await waitForElementToBeRemoved(
      screen.getByText(/About to delete Instance/)
    );

    expect(mockDisplayNotification).not.toHaveBeenCalled();
    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
  });

  describe('Cancel Operation', () => {
    it('should show cancel confirmation modal', async () => {
      const modalText =
        'About to cancel Instance instance_1. In case there are called instances, these will be canceled too.';

      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
          }}
        />,
        {wrapper: createWrapper()}
      );

      userEvent.click(
        screen.getByRole('button', {name: 'Cancel Instance instance_1'})
      );

      expect(screen.getByText(modalText));
      expect(screen.getByRole('button', {name: 'Apply'})).toBeInTheDocument();
      expect(screen.getByRole('button', {name: 'Cancel'})).toBeInTheDocument();

      userEvent.click(screen.getByRole('button', {name: 'Cancel'}));

      await waitForElementToBeRemoved(screen.getByText(modalText));
    });

    it('should show modal when trying to cancel called instance', async () => {
      const onOperationMock = jest.fn();

      const modalText =
        /To cancel this instance, the root instance.*needs to be canceled. When the root instance is canceled all the called instances will be canceled automatically./;

      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
            rootInstanceId: '6755399441058622',
          }}
          onOperation={onOperationMock}
        />,
        {wrapper: createWrapper()}
      );

      userEvent.click(
        screen.getByRole('button', {name: 'Cancel Instance instance_1'})
      );

      expect(screen.getByText(modalText)).toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: 'Cancel'})
      ).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', {name: 'Apply'})
      ).not.toBeInTheDocument();

      userEvent.click(screen.getByRole('button', {name: 'Close'}));

      await waitForElementToBeRemoved(screen.getByText(modalText));
    });

    it('should redirect to linked parent instance', () => {
      const mockHistory = createMemoryHistory({
        initialEntries: ['/instances'],
      });
      const rootInstanceId = '6755399441058622';

      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
            rootInstanceId,
          }}
        />,
        {wrapper: createWrapper(mockHistory)}
      );

      userEvent.click(
        screen.getByRole('button', {name: 'Cancel Instance instance_1'})
      );

      userEvent.click(
        screen.getByRole('link', {
          name: `View root instance ${rootInstanceId}`,
        })
      );

      expect(mockHistory.location.pathname).toBe(
        `/instances/${rootInstanceId}`
      );
    });
  });
});
