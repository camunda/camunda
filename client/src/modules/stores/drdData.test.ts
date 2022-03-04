/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {waitFor} from '@testing-library/react';
import {mockServer} from 'modules/mock-server/node';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDrdData} from 'modules/mocks/mockDrdData';
import {rest} from 'msw';
import {decisionInstanceStore} from './decisionInstance';
import {drdDataStore} from './drdData';

describe('drdDataStore', () => {
  afterEach(() => {
    drdDataStore.reset();
    decisionInstanceStore.reset();
  });

  it('should fetch DRD data', async () => {
    mockServer.use(
      rest.get(
        '/api/decision-instances/:decisionInstancdId/drd-data',
        (_, res, ctx) => res.once(ctx.json(mockDrdData))
      )
    );

    drdDataStore.fetchDrdData('1');
    await waitFor(() => expect(drdDataStore.state.status).toBe('fetched'));
    expect(drdDataStore.state.drdData).toEqual(mockDrdData);
  });

  it('should catch error', async () => {
    mockServer.use(
      rest.get(
        '/api/decision-instances/:decisionInstancdId/drd-data',
        (_, res, ctx) =>
          res.once(ctx.status(500), ctx.json({error: 'an error occured'}))
      )
    );

    drdDataStore.fetchDrdData('1');
    await waitFor(() => expect(drdDataStore.state.status).toBe('error'));
    expect(drdDataStore.state.drdData).toEqual(null);
  });

  it('should get current decision', async () => {
    mockServer.use(
      rest.get(
        '/api/decision-instances/:decisionInstancdId/drd-data',
        (_, res, ctx) => res.once(ctx.json(mockDrdData))
      ),
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );

    decisionInstanceStore.fetchDecisionInstance('1');
    drdDataStore.fetchDrdData('1');

    await waitFor(() => expect(drdDataStore.state.status).toBe('fetched'));

    expect(drdDataStore.currentDecision).toEqual('invoiceClassification');
  });
});
