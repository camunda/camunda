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
} from '@testing-library/react';
import {createMemoryHistory} from 'history';

import {MetricPanel} from './index';
import PropTypes from 'prop-types';
import {statistics} from 'modules/stores/statistics';

jest.mock('modules/api/instances', () => ({
  fetchWorkflowCoreStatistics: jest.fn().mockImplementation(() => ({
    coreStatistics: {
      running: 821,
      active: 90,
      withIncidents: 731,
    },
  })),
}));

describe('<MetricPanel />', () => {
  beforeEach(() => {
    statistics.reset();
  });

  const MockApp = ({history = createMemoryHistory()}) => (
    <Router history={history}>
      <MetricPanel />
    </Router>
  );

  MockApp.propTypes = {
    history: PropTypes.object,
  };

  it('should first display skeleton, then the statistics', async () => {
    const {getByText, getByTestId} = render(<MockApp />);

    expect(getByTestId('instances-bar-skeleton')).toBeInTheDocument();
    expect(getByTestId('total-instances-link')).toHaveTextContent(
      'Running Instances in total'
    );

    statistics.fetchStatistics();

    await waitForElementToBeRemoved(() => [
      getByTestId('instances-bar-skeleton'),
    ]);
    expect(getByText('821 Running Instances in total')).toBeInTheDocument();
  });

  it('should show active instances and instances with incidents', async () => {
    const {getByText, findByTestId} = render(<MockApp />);

    statistics.fetchStatistics();
    expect(getByText('Instances with Incident')).toBeInTheDocument();
    expect(getByText('Active Instances')).toBeInTheDocument();
    expect(await findByTestId('incident-instances-badge')).toHaveTextContent(
      '731'
    );
    expect(await findByTestId('active-instances-badge')).toHaveTextContent(
      '90'
    );
  });

  it('should go to the correct page when clicking on instances with incidents', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    const {getByText} = render(<MockApp history={MOCK_HISTORY} />);

    fireEvent.click(getByText('Instances with Incident'));

    const searchParams = new URLSearchParams(MOCK_HISTORY.location.search);

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(searchParams.get('filter')).toBe('{"incidents":true}');
  });

  it('should go to the correct page when clicking on active instances', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    const {getByText} = render(<MockApp history={MOCK_HISTORY} />);

    fireEvent.click(getByText('Active Instances'));

    const searchParams = new URLSearchParams(MOCK_HISTORY.location.search);

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(searchParams.get('filter')).toBe('{"active":true}');
  });

  it('should go to the correct page when clicking on total instances', async () => {
    const MOCK_HISTORY = createMemoryHistory();
    const {findByText} = render(<MockApp history={MOCK_HISTORY} />);

    statistics.fetchStatistics();
    fireEvent.click(await findByText('821 Running Instances in total'));

    const searchParams = new URLSearchParams(MOCK_HISTORY.location.search);

    expect(MOCK_HISTORY.location.pathname).toBe('/instances');
    expect(searchParams.get('filter')).toBe('{"active":true,"incidents":true}');
  });
});
