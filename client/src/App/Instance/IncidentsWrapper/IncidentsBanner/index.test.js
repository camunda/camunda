/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {IncidentsBanner} from './index';
import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {EXPAND_STATE} from 'modules/constants';
import {MemoryRouter, Route} from 'react-router-dom';
import {render, screen} from '@testing-library/react';
import PropTypes from 'prop-types';
import {incidents} from 'modules/stores/incidents';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

const mockProps = {
  onClick: jest.fn(),
  isArrowFlipped: false,
  expandState: 'DEFAULT',
};

const Wrapper = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Route path="/instances/:id">{children}</Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

describe('IncidentsBanner', () => {
  it('should display incidents banner if banner is not collapsed', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(
          ctx.json({
            count: 1,
          })
        )
      )
    );

    await incidents.fetchIncidents(1);

    render(<IncidentsBanner {...mockProps} />, {wrapper: Wrapper});

    expect(
      screen.getByText('There is 1 Incident in Instance 1.')
    ).toBeInTheDocument();
  });

  it('should not display incidents banner if panel is collapsed', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(
          ctx.json({
            count: 1,
          })
        )
      )
    );

    await incidents.fetchIncidents(1);

    render(
      <IncidentsBanner {...mockProps} expandState={EXPAND_STATE.COLLAPSED} />,
      {wrapper: Wrapper}
    );

    expect(
      screen.queryByText('There is 1 Incident in Instance 1.')
    ).not.toBeInTheDocument();
  });

  it('should show the right text for more than 1 incident', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(
          ctx.json({
            count: 2,
          })
        )
      )
    );

    await incidents.fetchIncidents(1);

    render(<IncidentsBanner {...mockProps} />, {wrapper: Wrapper});

    expect(
      screen.getByText('There are 2 Incidents in Instance 1.')
    ).toBeInTheDocument();
  });
});
