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
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Header} from '../index';
import {mockFetchDecisionInstance} from 'modules/mocks/api/v2/decisionInstances/fetchDecisionInstance';
import {Paths} from 'modules/Routes';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/mockQueryClient';
import {createUser} from 'modules/testUtils';
import {mockMe} from 'modules/mocks/api/v2/me';

const MOCK_DECISION_INSTANCE_ID = '123567';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    <MemoryRouter
      initialEntries={[Paths.decisionInstance(MOCK_DECISION_INSTANCE_ID)]}
    >
      <Routes>
        <Route path={Paths.decisionInstance()} element={children} />
      </Routes>
    </MemoryRouter>
  </QueryClientProvider>
);

describe('<Header /> - Business ID', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser());
  });

  it('should render a Business ID when present', async () => {
    mockFetchDecisionInstance().withSuccess({
      ...invoiceClassification,
      businessId: 'order-12345',
    });

    render(
      <Header
        decisionEvaluationInstanceKey={MOCK_DECISION_INSTANCE_ID}
        onChangeDrdPanelState={() => void 0}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.getByText('Business ID')).toBeInTheDocument();
    expect(screen.getByText('order-12345')).toBeInTheDocument();
  });

  it('should not render a Business ID when null', async () => {
    mockFetchDecisionInstance().withSuccess({
      ...invoiceClassification,
      businessId: null,
    });

    render(
      <Header
        decisionEvaluationInstanceKey={MOCK_DECISION_INSTANCE_ID}
        onChangeDrdPanelState={() => void 0}
      />,
      {wrapper: Wrapper},
    );

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('instance-header-skeleton'),
    );

    expect(screen.queryByText('Business ID')).not.toBeInTheDocument();
  });
});
