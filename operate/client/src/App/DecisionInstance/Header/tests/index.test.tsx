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
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Header} from '../index';
import {mockFetchDecisionInstance} from 'modules/mocks/api/decisionInstances/fetchDecisionInstance';
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

describe('<Header />', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser());
  });

  it('should show a loading skeleton', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance(
      MOCK_DECISION_INSTANCE_ID,
    );

    render(<Header />, {wrapper: Wrapper});

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('instance-header-skeleton'),
    );
  });

  it('should show the decision instance details', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    decisionInstanceDetailsStore.fetchDecisionInstance(
      MOCK_DECISION_INSTANCE_ID,
    );

    render(<Header />, {wrapper: Wrapper});

    expect(await screen.findByTestId('EVALUATED-icon')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /open decision requirements diagram/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /^decision name$/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /decision instance key/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /version/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /evaluation date/i}),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /process instance key/i}),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('cell', {
        name: invoiceClassification.decisionName,
      }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('cell', {name: MOCK_DECISION_INSTANCE_ID}),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('link', {
        description: `View decision "${invoiceClassification.decisionName} version ${invoiceClassification.decisionVersion}" instances`,
      }),
    ).toHaveTextContent(invoiceClassification.decisionVersion.toString());
    expect(
      screen.getByRole('cell', {
        name: '2022-01-20 13:26:52',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        description: `View process instance ${invoiceClassification.processInstanceId}`,
      }),
    ).toBeInTheDocument();
  });
});
