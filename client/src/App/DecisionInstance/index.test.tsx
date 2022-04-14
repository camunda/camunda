/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {render, screen, waitFor, within} from 'modules/testing-library';
import {rest} from 'msw';
import {MemoryRouter, Route, Routes} from 'react-router-dom';
import {mockServer} from 'modules/mock-server/node';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDrdData} from 'modules/mocks/mockDrdData';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {DecisionInstance} from './';
import {decisionInstanceDetailsStore} from 'modules/stores/decisionInstanceDetails';
import {drdStore} from 'modules/stores/drd';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';

const DECISION_INSTANCE_ID = '4294980768';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => {
  return (
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/decisions/${DECISION_INSTANCE_ID}`]}>
        <Routes>
          <Route path="/decisions/:decisionInstanceId" element={children} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>
  );
};

describe('<DecisionInstance />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get(
        '/api/decision-instances/:decisionInstanceId/drd-data',
        (_, res, ctx) => res(ctx.json(mockDrdData))
      ),
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res(ctx.text(mockDmnXml))
      )
    );
  });

  afterEach(() => {
    decisionInstanceDetailsStore.reset();
    drdStore.reset();
  });

  it('should set page title', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    render(<DecisionInstance />, {wrapper: Wrapper});

    expect(
      await screen.findByText('Definitions Name Mock')
    ).toBeInTheDocument();

    expect(document.title).toBe(
      `Operate: Decision Instance ${DECISION_INSTANCE_ID} of ${invoiceClassification.decisionName}`
    );
  });

  it('should close DRD panel', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();

    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Close DRD Panel',
      })
    );

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();
    expect(screen.getByTestId('decision-instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel')
    ).toBeInTheDocument();
  });

  it('should maximize DRD panel and hide other panels', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Maximize DRD Panel',
      })
    );

    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('decision-instance-header')
    ).not.toBeInTheDocument();
    expect(screen.queryByTestId('decision-panel')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('decision-instance-variables-panel')
    ).not.toBeInTheDocument();
  });

  it('should minimize DRD panel', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Maximize DRD Panel',
      })
    );
    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Minimize DRD Panel',
      })
    );

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.getByTestId('decision-instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel')
    ).toBeInTheDocument();
  });

  it('should show DRD panel on header button click', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    const {user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Close DRD Panel',
      })
    );

    await waitFor(() =>
      expect(
        within(screen.getByTestId('decision-instance-header')).getByRole(
          'button',
          {name: /open decision requirements diagram/i}
        )
      ).toBeEnabled()
    );

    await user.click(
      within(screen.getByTestId('decision-instance-header')).getByRole(
        'button',
        {name: /open decision requirements diagram/i}
      )
    );

    expect(screen.getByTestId('drd-panel')).toBeInTheDocument();
    expect(screen.getByTestId('drd')).toBeInTheDocument();
    expect(screen.getByTestId('decision-instance-header')).toBeInTheDocument();
    expect(screen.getByTestId('decision-panel')).toBeInTheDocument();
    expect(
      screen.getByTestId('decision-instance-variables-panel')
    ).toBeInTheDocument();
  });

  it('should persist panel state', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:decisionInstanceId', (_, res, ctx) =>
        res(ctx.json(invoiceClassification))
      )
    );

    const {unmount, user} = render(<DecisionInstance />, {wrapper: Wrapper});

    await user.click(
      within(screen.getByTestId('drd')).getByRole('button', {
        name: 'Close DRD Panel',
      })
    );

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();

    unmount();
    render(<DecisionInstance />, {wrapper: Wrapper});

    expect(screen.queryByTestId('drd-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('drd')).not.toBeInTheDocument();
  });
});
