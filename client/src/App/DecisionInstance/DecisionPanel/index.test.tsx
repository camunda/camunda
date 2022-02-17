/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {render, screen} from '@testing-library/react';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockLiteralExpression} from 'modules/mocks/mockLiteralExpression';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {DecisionPanel} from '.';
import {decisionXmlStore} from 'modules/stores/decisionXml';

describe('<DecisionPanel />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      ),
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      )
    );

    decisionXmlStore.init();
  });

  afterEach(() => {
    decisionXmlStore.reset();
    decisionInstanceStore.reset();
  });

  it('should render decision table', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );

    decisionInstanceStore.fetchDecisionInstance('337423841237089');

    render(<DecisionPanel />, {wrapper: ThemeProvider});

    expect(
      await screen.findByText('DecisionTable view mock')
    ).toBeInTheDocument();
  });

  it('should render literal expression', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockLiteralExpression))
      )
    );

    decisionInstanceStore.fetchDecisionInstance('337423841237089');

    render(<DecisionPanel />, {wrapper: ThemeProvider});

    expect(
      await screen.findByText('LiteralExpression view mock')
    ).toBeInTheDocument();
  });
});
