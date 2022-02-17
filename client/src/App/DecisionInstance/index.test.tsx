/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {render, screen, within} from '@testing-library/react';
import {rest} from 'msw';
import {MemoryRouter, Route} from 'react-router-dom';
import {mockServer} from 'modules/mock-server/node';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDrdData} from 'modules/mocks/mockDrdData';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {DecisionInstance} from './';
import userEvent from '@testing-library/user-event';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {drdStore} from 'modules/stores/drd';

const Wrapper: React.FC = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={['/decisions/4294980768']}>
        <Route path="/decisions/:decisionInstanceId">{children}</Route>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('<DecisionInstance />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get(
        '/api/decision-instances/:decisionInstanceId/drd-data',
        (_, res, ctx) => res(ctx.json(mockDrdData))
      )
    );
  });

  afterEach(() => {
    decisionInstanceStore.reset();
    drdStore.reset();
  });

  it('should close DRD panel', () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    render(<DecisionInstance />, {wrapper: Wrapper});

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();

    userEvent.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Close DRD Panel',
      })
    );

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();
    expect(screen.getByTestId('decision-instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel')
    ).toBeInTheDocument();
  });

  it('should maximize DRD panel and hide other panels', () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    render(<DecisionInstance />, {wrapper: Wrapper});

    userEvent.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Maximize DRD Panel',
      })
    );

    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('decision-instance-header')
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('decision-panel')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('decision-instance-variables-panel')
    ).not.toBeInTheDocument();
  });

  it('should minimize DRD panel', () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    render(<DecisionInstance />, {wrapper: Wrapper});

    userEvent.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Maximize DRD Panel',
      })
    );
    userEvent.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Minimize DRD Panel',
      })
    );

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.getByTestId('decision-instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel')
    ).toBeInTheDocument();
  });

  it('should show DRD panel on header button click', () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    render(<DecisionInstance />, {wrapper: Wrapper});

    userEvent.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Close DRD Panel',
      })
    );
    userEvent.click(
      within(screen.getByTestId('decision-instance-header')).getByRole(
        'button',
        {
          name: 'Show DRD Panel',
        }
      )
    );

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.getByTestId('decision-instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel')
    ).toBeInTheDocument();
  });

  it('should persist panel state', () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    const {unmount} = render(<DecisionInstance />, {wrapper: Wrapper});

    userEvent.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Close DRD Panel',
      })
    );

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();

    unmount();
    render(<DecisionInstance />, {wrapper: Wrapper});

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();
  });
});
