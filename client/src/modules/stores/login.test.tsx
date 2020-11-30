/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {rest} from 'msw';
import {login} from './login';
import {mockServer} from 'modules/mockServer';

describe('login store', () => {
  afterEach(() => {
    login.reset();
  });

  it('should assume that there is an existing session', () => {
    expect(login.status).toBe('initial');
  });

  it('should login', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );

    login.disableSession();

    expect(login.status).toBe('session-invalid');

    await login.handleLogin('demo', 'demo');

    expect(login.status).toBe('logged-in');
  });

  it('should handle login failure', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) =>
        res.once(ctx.status(401), ctx.text('')),
      ),
    );

    expect(await login.handleLogin('demo', 'demo')).toStrictEqual(
      expect.objectContaining({status: 401}),
    );
    expect(login.status).toBe('initial');
  });

  it('should logout', async () => {
    mockServer.use(
      rest.post('/api/login', (_, res, ctx) => res.once(ctx.text(''))),
    );
    mockServer.use(
      rest.post('/api/logout', (_, res, ctx) => res.once(ctx.text(''))),
    );

    await login.handleLogin('demo', 'demo');

    expect(login.status).toBe('logged-in');

    await login.handleLogout();

    expect(login.status).toBe('logged-out');
  });

  it('should throw an error on logout failure', async () => {
    mockServer.use(
      rest.post('/api/logout', (_, res, ctx) =>
        res.once(ctx.status(500), ctx.text('')),
      ),
    );

    await expect(login.handleLogout()).rejects.toThrow();
  });
});
