/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {PAGE_TITLE} from 'modules/constants';
import {statistics} from 'modules/stores/statistics';
import {fetchWorkflowCoreStatistics} from 'modules/api/instances';

import {Dashboard} from './index';

import PropTypes from 'prop-types';

jest.mock('modules/api/instances');

const Wrapper = ({children}) => {
  return <MemoryRouter>{children} </MemoryRouter>;
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};
describe('Dashboard', () => {
  beforeEach(() => {
    statistics.reset();
  });
  afterEach(() => {
    fetchWorkflowCoreStatistics.mockReset();
  });
  it('should render', async () => {
    fetchWorkflowCoreStatistics.mockResolvedValueOnce({
      coreStatistics: {
        running: 821,
        active: 90,
        withIncidents: 731,
      },
    });
    render(<Dashboard />, {wrapper: Wrapper});

    await statistics.fetchStatistics();
    expect(document.title).toBe(PAGE_TITLE.DASHBOARD);
    expect(screen.getByText('Camunda Operate Dashboard')).toBeInTheDocument();
    expect(
      screen.getByText('821 Running Instances in total')
    ).toBeInTheDocument();
    expect(screen.getByText('Instances by Workflow')).toBeInTheDocument();
    expect(screen.getByText('Incidents by Error Message')).toBeInTheDocument();
  });

  it('should display error', async () => {
    fetchWorkflowCoreStatistics.mockResolvedValueOnce({
      coreStatistics: {
        error: 'an error occured',
      },
    });
    render(<Dashboard />, {wrapper: Wrapper});

    await statistics.fetchStatistics();
    expect(screen.queryByText('Metric Panel')).not.toBeInTheDocument();
    expect(
      screen.getByText('Workflow statistics could not be fetched.')
    ).toBeInTheDocument();
  });
});
