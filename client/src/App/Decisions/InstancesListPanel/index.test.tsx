/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  render,
  screen,
  within,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {InstancesListPanel} from './';
import {decisionInstancesStore} from 'modules/stores/decisionInstances';
import {mockDecisionInstances} from 'modules/mocks/mockDecisionInstances';

describe('Decisions List', () => {
  afterEach(() => {
    decisionInstancesStore.reset();
  });

  it('should initially render skeleton', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    render(<InstancesListPanel />, {wrapper: ThemeProvider});

    expect(screen.getByTestId('table-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));
  });

  it('should render error message', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.status(500))
      )
    );

    render(<InstancesListPanel />, {wrapper: ThemeProvider});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(screen.getByText('Data could not be fetched')).toBeInTheDocument();
  });

  it('should render empty message', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json({decisionInstances: []}))
      )
    );

    render(<InstancesListPanel />, {wrapper: ThemeProvider});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(
      screen.getByText('There are no Instances matching this filter set')
    ).toBeInTheDocument();
  });

  it('should render decision instances', async () => {
    mockServer.use(
      rest.post('/api/decision-instances', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstances))
      )
    );

    render(<InstancesListPanel />, {wrapper: ThemeProvider});

    await waitForElementToBeRemoved(screen.getByTestId('table-skeleton'));

    expect(
      screen.getByRole('columnheader', {
        name: 'Decision',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Decision Instance Id',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Version',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Evaluation Time',
      })
    ).toBeInTheDocument();

    expect(
      screen.getByRole('columnheader', {
        name: 'Process Instance Id',
      })
    ).toBeInTheDocument();

    const rows = screen.getAllByRole('row');
    expect(rows).toHaveLength(29);

    const [, firstDecisionInstance, secondDecisionInstance] = rows;
    expect(
      within(firstDecisionInstance).getByText('test decision instance 1')
    ).toBeInTheDocument();
    expect(
      within(firstDecisionInstance).getByTestId(
        'COMPLETED-icon-2251799813689541'
      )
    ).toBeInTheDocument();

    expect(
      within(secondDecisionInstance).getByText('test decision instance 2')
    ).toBeInTheDocument();
    expect(
      within(secondDecisionInstance).getByTestId('FAILED-icon-2251799813689542')
    ).toBeInTheDocument();
  });
});
