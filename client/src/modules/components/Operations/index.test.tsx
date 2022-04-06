/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitFor,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {rest} from 'msw';
import {processInstancesStore} from 'modules/stores/processInstances';
import {Operations} from './index';
import {mockServer} from 'modules/mock-server/node';
import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';
import {groupedProcessesMock} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import userEvent from '@testing-library/user-event';
import {LocationLog} from 'modules/utils/LocationLog';

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

const Wrapper: React.FC = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/processes']}>
        <Routes>
          <Route path="/processes/:processInstanceId" element={children} />
          <Route path="/processes" element={children} />
        </Routes>
        <LocationLog />
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('Operations', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  describe('Operation Buttons', () => {
    it('should render retry and cancel button if instance is running and has an incident', () => {
      render(<Operations instance={{...instanceMock, state: 'INCIDENT'}} />, {
        wrapper: Wrapper,
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
        wrapper: Wrapper,
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
        {wrapper: Wrapper}
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
        {wrapper: Wrapper}
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
        {wrapper: Wrapper}
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
        {wrapper: Wrapper}
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

      processInstancesStore.init();
      processInstancesStore.fetchProcessInstancesFromFilters();

      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
          }}
        />,
        {wrapper: Wrapper}
      );

      expect(screen.queryByTestId('operation-spinner')).not.toBeInTheDocument();

      await waitFor(() =>
        expect(processInstancesStore.state.status).toBe('fetched')
      );
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

      processInstancesStore.reset();

      jest.clearAllTimers();
      jest.useRealTimers();
    });
  });

  it('should not display notification and redirect if delete operation is performed on instances page', async () => {
    render(
      <Operations
        instance={{
          ...instanceMock,
          state: 'COMPLETED',
        }}
        onError={() => {}}
      />,
      {
        wrapper: Wrapper,
      }
    );

    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
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
    expect(screen.getByTestId('pathname')).toHaveTextContent(/^\/processes$/);
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
        {wrapper: Wrapper}
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
        {wrapper: Wrapper}
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
      const rootInstanceId = '6755399441058622';

      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
            rootInstanceId,
          }}
        />,
        {
          wrapper: Wrapper,
        }
      );

      userEvent.click(
        screen.getByRole('button', {name: 'Cancel Instance instance_1'})
      );

      userEvent.click(
        screen.getByRole('link', {
          name: `View root instance ${rootInstanceId}`,
        })
      );

      expect(screen.getByTestId('pathname').textContent).toBe(
        `/processes/${rootInstanceId}`
      );
    });
  });
});
