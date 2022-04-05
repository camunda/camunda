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
import {mockServer} from 'modules/mock-server/node';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {rest} from 'msw';
import {Result} from './index';

describe('<Result />', () => {
  beforeEach(() => {
    decisionInstanceStore.reset();
  });

  it('should show an error message', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res.once(ctx.status(500))
      )
    );
    decisionInstanceStore.fetchDecisionInstance('1');

    render(<Result />, {wrapper: ThemeProvider});

    expect(
      await screen.findByText(/data could not be fetched/i)
    ).toBeInTheDocument();
  });

  it('should show a loading spinner', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res.once(ctx.status(500))
      )
    );

    render(<Result />, {
      wrapper: ThemeProvider,
    });

    decisionInstanceStore.fetchDecisionInstance('1');

    expect(screen.getByTestId('result-loading-spinner')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('result-loading-spinner')
    );
  });

  it('should show the result on the json editor', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );
    decisionInstanceStore.fetchDecisionInstance('1');

    render(<Result />, {wrapper: ThemeProvider});

    expect(
      await screen.findByTestId('results-json-viewer')
    ).toBeInTheDocument();
  });
});
