/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {VariablesPanel} from './index';
import {mockServer} from 'modules/mock-server/node';
import {rest} from 'msw';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {getStateLocally, storeStateLocally} from 'modules/utils/localStorage';

describe('<VariablesPanel />', () => {
  it('should have 2 tabs', () => {
    render(<VariablesPanel />, {wrapper: ThemeProvider});

    expect(
      screen.getByRole('button', {
        name: /inputs and outputs/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', {
        name: /result/i,
      })
    ).toBeInTheDocument();
  });

  it('should render the default tab content', () => {
    render(<VariablesPanel />, {wrapper: ThemeProvider});

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      })
    ).toBeInTheDocument();
  });

  it('should switch tab content', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );

    decisionInstanceStore.fetchDecisionInstance('1');

    render(<VariablesPanel />, {wrapper: ThemeProvider});

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('inputs-skeleton')
    );

    userEvent.click(
      screen.getByRole('button', {
        name: /result/i,
      })
    );

    expect(screen.getByTestId('results-json-viewer')).toBeInTheDocument();

    userEvent.click(
      screen.getByRole('button', {
        name: /inputs and outputs/i,
      })
    );

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      })
    ).toBeInTheDocument();
  });

  it('should use persisted tab and should persist selected tab', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );
    decisionInstanceStore.fetchDecisionInstance('1');
    storeStateLocally({
      decisionInstanceTab: 'result',
    });

    render(<VariablesPanel />, {wrapper: ThemeProvider});

    expect(
      await screen.findByTestId('results-json-viewer')
    ).toBeInTheDocument();

    userEvent.click(
      screen.getByRole('button', {
        name: /inputs and outputs/i,
      })
    );

    expect(getStateLocally()?.decisionInstanceTab).toBe('inputs-and-outputs');
  });
});
