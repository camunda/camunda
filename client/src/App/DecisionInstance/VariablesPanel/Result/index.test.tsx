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
import {
  assignApproverGroup,
  invoiceClassification,
} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Result} from './index';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';

describe('<Result />', () => {
  beforeEach(() => {
    decisionInstanceDetailsStore.reset();
  });

  it('should show an error message', async () => {
    mockFetchDecisionInstance().withServerError();

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<Result />, {wrapper: ThemeProvider});

    expect(
      await screen.findByText(/data could not be fetched/i)
    ).toBeInTheDocument();
  });

  it('should show a loading spinner', async () => {
    mockFetchDecisionInstance().withServerError();

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
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<Result />, {wrapper: ThemeProvider});

    expect(
      await screen.findByTestId('results-json-viewer')
    ).toBeInTheDocument();
  });

  it('should show empty message for failed decision instances', async () => {
    mockFetchDecisionInstance().withSuccess(assignApproverGroup);

    decisionInstanceDetailsStore.fetchDecisionInstance('1');

    render(<Result />, {wrapper: ThemeProvider});

    expect(
      await screen.findByText(
        'No result available because the evaluation failed'
      )
    ).toBeInTheDocument();
  });
});
