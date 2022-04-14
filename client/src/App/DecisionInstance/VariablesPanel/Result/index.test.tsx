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
} from 'modules/testing-library';
import {mockServer} from 'modules/mock-server/node';
import {
  assignApproverGroup,
  invoiceClassification,
} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {rest} from 'msw';
import {Result} from './index';

describe('<Result />', () => {
  beforeEach(() => {
    decisionInstanceDetailsStore.reset();
  });

  it('should show an error message', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res.once(ctx.status(500))
      )
    );
    decisionInstanceDetailsStore.fetchDecisionInstance('1');

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

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

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
    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<Result />, {wrapper: ThemeProvider});

    expect(
      await screen.findByTestId('results-json-viewer')
    ).toBeInTheDocument();
  });

  it('should show empty message for failed decision instances', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res.once(ctx.json(assignApproverGroup))
      )
    );
    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<Result />, {wrapper: ThemeProvider});

    expect(
      await screen.findByText(
        'No result available because the evaluation failed'
      )
    ).toBeInTheDocument();
  });
});
