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
  within,
} from 'modules/testing-library';
import {
  assignApproverGroup,
  assignApproverGroupWithoutVariables,
  invoiceClassification,
} from 'modules/mocks/mockDecisionInstance';
import {InputsAndOutputs} from './index';
import {mockFetchDecisionInstance} from 'modules/mocks/api/v2/decisionInstances/fetchDecisionInstance';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

describe('<InputsAndOutputs />', () => {
  it('should have section panels', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    render(<InputsAndOutputs decisionEvaluationInstanceKey="1" />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('inputs-skeleton'),
    );

    expect(screen.getByRole('heading', {name: /inputs/i})).toBeInTheDocument();
    expect(screen.getByRole('heading', {name: /outputs/i})).toBeInTheDocument();
  });

  it('should show a loading skeleton', async () => {
    mockFetchDecisionInstance().withServerError();

    render(<InputsAndOutputs decisionEvaluationInstanceKey="1" />, {
      wrapper: Wrapper,
    });

    expect(screen.getByTestId('inputs-skeleton')).toBeInTheDocument();
    expect(screen.getByTestId('outputs-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('inputs-skeleton'),
    );

    expect(screen.queryByTestId('inputs-skeleton')).not.toBeInTheDocument();
    expect(screen.queryByTestId('outputs-skeleton')).not.toBeInTheDocument();
  });

  it('should show empty message for failed decision instances with variables', async () => {
    mockFetchDecisionInstance().withSuccess(assignApproverGroup);

    render(<InputsAndOutputs decisionEvaluationInstanceKey="1" />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText(
        'No output available because the evaluation failed',
      ),
    ).toBeInTheDocument();

    expect(
      screen.queryByText('No input available because the evaluation failed'),
    ).not.toBeInTheDocument();
  });

  it('should show empty message for failed decision instances without variables', async () => {
    mockFetchDecisionInstance().withSuccess(
      assignApproverGroupWithoutVariables,
    );

    render(<InputsAndOutputs decisionEvaluationInstanceKey="1" />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findByText(
        'No output available because the evaluation failed',
      ),
    ).toBeInTheDocument();

    expect(
      screen.getByText('No input available because the evaluation failed'),
    ).toBeInTheDocument();
  });

  it('should load inputs and outputs', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    render(<InputsAndOutputs decisionEvaluationInstanceKey="1" />, {
      wrapper: Wrapper,
    });

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('inputs-skeleton'),
    );

    const [inputsTable, outputsTable] = screen.getAllByRole('table');

    const [inputsNameColumnHeader, inputsValueColumnHeader] = within(
      inputsTable!,
    ).getAllByRole('columnheader');
    const [
      outputsRuleColumnHeader,
      outputsNameColumnHeader,
      outputsValueColumnHeader,
    ] = within(outputsTable!).getAllByRole('columnheader');
    const [, inputsFirstTableBodyRow] = within(inputsTable!).getAllByRole(
      'row',
    );
    const [, outputsFirstTableBodyRow] = within(outputsTable!).getAllByRole(
      'row',
    );
    const [inputsNameCell, inputsValueCell] = within(
      inputsFirstTableBodyRow!,
    ).getAllByRole('cell');
    const [outputsRuleCell, outputsNameCell, outputsValueCell] = within(
      outputsFirstTableBodyRow!,
    ).getAllByRole('cell');

    expect(inputsNameColumnHeader).toBeInTheDocument();
    expect(inputsValueColumnHeader).toBeInTheDocument();
    expect(outputsRuleColumnHeader).toBeInTheDocument();
    expect(outputsNameColumnHeader).toBeInTheDocument();
    expect(outputsValueColumnHeader).toBeInTheDocument();

    expect(inputsNameCell).toBeInTheDocument();
    expect(inputsValueCell).toBeInTheDocument();
    expect(outputsRuleCell).toBeInTheDocument();
    expect(outputsNameCell).toBeInTheDocument();
    expect(outputsValueCell).toBeInTheDocument();
  });

  it('should show an error', async () => {
    mockFetchDecisionInstance().withServerError();

    render(<InputsAndOutputs decisionEvaluationInstanceKey="1" />, {
      wrapper: Wrapper,
    });

    expect(
      await screen.findAllByText(/data could not be fetched/i),
    ).toHaveLength(2);
  });
});
