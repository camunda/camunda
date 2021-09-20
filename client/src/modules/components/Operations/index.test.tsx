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
import userEvent from '@testing-library/user-event';
import {rest} from 'msw';
import {instancesStore} from 'modules/stores/instances';
import {Operations} from './index';
import {mockServer} from 'modules/mock-server/node';
import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';
import {groupedProcessesMock} from 'modules/testUtils';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MemoryRouter, Router} from 'react-router';
import {createMemoryHistory} from 'history';

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
  callHierarchy: [],
};

describe('Operations', () => {
  describe('Operation Buttons', () => {
    it('should render retry and cancel button if instance is running and has an incident', () => {
      render(<Operations instance={{...instanceMock, state: 'INCIDENT'}} />, {
        wrapper: ThemeProvider,
      });

      expect(
        screen.getByTitle(`Retry Instance instance_1`)
      ).toBeInTheDocument();
      expect(
        screen.getByTitle(`Cancel Instance instance_1`)
      ).toBeInTheDocument();
    });
    it('should render only cancel button if instance is running and does not have an incident', () => {
      render(<Operations instance={{...instanceMock, state: 'ACTIVE'}} />, {
        wrapper: ThemeProvider,
      });

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.getByTitle(`Cancel Instance instance_1`)
      ).toBeInTheDocument();
    });
    it('should not render retry and cancel buttons if instance is completed', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'COMPLETED',
          }}
        />,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTitle(`Cancel Instance instance_1`)
      ).not.toBeInTheDocument();
    });
    it('should not render retry and cancel buttons if instance is canceled', () => {
      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'CANCELED',
          }}
        />,
        {wrapper: ThemeProvider}
      );

      expect(
        screen.queryByTitle(`Retry Instance instance_1`)
      ).not.toBeInTheDocument();
      expect(
        screen.queryByTitle(`Cancel Instance instance_1`)
      ).not.toBeInTheDocument();
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
        {wrapper: ThemeProvider}
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
        {wrapper: ThemeProvider}
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
        {wrapper: ThemeProvider}
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

      // TODO: Normally this should not be necessary. all the ongoing requests should be canceled and state should not be updated if state is reset. this should also be removed when this problem is solved with https://jira.camunda.com/browse/OPE-1169
      await waitFor(() =>
        expect(instancesStore.state.filteredInstancesCount).toBe(2)
      );

      jest.clearAllTimers();
      jest.useRealTimers();
    });
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
        {wrapper: ThemeProvider}
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

      const Wrapper: React.FC = ({children}) => {
        return (
          <ThemeProvider>
            <MemoryRouter>{children}</MemoryRouter>
          </ThemeProvider>
        );
      };

      const modalText =
        /To cancel this instance, the parent instance.*needs to be canceled. When the parent is canceled all the called instances will be canceled automatically./;

      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
            parentInstanceId: '6755399441058622',
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
      const mockHistory = createMemoryHistory();
      const parentInstanceId = '6755399441058622';

      const Wrapper: React.FC = ({children}) => {
        return (
          <ThemeProvider>
            <Router history={mockHistory}>{children}</Router>
          </ThemeProvider>
        );
      };

      render(
        <Operations
          instance={{
            ...instanceMock,
            state: 'INCIDENT',
            parentInstanceId,
          }}
        />,
        {wrapper: Wrapper}
      );

      userEvent.click(
        screen.getByRole('button', {name: 'Cancel Instance instance_1'})
      );

      userEvent.click(
        screen.getByRole('link', {
          name: `View parent instance ${parentInstanceId}`,
        })
      );

      expect(mockHistory.location.pathname).toBe(
        `/instances/${parentInstanceId}`
      );
    });
  });
});
