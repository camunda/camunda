/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import {PAGE_TITLE} from 'modules/constants';
import {statisticsStore} from 'modules/stores/statistics';
import {Dashboard} from './index';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {mockIncidentsByError} from './IncidentsByError/index.setup';
import {mockWithSingleVersion} from './InstancesByWorkflow/index.setup';

type Props = {
  children?: React.ReactNode;
};

const Wrapper = ({children}: Props) => {
  return (
    <ThemeProvider>
      <MemoryRouter>{children}</MemoryRouter>
    </ThemeProvider>
  );
};

describe('Dashboard', () => {
  beforeEach(() => {
    statisticsStore.reset();
  });

  it('should render', async () => {
    mockServer.use(
      rest.get('/api/workflow-instances/core-statistics', (_, res, ctx) =>
        res.once(
          ctx.json({
            running: 821,
            active: 90,
            withIncidents: 731,
          })
        )
      ),
      rest.get('/api/incidents/byError', (_, res, ctx) =>
        res.once(ctx.json(mockIncidentsByError))
      ),
      rest.get('/api/incidents/byWorkflow', (_, res, ctx) =>
        res.once(ctx.json(mockWithSingleVersion))
      )
    );

    render(<Dashboard />, {wrapper: Wrapper});

    await statisticsStore.fetchStatistics();

    expect(document.title).toBe(PAGE_TITLE.DASHBOARD);
    expect(screen.getByText('Camunda Operate Dashboard')).toBeInTheDocument();
    expect(
      screen.getByText('821 Running Instances in total')
    ).toBeInTheDocument();
    expect(screen.getByText('Instances by Workflow')).toBeInTheDocument();
    expect(screen.getByText('Incidents by Error Message')).toBeInTheDocument();
  });
});
