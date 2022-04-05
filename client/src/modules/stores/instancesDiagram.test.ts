/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {instancesDiagramStore} from './instancesDiagram';
import {rest} from 'msw';
import {mockServer} from 'modules/mock-server/node';
import {waitFor} from '@testing-library/react';

jest.mock('modules/utils/bpmn');

describe('stores/instancesDiagram', () => {
  it('should retry fetch on network reconnection', async () => {
    const eventListeners: any = {};
    const originalEventListener = window.addEventListener;
    window.addEventListener = jest.fn((event: string, cb: any) => {
      eventListeners[event] = cb;
    });

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );

    instancesDiagramStore.fetchProcessXml('1');

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toEqual('fetched')
    );

    mockServer.use(
      rest.get('/api/processes/:processId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      )
    );

    eventListeners.online();

    expect(instancesDiagramStore.state.status).toEqual('fetching');

    await waitFor(() =>
      expect(instancesDiagramStore.state.status).toEqual('fetched')
    );

    window.addEventListener = originalEventListener;
  });
});
