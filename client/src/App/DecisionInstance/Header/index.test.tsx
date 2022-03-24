/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';
import {mockServer} from 'modules/mock-server/node';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {rest} from 'msw';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {Header} from './index';

const MOCK_DECISION_INSTANCE_ID = '123567';

const Wrapper: React.FC = ({children}) => (
  <ThemeProvider>
    <MemoryRouter initialEntries={[`/decisions/${MOCK_DECISION_INSTANCE_ID}`]}>
      <Routes>
        <Route path="/decisions/:decisionInstanceId" element={children} />
      </Routes>
    </MemoryRouter>
  </ThemeProvider>
);

describe('<Header />', () => {
  it('should show a loading skeleton', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );

    decisionInstanceStore.fetchDecisionInstance(MOCK_DECISION_INSTANCE_ID);

    render(<Header />, {wrapper: Wrapper});

    expect(screen.getByTestId('details-skeleton')).toBeInTheDocument();

    await waitForElementToBeRemoved(() =>
      screen.getByTestId('details-skeleton')
    );
  });

  it('should show the decision instance details', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );

    decisionInstanceStore.fetchDecisionInstance(MOCK_DECISION_INSTANCE_ID);

    render(<Header />, {wrapper: Wrapper});

    expect(screen.getByTestId('completed-icon')).toBeInTheDocument();
    expect(
      screen.getByRole('button', {name: /open decision requirements diagram/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /^decision$/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /decision instance id/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /version/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /evaluation date/i})
    ).toBeInTheDocument();
    expect(
      screen.getByRole('columnheader', {name: /process instance id/i})
    ).toBeInTheDocument();
    expect(
      await screen.findByRole('cell', {
        name: invoiceClassification.decisionName,
      })
    );
    expect(await screen.findByRole('cell', {name: MOCK_DECISION_INSTANCE_ID}));
    expect(
      await screen.findByRole('cell', {
        name: invoiceClassification.decisionVersion,
      })
    );
    expect(
      await screen.findByRole('cell', {
        name: '2018-12-12 00:00:00',
      })
    );
    expect(
      await screen.findByRole('link', {
        name: `View process instance ${invoiceClassification.processInstanceId}`,
      })
    );
  });
});
