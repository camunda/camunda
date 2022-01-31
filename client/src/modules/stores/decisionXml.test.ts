/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';
import {mockDmnXml} from 'modules/mocks/mockDmnXml';
import {decisionXmlStore} from './decisionXml';

describe('decisionXmlStore', () => {
  it('should initialize and reset ', async () => {
    mockServer.use(
      rest.get('/api/decisions/:decisionDefinitionId/xml', (_, res, ctx) =>
        res.once(ctx.text(mockDmnXml))
      )
    );

    expect(decisionXmlStore.state.status).toBe('initial');

    decisionXmlStore.init('4423094875234230');

    await waitFor(() => expect(decisionXmlStore.state.status).toBe('fetched'));
    expect(decisionXmlStore.state.xml).toEqual(mockDmnXml);

    decisionXmlStore.reset();

    expect(decisionXmlStore.state.status).toBe('initial');
    expect(decisionXmlStore.state.xml).toEqual(null);
  });
});
