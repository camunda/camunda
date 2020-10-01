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

import {createMockDataManager} from 'modules/testHelpers/dataManager';
import {DataManagerProvider} from 'modules/DataManager';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';

import {groupedWorkflowsMock, mockWorkflowStatistics} from 'modules/testUtils';

import {filters} from 'modules/stores/filters';

import {INSTANCE, ACTIVE_INSTANCE} from './index.setup';

import {ListPanel} from './index';
import {MemoryRouter} from 'react-router-dom';
import PropTypes from 'prop-types';

import {DEFAULT_FILTER, DEFAULT_SORTING} from 'modules/constants';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {instances} from 'modules/stores/instances';

describe('ListPanel', () => {
  const locationMock = {pathname: '/instances'};
  const historyMock = createMemoryHistory();

  const Wrapper = ({children}) => {
    return (
      <MemoryRouter>
        <DataManagerProvider>
          <CollapsablePanelProvider>{children}</CollapsablePanelProvider>
        </DataManagerProvider>
      </MemoryRouter>
    );
  };
  Wrapper.propTypes = {
    children: PropTypes.oneOfType([
      PropTypes.arrayOf(PropTypes.node),
      PropTypes.node,
    ]),
  };

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

    filters.setUrlParameters(historyMock, locationMock);

    await filters.init();
    filters.setFilter(DEFAULT_FILTER);
    filters.setSorting(DEFAULT_SORTING);
    filters.setEntriesPerPage(10);
    createMockDataManager();
  });

  afterEach(() => {
    filters.reset();
    instances.reset();
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
      await instances.fetchInstances({});
      filters.setFilter({});
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
      await instances.fetchInstances({});

      filters.setFilter(DEFAULT_FILTER);
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
      instances.fetchInstances({});

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

      await instances.fetchInstances({});

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

      await instances.fetchInstances({});
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
      )
    );

    await instances.fetchInstances({});
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
});
