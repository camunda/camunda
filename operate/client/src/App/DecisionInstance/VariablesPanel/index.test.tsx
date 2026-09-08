/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from 'modules/testing-library';
import {VariablesPanel} from './index';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {mockFetchDecisionInstance} from 'modules/mocks/api/v2/decisionInstances/fetchDecisionInstance';
import {
  invoiceClassification,
  literalExpression,
} from 'modules/mocks/mockDecisionInstance';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

describe('<VariablesPanel />', () => {
  it('should have 2 tabs', () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    render(
      <VariablesPanel
        decisionEvaluationInstanceKey="1"
        decisionDefinitionType="DECISION_TABLE"
      />,
      {wrapper: Wrapper},
    );

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
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    render(
      <VariablesPanel
        decisionEvaluationInstanceKey="1"
        decisionDefinitionType="DECISION_TABLE"
      />,
      {wrapper: Wrapper},
    );

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

    const {user} = render(
      <VariablesPanel
        decisionEvaluationInstanceKey="1"
        decisionDefinitionType="DECISION_TABLE"
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('inputs-skeleton'),
    );

    await user.click(
      screen.getByRole('tab', {
        name: /result/i,
      }),
    );

    expect(await screen.findByTestId('monaco-editor')).toBeInTheDocument();

    await user.click(
      screen.getByRole('tab', {
        name: /inputs and outputs/i,
      }),
    );

    expect(screen.getByTestId('monaco-editor')).not.toBeVisible();

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

    render(
      <VariablesPanel
        decisionEvaluationInstanceKey="1"
        decisionDefinitionType="LITERAL_EXPRESSION"
      />,
      {wrapper: Wrapper},
    );

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

  it('should keep the Result tab selected when switching back to a decision with both tabs', async () => {
    // A fresh mock is registered before each render below, since switching
    // decisionEvaluationInstanceKey triggers a new GET request per render
    // (mockFetchDecisionInstance's handlers are one-time use).
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    const {user, rerender} = render(
      <VariablesPanel
        decisionEvaluationInstanceKey="1"
        decisionDefinitionType="DECISION_TABLE"
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('inputs-skeleton'),
    );

    await user.click(screen.getByRole('tab', {name: /result/i}));
    expect(await screen.findByTestId('monaco-editor')).toBeVisible();

    // Switch to a literal expression decision, which only has a Result tab.
    mockFetchDecisionInstance().withSuccess(literalExpression);
    rerender(
      <VariablesPanel
        decisionEvaluationInstanceKey="2"
        decisionDefinitionType="LITERAL_EXPRESSION"
      />,
    );

    expect(
      await screen.findByTestId('results-json-viewer'),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('tab', {name: /inputs and outputs/i}),
    ).not.toBeInTheDocument();

    // Switch back to a decision with both tabs — Result should still be
    // selected instead of resetting to Inputs and Outputs.
    mockFetchDecisionInstance().withSuccess(invoiceClassification);
    rerender(
      <VariablesPanel
        decisionEvaluationInstanceKey="1"
        decisionDefinitionType="DECISION_TABLE"
      />,
    );

    expect(
      await screen.findByRole('tab', {name: /result/i}),
    ).toHaveAttribute('aria-selected', 'true');
    expect(
      screen.getByRole('tab', {name: /inputs and outputs/i}),
    ).toHaveAttribute('aria-selected', 'false');
  });
});
