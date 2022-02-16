/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {render, screen, waitFor} from '@testing-library/react';
import {mockDecisionInstance} from 'modules/mocks/mockDecisionInstance';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionInstanceStore} from 'modules/stores/decisionInstance';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {DecisionPanel} from '.';
import {decisionXmlStore} from 'modules/stores/decisionXml';

describe('<DecisionPanel />', () => {
  beforeEach(() => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstance))
      ),
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      )
    );

    decisionXmlStore.init();
    decisionInstanceStore.fetchDecisionInstance('337423841237089');
  });

  afterEach(() => {
    decisionXmlStore.reset();
    decisionInstanceStore.reset();
  });

  it('should render decision table', async () => {
    render(<DecisionPanel />, {wrapper: ThemeProvider});

    await waitFor(() =>
      expect(screen.getByText('Decision View mock')).toBeInTheDocument()
    );
  });
});
