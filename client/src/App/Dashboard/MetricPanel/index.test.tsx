/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {Router} from 'react-router-dom';
import {
  render,
  waitForElementToBeRemoved,
  fireEvent,
  screen,
} from '@testing-library/react';
import {createMemoryHistory} from 'history';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MetricPanel} from './index';
import {statisticsStore} from 'modules/stores/statistics';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

function createWrapper(history = createMemoryHistory()) {
  const Wrapper: React.FC = ({children}) => {
    return (
      <ThemeProvider>
        <Router history={history}>{children}</Router>
      </ThemeProvider>
    );
  };

  return Wrapper;
}

describe('<MetricPanel />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            running: 821,
            active: 90,
            withIncidents: 731,
          })
        )
      )
    );

    statisticsStore.reset();
  });

  it('should first display skeleton, then the statistics', async () => {
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    expect(screen.getByTestId('instances-bar-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('total-instances-link')).toHaveTextContent(
      'Running Instances in total'
    );

    statisticsStore.fetchStatistics();

    await waitForElementToBeRemoved(() => [
      screen.getByTestId('instances-bar-skeleton'),
    ]);
    expect(
      screen.getByText('821 Running Instances in total')
    ).toBeInTheDocument();
  });

  it('should show active instances and instances with incidents', async () => {
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    statisticsStore.fetchStatistics();
    expect(screen.getByText('Instances with Incident')).toBeInTheDocument();
    expect(screen.getByText('Active Instances')).toBeInTheDocument();
    expect(
      await screen.findByTestId('incident-instances-badge')
    ).toHaveTextContent('731');
    expect(
      await screen.findByTestId('active-instances-badge')
    ).toHaveTextContent('90');
  });

  it('should go to the correct page when clicking on instances with incidents', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    render(<MetricPanel />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    fireEvent.click(screen.getByText('Instances with Incident'));

    const searchParams = new URLSearchParams(MOCK_HISTORY.location.search);

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(searchParams.get('filter')).toBe('{"incidents":true}');
  });

  it('should go to the correct page when clicking on active instances', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    render(<MetricPanel />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    fireEvent.click(screen.getByText('Active Instances'));

    const searchParams = new URLSearchParams(MOCK_HISTORY.location.search);

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(searchParams.get('filter')).toBe('{"active":true}');
  });

  it('should go to the correct page when clicking on total instances', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    render(<MetricPanel />, {
      wrapper: createWrapper(MOCK_HISTORY),
    });

    statisticsStore.fetchStatistics();
    fireEvent.click(await screen.findByText('821 Running Instances in total'));

    const searchParams = new URLSearchParams(MOCK_HISTORY.location.search);

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(searchParams.get('filter')).toBe('{"active":true,"incidents":true}');
  });

  it('should handle server errors', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.json({}))
      )
    );
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    statisticsStore.fetchStatistics();

    expect(
      await screen.findByText('Workflow statistics could not be fetched')
    ).toBeInTheDocument();
  });

  it('should handle networks errors', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/core-statistics', (_, res) =>
        res.networkError('A network error')
      )
    );
    render(<MetricPanel />, {
      wrapper: createWrapper(),
    });

    statisticsStore.fetchStatistics();

    expect(
      await screen.findByText('Workflow statistics could not be fetched')
    ).toBeInTheDocument();
  });
});
