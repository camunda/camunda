/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionInstanceDetailsStore} from './decisionInstanceDetails';

describe('decisionInstanceDetailsStore', () => {
  it('should initialize and reset ', async () => {
    mockServer.use(
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      ),
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      )
    );

    expect(decisionInstanceDetailsStore.state.status).toBe('initial');

    decisionInstanceDetailsStore.fetchDecisionInstance('22517947328274621');

    await waitFor(() =>
      expect(decisionInstanceDetailsStore.state.status).toBe('fetched')
    );
    expect(decisionInstanceDetailsStore.state.decisionInstance).toEqual(
      invoiceClassification
    );

    decisionInstanceDetailsStore.reset();

    expect(decisionInstanceDetailsStore.state.status).toBe('initial');
    expect(decisionInstanceDetailsStore.state.decisionInstance).toEqual(null);
  });
});
