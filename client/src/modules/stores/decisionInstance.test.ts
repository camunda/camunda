/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {mockDecisionInstance} from 'modules/mocks/mockDecisionInstance';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionInstanceStore} from './decisionInstance';

describe('decisionInstanceStore', () => {
  it('should initialize and reset ', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(mockDecisionInstance))
      ),
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      )
    );

    expect(decisionInstanceStore.state.status).toBe('initial');

    decisionInstanceStore.fetchDecisionInstance('22517947328274621');

    await waitFor(() =>
      expect(decisionInstanceStore.state.status).toBe('fetched')
    );
    expect(decisionInstanceStore.state.decisionInstance).toEqual(
      mockDecisionInstance
    );

    decisionInstanceStore.reset();

    expect(decisionInstanceStore.state.status).toBe('initial');
    expect(decisionInstanceStore.state.decisionInstance).toEqual(null);
  });
});
