/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, HttpResponse} from 'msw';
import {nodeMockServer} from './mockServer/nodeMockServer';
import {request} from './request';

const MOCK_URL = '/api/login';

describe('request', () => {
  it('should handle a successful request', async () => {
    nodeMockServer.use(
      http.post(
        MOCK_URL,
        () => {
          return HttpResponse.json();
        },
        {
          once: true,
        },
      ),
    );

    const response = await request(
      new Request(new URL(MOCK_URL, window.location.origin), {
        method: 'POST',
        body: JSON.stringify({username: 'demo', password: 'demo'}),
      }),
    );

    expect(response).toStrictEqual({
      response: expect.objectContaining({
        status: 200,
      }),
      error: null,
    });
  });

  it('should handle a failed request', async () => {
    nodeMockServer.use(
      http.post(MOCK_URL, () => new HttpResponse('', {status: 401}), {
        once: true,
      }),
    );

    const response = await request(
      new Request(new URL(MOCK_URL, window.location.origin), {
        method: 'POST',
        body: JSON.stringify({username: 'demo', password: 'demo'}),
      }),
    );

    expect(response).toStrictEqual({
      response: null,
      error: expect.objectContaining({
        variant: 'failed-response',
        response: expect.objectContaining({
          status: 401,
        }),
        networkError: null,
      }),
    });
  });

  it('should handle a network errors', async () => {
    nodeMockServer.use(
      http.post(MOCK_URL, () => HttpResponse.error(), {
        once: true,
      }),
    );

    const response = await request(
      new Request(new URL(MOCK_URL, window.location.origin), {
        method: 'POST',
        body: JSON.stringify({username: 'demo', password: 'demo'}),
      }),
    );

    expect(response).toStrictEqual({
      response: null,
      error: expect.objectContaining({
        variant: 'network-error',
        response: null,
        networkError: expect.objectContaining({
          message: 'Failed to fetch',
        }),
      }),
    });
  });
});
