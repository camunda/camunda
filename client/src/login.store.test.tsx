/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {login} from './login.store';

const fetchMock = jest.spyOn(window, 'fetch');

describe('login store', () => {
  afterEach(() => {
    login.reset();
    fetchMock.mockClear();
  });

  afterAll(() => {
    fetchMock.mockRestore();
  });

  it('should login', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 204}));

    expect(login.isLoggedIn).toBe(false);
    expect(login.isCheckingExistingSession).toBe(false);

    await login.handleLogin('demo', 'demo');

    expect(login.isLoggedIn).toBe(true);
    expect(login.isCheckingExistingSession).toBe(false);
  });

  it('should throw an error on login failure', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 404}));

    await expect(login.handleLogin('demo', 'demo')).rejects.toThrow();
  });

  it('should logout', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(undefined, {status: 204}))
      .mockResolvedValueOnce(new Response(undefined, {status: 200}));

    await login.handleLogin('demo', 'demo');

    expect(login.isLoggedIn).toBe(true);
    expect(login.isCheckingExistingSession).toBe(false);

    await login.handleLogout();

    expect(login.isLoggedIn).toBe(false);
    expect(login.isCheckingExistingSession).toBe(false);
  });

  it('should throw an error on logout failure', async () => {
    fetchMock.mockResolvedValueOnce(new Response(undefined, {status: 404}));

    await expect(login.handleLogout()).rejects.toThrow();
  });
});
