/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {invoiceClassification} from 'modules/mocks/mockDecisionInstance';
import {decisionXmlStore} from './decisionXml';
import {decisionInstanceStore} from './decisionInstance';

describe('decisionXmlStore', () => {
  it('should initialize and reset ', async () => {
    mockServer.use(
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      ),
      rest.get('/api/decision-instances/:id', (_, res, ctx) =>
        res.once(ctx.json(invoiceClassification))
      )
    );

    expect(decisionXmlStore.state.status).toBe('initial');

    decisionXmlStore.init();
    decisionInstanceStore.fetchDecisionInstance('4423094875234230');

    await waitFor(() => expect(decisionXmlStore.state.status).toBe('fetched'));
    expect(decisionXmlStore.state.xml).toEqual(mockDmnXml);

    decisionXmlStore.reset();
    decisionInstanceStore.reset();

    expect(decisionXmlStore.state.status).toBe('initial');
    expect(decisionXmlStore.state.xml).toEqual(null);
  });
});
