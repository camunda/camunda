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
import {invoiceClassification} from 'modules/mocks/mockDecisionInstanceV2';
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

describe('<Header />', () => {
  beforeEach(() => {
    mockMe().withSuccess(createUser());
  });

  it('should show a loading skeleton', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    render(
      <Header
        decisionEvaluationInstanceKey={MOCK_DECISION_INSTANCE_ID}
        onChangeDrdPanelState={() => void 0}
      />,
      {wrapper: Wrapper},
    );

    expect(screen.getByTestId('instance-header-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.queryByTestId('instance-header-skeleton'),
    );
  });

  it('should show the decision instance details', async () => {
    mockFetchDecisionInstance().withSuccess(invoiceClassification);

    render(
      <Header
        decisionEvaluationInstanceKey={MOCK_DECISION_INSTANCE_ID}
        onChangeDrdPanelState={() => void 0}
      />,
      {wrapper: Wrapper},
    );

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
        name: invoiceClassification.decisionDefinitionName,
      }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('cell', {
        name: invoiceClassification.decisionEvaluationInstanceKey,
      }),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('link', {
        description: `View decision "${invoiceClassification.decisionDefinitionName} version ${invoiceClassification.decisionDefinitionVersion}" instances`,
      }),
    ).toHaveTextContent(
      invoiceClassification.decisionDefinitionVersion.toString(),
    );
    expect(
      screen.getByRole('cell', {
        name: '2022-01-20 13:26:52',
      }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', {
        description: `View process instance ${invoiceClassification.processInstanceKey}`,
      }),
    ).toBeInTheDocument();
  });
});
