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
import {VariablesPanel} from './index';
import {
  invoiceClassification,
  literalExpression,
} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';
import {useEffect} from 'react';

describe('<VariablesPanel />', () => {
  const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
    useEffect(() => decisionInstanceDetailsStore.reset);

    return <>{children}</>;
  };

  it('should have 2 tabs', () => {
    render(<VariablesPanel />, {wrapper: Wrapper});

    expect(
      screen.getByRole('tab', {
        name: /inputs and outputs/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('tab', {
        name: /result/i,
      }),
    ).toBeInTheDocument();
  });

  it('should render the default tab content', () => {
    render(<VariablesPanel />, {wrapper: Wrapper});

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      }),
    ).toBeInTheDocument();
  });

  it('should switch tab content', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    const {user} = render(<VariablesPanel />, {wrapper: Wrapper});

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('inputs-skeleton'),
    );

    await user.click(
      screen.getByRole('tab', {
        name: /result/i,
      }),
    );

    expect(screen.getByTestId('results-json-viewer')).toBeVisible();

    await user.click(
      screen.getByRole('tab', {
        name: /inputs and outputs/i,
      }),
    );

    expect(screen.getByTestId('results-json-viewer')).not.toBeVisible();

    expect(
      screen.getByRole('heading', {
        name: /inputs/i,
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: /outputs/i,
      }),
    ).toBeInTheDocument();
  });

  it('should hide input/output tab for literal expressions', async () => {
    mockFetchDecisionInstance().withSuccess(literalExpression);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<VariablesPanel />, {wrapper: Wrapper});

    expect(
      await screen.findByTestId('results-json-viewer'),
    ).toBeInTheDocument();

    expect(
      await screen.findByRole('heading', {
        name: /result/i,
      }),
    ).toBeInTheDocument();

    expect(
      screen.queryByRole('tab', {
        name: /inputs and outputs/i,
      }),
    ).not.toBeInTheDocument();
  });
});
