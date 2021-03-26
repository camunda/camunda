/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {IncidentsBanner} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {EXPAND_STATE} from 'modules/constants';
import {MemoryRouter, Route} from 'react-router-dom';
import {render, screen} from '@testing-library/react';
import {incidentsStore} from 'modules/stores/incidents';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

const mockProps = {
  onClick: jest.fn(),
  isArrowFlipped: false,
  expandState: 'DEFAULT',
  isOpen: false,
};

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Route path="/instances/:processInstanceId">{children}</Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('IncidentsBanner', () => {
  it('should display incidents banner if banner is not collapsed', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(
          ctx.json({
            count: 1,
          })
        )
      )
    );

    await incidentsStore.fetchIncidents('1');

    render(<IncidentsBanner {...mockProps} />, {wrapper: Wrapper});

    expect(
      screen.getByText('There is 1 Incident in Instance 1')
    ).toBeInTheDocument();
  });

  it('should not display incidents banner if panel is collapsed', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(
          ctx.json({
            count: 1,
          })
        )
      )
    );

    await incidentsStore.fetchIncidents('1');

    render(
      <IncidentsBanner {...mockProps} expandState={EXPAND_STATE.COLLAPSED} />,
      {wrapper: Wrapper}
    );

    expect(
      screen.queryByText('There is 1 Incident in Instance 1')
    ).not.toBeInTheDocument();
  });

  it('should show the right text for more than 1 incident', async () => {
    mockServer.use(
      rest.get('/api/process-instances/:instanceId/incidents', (_, res, ctx) =>
        res.once(
          ctx.json({
            count: 2,
          })
        )
      )
    );

    await incidentsStore.fetchIncidents('1');

    render(<IncidentsBanner {...mockProps} />, {wrapper: Wrapper});

    expect(
      screen.getByText('There are 2 Incidents in Instance 1')
    ).toBeInTheDocument();
  });
});
