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

import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';
import {ListPanel} from './index';
import {MemoryRouter} from 'react-router-dom';
import {DEFAULT_FILTER, DEFAULT_SORTING} from 'modules/constants';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {instancesStore} from 'modules/stores/instances';
import {NotificationProvider} from 'modules/notifications';

const locationMock = {pathname: '/instances'};
const historyMock = createMemoryHistory();

const Wrapper: React.FC = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter>
        <NotificationProvider>
          <CollapsablePanelProvider>{children}</CollapsablePanelProvider>
        </NotificationProvider>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('ListPanel', () => {
  beforeEach(async () => {
    mockServer.use(
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );

    filtersStore.setUrlParameters(historyMock, locationMock);

    await filtersStore.init();
    filtersStore.setFilter(DEFAULT_FILTER);
    filtersStore.setSorting(DEFAULT_SORTING);
    filtersStore.setEntriesPerPage(10);
  });

  afterEach(() => {
    filtersStore.reset();
    instancesStore.reset();
  });

  describe('messages', () => {
    it('should display a message for empty list when filter has no state', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(ctx.json({workflowInstances: [], totalCount: 0}))
        )
      );
      await instancesStore.fetchInstances();
      filtersStore.setFilter({});
      render(<ListPanel />, {
        wrapper: Wrapper,
      });
      expect(
        screen.getByText('There are no instances matching this filter set.')
      ).toBeInTheDocument();
      expect(
        screen.getByText(
          'To see some results, select at least one instance state.'
        )
      ).toBeInTheDocument();
    });

    it('should display an empty list message when filter has at least one state', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) =>
            res.once(ctx.json({workflowInstances: [], totalCount: 0}))
        )
      );
      await instancesStore.fetchInstances();

      filtersStore.setFilter(DEFAULT_FILTER);
      render(<ListPanel />, {
        wrapper: Wrapper,
      });
      expect(
        screen.getByText('There are no instances matching this filter set.')
      ).toBeInTheDocument();
      expect(
        screen.queryByText(
          'To see some results, select at least one instance state.'
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
      instancesStore.fetchInstances();

      render(<ListPanel />, {
        wrapper: Wrapper,
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

      await instancesStore.fetchInstances();

      render(<ListPanel />, {wrapper: Wrapper});
      expect(screen.getByTestId('instances-list')).toBeInTheDocument();
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

      await instancesStore.fetchInstances();
      render(<ListPanel />, {
        wrapper: Wrapper,
      });

      expect(
        screen.getByText(/^© Camunda Services GmbH \d{4}. All rights reserved./)
      ).toBeInTheDocument();
    });
  });

  it('should start operation on an instance from list', async () => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) =>
          res.once(ctx.json({workflowInstances: [INSTANCE], totalCount: 0}))
      ),
      rest.post(
        '/api/workflow-instances/:instanceId/operation',
        (_, res, ctx) => res.once(ctx.json({}))
      )
    );

    await instancesStore.fetchInstances();
    render(<ListPanel />, {
      wrapper: Wrapper,
    });

    expect(
      screen.queryByTitle(/has scheduled operations/i)
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', {name: 'Cancel Instance 1'}));
    expect(
      screen.getByTitle(/instance 1 has scheduled operations/i)
    ).toBeInTheDocument();
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
      instancesStore.fetchInstances();

      render(<ListPanel />, {
        wrapper: Wrapper,
      });

      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

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
      instancesStore.fetchInstances();
      await waitForElementToBeRemoved(
        screen.getAllByTestId('operation-spinner')
      );
    });

    it('should remove spinners after batch operation if a server error occurs', async () => {
      mockServer.use(
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
        ),
        rest.post('/api/workflow-instances/batch-operation', (_, res, ctx) =>
          res.once(ctx.status(500), ctx.json({error: 'An error occured'}))
        ),
        rest.post(
          '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
          (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
        )
      );

      instancesStore.fetchInstances();

      render(<ListPanel />, {
        wrapper: Wrapper,
      });

      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

      fireEvent.click(
        screen.getByRole('checkbox', {name: 'Select all instances'})
      );
      fireEvent.click(screen.getByRole('button', {name: /Apply Operation on/}));
      fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
      fireEvent.click(screen.getByRole('button', {name: 'Apply'}));
      expect(screen.getAllByTestId('operation-spinner').length).toBe(2);
      await waitForElementToBeRemoved(
        screen.getAllByTestId('operation-spinner')
      );
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
          (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
        )
      );

      instancesStore.fetchInstances();

      render(<ListPanel />, {
        wrapper: Wrapper,
      });

      await waitForElementToBeRemoved(screen.getByTestId('listpanel-skeleton'));

      fireEvent.click(
        screen.getByRole('checkbox', {name: 'Select all instances'})
      );
      fireEvent.click(screen.getByRole('button', {name: /Apply Operation on/}));
      fireEvent.click(screen.getByRole('button', {name: 'Cancel'}));
      fireEvent.click(screen.getByRole('button', {name: 'Apply'}));
      expect(screen.getAllByTestId('operation-spinner').length).toBe(2);
      await waitForElementToBeRemoved(
        screen.getAllByTestId('operation-spinner')
      );
    });
  });
});
