/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
  within,
  waitFor,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {groupedProcessesMock, mockProcessInstances} from 'modules/testUtils';
import CollapsablePanelContext from 'modules/contexts/CollapsablePanelContext';
import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';
import {ListPanel} from './index';
import {Router} from 'react-router-dom';
import {createMemoryHistory, MemoryHistory} from 'history';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {instancesStore} from 'modules/stores/instances';
import {NotificationProvider} from 'modules/notifications';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';

function createWrapper({
  expandOperationsMock,
  history,
}: {
  expandOperationsMock?: jest.Mock;
  history?: MemoryHistory;
} = {}) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <NotificationProvider>
          <Router history={history ?? createMemoryHistory()}>
            <CollapsablePanelContext.Provider
              value={{expandOperations: expandOperationsMock ?? jest.fn()}}
            >
              {children}
            </CollapsablePanelContext.Provider>
          </Router>
        </NotificationProvider>
      </ThemeProvider>
    );
  };
  return Wrapper;
}

describe('ListPanel', () => {
  afterEach(() => {
    instancesStore.reset();
    instancesDiagramStore.reset();
  });

  describe('messages', () => {
    it('should display a message for empty list when no filter is selected', async () => {
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      instancesStore.fetchInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

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
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      instancesStore.fetchInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper({
          history: createMemoryHistory({
            initialEntries: ['/instances?incidents=true&active=true'],
          }),
        }),
      });

      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

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
    it('should render a skeleton', async () => {
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );
      instancesStore.fetchInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByTestId('listpanel-skeleton')).toBeInTheDocument();
      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));
    });

    it('should render table body and footer', async () => {
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(
            ctx.json({
              processInstances: [INSTANCE, ACTIVE_INSTANCE],
              totalCount: 0,
            })
          )
        )
      );

      instancesStore.fetchInstancesFromFilters();

      render(<ListPanel />, {wrapper: createWrapper()});
      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

      const withinFirstRow = within(
        screen.getByRole('row', {
          name: /instance 1/i,
        })
      );

      expect(withinFirstRow.getByText('someProcessName')).toBeInTheDocument();
      expect(
        screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
      ).toBeInTheDocument();
    });

    it('should render Footer when list is empty', async () => {
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      instancesStore.fetchInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

      expect(
        screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
      ).toBeInTheDocument();
    });
  });

  it('should start operation on an instance from list', async () => {
    jest.useFakeTimers();

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json({processInstances: [INSTANCE], totalCount: 1}))
      ),
      rest.post('/api/process-instances/:instanceId/operation', (_, res, ctx) =>
        res.once(
          ctx.json({
            id: '1',
            name: null,
            type: 'CANCEL_PROCESS_INSTANCE',
            startDate: '2020-11-23T04:42:08.030+0000',
            endDate: null,
            username: 'demo',
            instancesCount: 1,
            operationsTotalCount: 1,
            operationsFinishedCount: 0,
          })
        )
      )
    );

    instancesStore.fetchInstancesFromFilters();
    instancesStore.init();

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.queryByTitle(/has scheduled operations/i)
    ).not.toBeInTheDocument();
    userEvent.click(screen.getByRole('button', {name: 'Cancel Instance 1'}));
    expect(
      screen.getByTitle(/instance 1 has scheduled operations/i)
    ).toBeInTheDocument();
    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json({processInstances: [INSTANCE], totalCount: 1}))
      ),
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(
          ctx.json({
            processInstances: [INSTANCE],
            totalCount: 2,
          })
        )
      )
    );
    jest.runOnlyPendingTimers();
    await waitForElementToBeRemoved(screen.getByTestId('operation-spinner'));

    // TODO: Normally this should not be necessary. all the ongoing requests should be canceled and state should not be updated if state is reset. this should also be removed when this problem is solved with https://jira.camunda.com/browse/OPE-1169
    await waitFor(() =>
      expect(instancesStore.state.filteredInstancesCount).toBe(2)
    );

    jest.clearAllTimers();
    jest.useRealTimers();
  });

  describe('spinner', () => {
    it('should display spinners on batch operation', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        ),
        rest.post('/api/process-instances/batch-operation', (_, res, ctx) =>
          res.once(ctx.json({}))
        )
      );

      instancesStore.fetchInstancesFromFilters();

      const expandOperationsMock = jest.fn();

      render(<ListPanel />, {
        wrapper: createWrapper({expandOperationsMock}),
      });

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

      userEvent.click(
        screen.getByRole('checkbox', {name: 'Select all instances'})
      );
      userEvent.click(screen.getByRole('button', {name: /Apply Operation on/}));
      userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
      userEvent.click(screen.getByRole('button', {name: 'Apply'}));
      expect(screen.getAllByTestId('operation-spinner').length).toBe(2);

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        )
      );

      instancesStore.fetchInstancesFromFilters();

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
      expect(screen.queryAllByTestId('operation-spinner').length).toBe(0);
      expect(expandOperationsMock).toHaveBeenCalledTimes(1);

      jest.clearAllTimers();
      jest.useRealTimers();
    }, 20000);

    it('should remove spinners after batch operation if a server error occurs', async () => {
      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        ),
        rest.post('/api/process-instances/batch-operation', (_, res, ctx) =>
          res.once(ctx.status(500), ctx.json({}))
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res(ctx.json({...mockProcessInstances, totalCount: 1000}))
        )
      );

      instancesStore.fetchInstancesFromFilters();

      const expandOperationsMock = jest.fn();
      render(<ListPanel />, {
        wrapper: createWrapper({expandOperationsMock}),
      });

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

      userEvent.click(
        screen.getByRole('checkbox', {name: 'Select all instances'})
      );
      userEvent.click(screen.getByRole('button', {name: /Apply Operation on/}));
      userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
      userEvent.click(screen.getByRole('button', {name: 'Apply'}));
      expect(screen.getAllByTestId('operation-spinner').length).toBe(2);
      await waitFor(() =>
        expect(screen.queryAllByTestId('operation-spinner').length).toBe(0)
      );

      // TODO: Normally this should not be necessary. all the ongoing requests should be canceled and state should not be updated if state is reset. this should also be removed when this problem is solved with https://jira.camunda.com/browse/OPE-1169
      await waitFor(() =>
        expect(instancesStore.state.filteredInstancesCount).toBe(1000)
      );

      expect(expandOperationsMock).not.toHaveBeenCalled();
    }, 20000);

    it('should remove spinners after batch operation if a network error occurs', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        ),
        rest.post('/api/process-instances/batch-operation', (_, res) =>
          res.networkError('A network error')
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({...mockProcessInstances, totalCount: 1000}))
        )
      );

      instancesStore.fetchInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

      userEvent.click(
        screen.getByRole('checkbox', {name: 'Select all instances'})
      );

      userEvent.click(screen.getByRole('button', {name: /Apply Operation on/}));
      userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
      userEvent.click(screen.getByRole('button', {name: 'Apply'}));
      expect(screen.getAllByTestId('operation-spinner').length).toBe(2);

      await waitFor(() =>
        expect(screen.queryAllByTestId('operation-spinner').length).toBe(0)
      );

      // TODO: Normally this should not be necessary. all the ongoing requests should be canceled and state should not be updated if state is reset. this should also be removed when this problem is solved with https://jira.camunda.com/browse/OPE-1169
      await waitFor(() =>
        expect(instancesStore.state.filteredInstancesCount).toBe(1000)
      );

      jest.clearAllTimers();
      jest.useRealTimers();
    }, 20000);
  });

  it('should show an error message', async () => {
    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.once(ctx.json({}), ctx.status(500))
      )
    );

    const {unmount} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    instancesStore.fetchInstancesFromFilters();

    expect(
      await screen.findByText('Instances could not be fetched')
    ).toBeInTheDocument();

    unmount();
    instancesStore.reset();

    mockServer.use(
      rest.post('/api/process-instances', (_, res, ctx) =>
        res.networkError('A network error')
      )
    );

    instancesStore.fetchInstancesFromFilters();

    render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Instances could not be fetched')
    ).toBeInTheDocument();
  });

  describe('getting started experience', () => {
    it('should poll until there is a process instance', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.get('/api/processes/grouped', (_, res, ctx) =>
          res.once(ctx.json(groupedProcessesMock))
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      render(<ListPanel />, {
        wrapper: createWrapper({
          history: createMemoryHistory({
            initialEntries: ['/instances?incidents=true&active=true'],
          }),
        }),
      });

      instancesStore.init(true);
      await instancesStore.fetchInstancesFromFilters();

      expect(screen.getByTestId('listpanel-skeleton')).toBeInTheDocument();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );
      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        )
      );

      jest.runOnlyPendingTimers();

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
      expect(
        screen.queryByTestId('listpanel-skeleton')
      ).not.toBeInTheDocument();
      expect(screen.getByText('2251799813685594')).toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should poll 3 times, then display empty list message (on first render)', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.get('/api/processes/grouped', (_, res, ctx) =>
          res.once(ctx.json(groupedProcessesMock))
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      render(<ListPanel />, {
        wrapper: createWrapper({
          history: createMemoryHistory({
            initialEntries: ['/instances?incidents=true&active=true'],
          }),
        }),
      });

      instancesStore.init(true);
      await instancesStore.fetchInstancesFromFilters();

      expect(screen.getByTestId('listpanel-skeleton')).toBeInTheDocument();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );
      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      jest.runOnlyPendingTimers();

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
      expect(
        screen.queryByTestId('listpanel-skeleton')
      ).not.toBeInTheDocument();
      expect(
        screen.getByText('There are no Instances matching this filter set')
      ).toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    });

    it('should poll 3 times, then display empty list message (on filter change)', async () => {
      jest.useFakeTimers();

      mockServer.use(
        rest.get('/api/processes/grouped', (_, res, ctx) =>
          res.once(ctx.json(groupedProcessesMock))
        ),
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json(mockProcessInstances))
        )
      );

      const mockHistory = createMemoryHistory({
        initialEntries: ['/instances?incidents=true&active=true'],
      });

      instancesStore.init(true);
      await instancesStore.fetchInstancesFromFilters();

      render(<ListPanel />, {
        wrapper: createWrapper({
          history: mockHistory,
        }),
      });

      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));
      expect(screen.getByText('2251799813685594')).toBeInTheDocument();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      mockHistory.push(
        '/instances?incidents=true&active=true&process=bigVarProcess'
      );
      instancesStore.fetchInstancesFromFilters();

      await waitFor(() =>
        expect(instancesStore.state.status).toBe('refetching')
      );
      expect(
        screen.queryByTestId('listpanel-skeleton')
      ).not.toBeInTheDocument();
      expect(screen.getByTestId('instances-loader')).toBeInTheDocument();
      expect(
        screen.queryByText('There are no Instances matching this filter set')
      ).not.toBeInTheDocument();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );
      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      jest.runOnlyPendingTimers();

      mockServer.use(
        rest.post('/api/process-instances', (_, res, ctx) =>
          res.once(ctx.json({processInstances: [], totalCount: 0}))
        )
      );

      jest.runOnlyPendingTimers();

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
      expect(screen.queryByTestId('instances-loader')).not.toBeInTheDocument();

      expect(
        screen.getByText('There are no Instances matching this filter set')
      ).toBeInTheDocument();

      jest.clearAllTimers();
      jest.useRealTimers();
    });
  });
});
