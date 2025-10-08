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
import {
  assignApproverGroup,
  invoiceClassification,
} from 'modules/mocks/mockDecisionInstance';
import {Result} from './index';
import {mockFetchDecisionInstance} from 'modules/mocks/api/v2/decisionInstances/fetchDecisionInstance';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    {children}
  </QueryClientProvider>
);

describe('<Result />', () => {
  it('should show an error message', async () => {
    mockFetchDecisionInstance().withServerError();

    render(<Result decisionEvaluationInstanceKey="1" />, {wrapper: Wrapper});

    expect(
      await screen.findByText(/data could not be fetched/i),
    ).toBeInTheDocument();
  });

  it('should show a loading spinner', async () => {
    mockFetchDecisionInstance().withServerError();

    render(<Result decisionEvaluationInstanceKey="1" />, {wrapper: Wrapper});

    expect(screen.getByTestId('result-loading-spinner')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('result-loading-spinner'),
    );
  });

  it('should show the result on the json editor', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    render(<Result decisionEvaluationInstanceKey="1" />, {wrapper: Wrapper});

    expect(
      await screen.findByTestId('results-json-viewer'),
    ).toBeInTheDocument();
  });

  it('should show empty message for failed decision instances', async () => {
    mockFetchDecisionInstance().withSuccess(assignApproverGroup);

    render(<Result decisionEvaluationInstanceKey="1" />, {wrapper: Wrapper});

    expect(
      await screen.findByText(
        'No result available because the evaluation failed',
      ),
    ).toBeInTheDocument();
  });
});
