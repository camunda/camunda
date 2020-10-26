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
import PropTypes from 'prop-types';
import {statisticsStore} from 'modules/stores/statistics';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

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

  const MockApp = ({history = createMemoryHistory()}) => (
    <ThemeProvider>
      <Router history={history}>
        <MetricPanel />
      </Router>
    </ThemeProvider>
  );

  MockApp.propTypes = {
    history: PropTypes.object,
  };

  it('should first display skeleton, then the statistics', async () => {
    render(<MockApp />);

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
    render(<MockApp />);

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
    render(<MockApp history={MOCK_HISTORY} />);

    fireEvent.click(screen.getByText('Instances with Incident'));

    const searchParams = new URLSearchParams(MOCK_HISTORY.location.search);

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(searchParams.get('filter')).toBe('{"incidents":true}');
  });

  it('should go to the correct page when clicking on active instances', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    render(<MockApp history={MOCK_HISTORY} />);

    fireEvent.click(screen.getByText('Active Instances'));

    const searchParams = new URLSearchParams(MOCK_HISTORY.location.search);

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(searchParams.get('filter')).toBe('{"active":true}');
  });

  it('should go to the correct page when clicking on total instances', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    render(<MockApp history={MOCK_HISTORY} />);

    statisticsStore.fetchStatistics();
    fireEvent.click(await screen.findByText('821 Running Instances in total'));

    const searchParams = new URLSearchParams(MOCK_HISTORY.location.search);

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(searchParams.get('filter')).toBe('{"active":true,"incidents":true}');
  });
});
