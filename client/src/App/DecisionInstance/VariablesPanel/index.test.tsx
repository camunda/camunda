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
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {VariablesPanel} from './index';
import {
  invoiceClassification,
  literalExpression,
} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';

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
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    const {user} = render(<VariablesPanel />, {wrapper: ThemeProvider});

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('inputs-skeleton')
    );

    await user.click(
      screen.getByRole('button', {
        name: /result/i,
      })
    );

    expect(screen.getByTestId('results-json-viewer')).toBeInTheDocument();

    await user.click(
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

  it('should hide input/output tab for literal expressions', async () => {
    mockFetchDecisionInstance().withSuccess(literalExpression);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<VariablesPanel />, {wrapper: ThemeProvider});

    expect(
      await screen.findByTestId('results-json-viewer')
    ).toBeInTheDocument();

    expect(
      screen.getByRole('heading', {
        name: /result/i,
      })
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('button', {
        name: /inputs and outputs/i,
      })
    ).not.toBeInTheDocument();
  });
});
