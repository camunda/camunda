/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  fireEvent,
  waitForElementToBeRemoved,
  within,
  waitFor,
} from '@testing-library/react';
import {createMemoryHistory} from 'history';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowInstances,
} from 'modules/testUtils';
import {filtersStore} from 'modules/stores/filters';
import CollapsablePanelContext from 'modules/contexts/CollapsablePanelContext';

import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';
import {ListPanel} from './index';
import {MemoryRouter} from 'react-router-dom';
import {DEFAULT_FILTER, DEFAULT_SORTING} from 'modules/constants';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {instancesStore} from 'modules/stores/instances';
import {NotificationProvider} from 'modules/notifications';
import {instancesDiagramStore} from 'modules/stores/instancesDiagram';

const locationMock = {pathname: '/instances'};
const historyMock = createMemoryHistory();

function createWrapper(expandOperationsMock = jest.fn()) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <NotificationProvider>
          <MemoryRouter>
            <CollapsablePanelContext.Provider
              value={{expandOperations: expandOperationsMock}}
            >
              {children}
            </CollapsablePanelContext.Provider>
          </MemoryRouter>
        </NotificationProvider>
      </ThemeProvider>
    );
  };
  return Wrapper;
}

describe('ListPanel', () => {
  afterEach(() => {
    filtersStore.reset();
    instancesStore.reset();
    instancesDiagramStore.reset();
  });

  describe('messages', () => {
    it('should display a message for empty list when no filter is selected', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(ctx.json({workflowInstances: [], totalCount: 0}))
        )
      );

      filtersStore.setFilter({});
      instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

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
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(ctx.json({workflowInstances: [], totalCount: 0}))
        )
      );

      filtersStore.setFilter(DEFAULT_FILTER);

      instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      render(<ListPanel />, {
        wrapper: createWrapper(),
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
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(ctx.json({workflowInstances: [], totalCount: 0}))
        )
      );
      instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      expect(screen.getByTestId('listpanel-skeleton')).toBeInTheDocument();
      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));
    });

    it('should render table body and footer', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(
              ctx.json({
                workflowInstances: [INSTANCE, ACTIVE_INSTANCE],
                totalCount: 0,
              })
            )
        )
      );

      filtersStore.setEntriesPerPage(10);
      instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      render(<ListPanel />, {wrapper: createWrapper()});
      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

      const withinFirstRow = within(
        screen.getByRole('row', {
          name: /instance 1/i,
        })
      );

      expect(withinFirstRow.getByText('someWorkflowName')).toBeInTheDocument();
      expect(
        screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
      ).toBeInTheDocument();
    });

    it('should render Footer when list is empty', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(ctx.json({workflowInstances: [], totalCount: 0}))
        )
      );

      instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

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
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(ctx.json({workflowInstances: [INSTANCE], totalCount: 1}))
      ),
      rest.post(
        '/api/workflow-instances/:instanceId/operation',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              id: '1',
              name: null,
              type: 'CANCEL_WORKFLOW_INSTANCE',
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

    filtersStore.setEntriesPerPage(10);
    instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });
    instancesStore.init();

    await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

    render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      screen.queryByTitle(/has scheduled operations/i)
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Cancel Instance 1'}));
    expect(
      screen.getByTitle(/instance 1 has scheduled operations/i)
    ).toBeInTheDocument();
    expect(screen.getByTestId('operation-spinner')).toBeInTheDocument();

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(ctx.json({workflowInstances: [INSTANCE], totalCount: 1}))
      ),
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(
            ctx.json({
              workflowInstances: [INSTANCE],
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
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
        ),
        rest.post('/api/workflow-instances/batch-operation', (_, res, ctx) =>
          res.once(ctx.json({}))
        )
      );

      filtersStore.setEntriesPerPage(10);
      instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      const mockExpandOperations = jest.fn();

      render(<ListPanel />, {
        wrapper: createWrapper(mockExpandOperations),
      });

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

      fireEvent.click(
        screen.getByRole('checkbox', {name: 'Select all instances'})
      );
      fireEvent.click(screen.getByRole('button', {name: /Apply Operation on/}));
      fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
      fireEvent.click(screen.getByRole('button', {name: 'Apply'}));
      expect(screen.getAllByTestId('operation-spinner').length).toBe(2);

      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
        )
      );

      instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));
      expect(screen.queryAllByTestId('operation-spinner').length).toBe(0);
      expect(mockExpandOperations).toHaveBeenCalledTimes(1);
    });

    it('should remove spinners after batch operation if a server error occurs', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
        ),
        rest.post('/api/workflow-instances/batch-operation', (_, res, ctx) =>
          res.once(ctx.status(500), ctx.json({}))
        ),
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(ctx.json({...mockWorkflowInstances, totalCount: 1000}))
        )
      );

      filtersStore.setEntriesPerPage(10);
      instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      const mockExpandOperations = jest.fn();
      render(<ListPanel />, {
        wrapper: createWrapper(mockExpandOperations),
      });

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

      fireEvent.click(
        screen.getByRole('checkbox', {name: 'Select all instances'})
      );
      fireEvent.click(screen.getByRole('button', {name: /Apply Operation on/}));
      fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
      fireEvent.click(screen.getByRole('button', {name: 'Apply'}));
      expect(screen.getAllByTestId('operation-spinner').length).toBe(2);
      await waitFor(() =>
        expect(screen.queryAllByTestId('operation-spinner').length).toBe(0)
      );

      // TODO: Normally this should not be necessary. all the ongoing requests should be canceled and state should not be updated if state is reset. this should also be removed when this problem is solved with https://jira.camunda.com/browse/OPE-1169
      await waitFor(() =>
        expect(instancesStore.state.filteredInstancesCount).toBe(1000)
      );

      expect(mockExpandOperations).not.toHaveBeenCalled();
    });

    it('should remove spinners after batch operation if a network error occurs', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
        ),
        rest.post('/api/workflow-instances/batch-operation', (_, res, ctx) =>
          res.networkError('A network error')
        ),
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(ctx.json({...mockWorkflowInstances, totalCount: 1000}))
        )
      );

      filtersStore.setEntriesPerPage(10);
      instancesStore.fetchInstances({
        firstResult: 0,
        maxResults: 50,
      });

      render(<ListPanel />, {
        wrapper: createWrapper(),
      });

      await waitFor(() => expect(instancesStore.state.status).toBe('fetched'));

      fireEvent.click(
        screen.getByRole('checkbox', {name: 'Select all instances'})
      );
      fireEvent.click(screen.getByRole('button', {name: /Apply Operation on/}));
      fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
      fireEvent.click(screen.getByRole('button', {name: 'Apply'}));
      expect(screen.getAllByTestId('operation-spinner').length).toBe(2);
      await waitFor(() =>
        expect(screen.queryAllByTestId('operation-spinner').length).toBe(0)
      );

      // TODO: Normally this should not be necessary. all the ongoing requests should be canceled and state should not be updated if state is reset. this should also be removed when this problem is solved with https://jira.camunda.com/browse/OPE-1169
      await waitFor(() =>
        expect(instancesStore.state.filteredInstancesCount).toBe(1000)
      );
    });
  });

  it('should show an error message', async () => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json({}), ctx.status(500))
      )
    );

    filtersStore.setEntriesPerPage(10);
    instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });

    const {unmount} = render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Instances could not be fetched')
    ).toBeInTheDocument();

    unmount();
    instancesStore.reset();

    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.networkError('A network error')
      )
    );

    await instancesStore.fetchInstances({
      firstResult: 0,
      maxResults: 50,
    });
    render(<ListPanel />, {
      wrapper: createWrapper(),
    });

    expect(
      await screen.findByText('Instances could not be fetched')
    ).toBeInTheDocument();
  });
});
