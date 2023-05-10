/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {rest} from 'msw';
import {nodeMockServer} from './mockServer/nodeMockServer';
import {request} from './request';

const MOCK_URL = '/api/login';

describe('request', () => {
  it('should handle a successful request', async () => {
    nodeMockServer.use(
      rest.post(MOCK_URL, (_, res, ctx) =>
        res.once(ctx.status(200), ctx.json({})),
      ),
    );

    const response = await request(MOCK_URL, {
      method: 'POST',
      body: JSON.stringify({username: 'demo', password: 'demo'}),
    });

    expect(response).toStrictEqual({
      response: expect.objectContaining({
        status: 200,
      }),
      error: null,
    });
  });

  it('should handle a failed request', async () => {
    nodeMockServer.use(
      rest.post(MOCK_URL, (_, res, ctx) =>
        res.once(ctx.status(401), ctx.text('')),
      ),
    );

    const response = await request(MOCK_URL, {
      method: 'POST',
      body: JSON.stringify({username: 'demo', password: 'demo'}),
    });

    expect(response).toStrictEqual({
      response: null,
      error: expect.objectContaining({
        variant: 'failed-response',
        response: expect.objectContaining({
          status: 401,
        }),
        error: null,
      }),
    });
  });

  it('should handle a network errors', async () => {
    nodeMockServer.use(
      rest.post(MOCK_URL, (_, res) => res.networkError('Failed to connect')),
    );

    const response = await request(MOCK_URL, {
      method: 'POST',
      body: JSON.stringify({username: 'demo', password: 'demo'}),
    });

    expect(response).toStrictEqual({
      response: null,
      error: expect.objectContaining({
        variant: 'network-error',
        response: null,
        error: expect.objectContaining({
          message: 'Failed to connect',
        }),
      }),
    });
  });
});
