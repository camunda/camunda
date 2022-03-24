/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {IncidentsBanner} from './index';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {render, screen} from '@testing-library/react';
import {incidentsStore} from 'modules/stores/incidents';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';

const mockProps = {
  onClick: jest.fn(),
  isArrowFlipped: false,
  isOpen: false,
};

type Props = {
  children?: React.ReactNode;
};

const {fetchIncidents} = incidentsStore;

const Wrapper: React.FC<Props> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/instances/1']}>
        <Routes>
          <Route path="/instances/:processInstanceId" element={children} />
        </Routes>
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

    await fetchIncidents('1');

    render(<IncidentsBanner {...mockProps} />, {wrapper: Wrapper});

    expect(screen.getByText('1 Incident occured')).toBeInTheDocument();
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

    await fetchIncidents('1');

    render(<IncidentsBanner {...mockProps} />, {wrapper: Wrapper});

    expect(screen.getByText('2 Incidents occured')).toBeInTheDocument();
  });
});
