/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, HttpResponse} from 'msw';
import {authenticationStore} from './authentication';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {getStateLocally} from 'modules/utils/localStorage';

describe('authentication store', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should assume that there is an existing session', () => {
    expect(authenticationStore.status).toBe('initial');
  });

  it('should login', async () => {
    nodeMockServer.use(
      http.post('/api/login', () => new HttpResponse(''), {once: true}),
    );

    authenticationStore.disableSession();

    expect(authenticationStore.status).toBe('session-invalid');

    await authenticationStore.handleLogin('demo', 'demo');

    expect(authenticationStore.status).toBe('logged-in');
  });

  it('should handle login failure', async () => {
    nodeMockServer.use(
      http.post('/api/login', () => new HttpResponse('', {status: 401}), {
        once: true,
      }),
    );

    expect(await authenticationStore.handleLogin('demo', 'demo')).toStrictEqual(
      {
        response: null,
        error: {
          variant: 'failed-response',
          response: expect.objectContaining({
            status: 401,
          }),
          networkError: null,
        },
      },
    );
    expect(authenticationStore.status).toBe('initial');
  });

  it('should logout', async () => {
    const mockReload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      reload: mockReload,
    });
    window.clientConfig = {
      canLogout: true,
      isLoginDelegated: false,
    };

    nodeMockServer.use(
      http.post('/api/login', () => new HttpResponse(''), {once: true}),
      http.post('/api/logout', () => new HttpResponse(''), {once: true}),
    );

    await authenticationStore.handleLogin('demo', 'demo');

    expect(authenticationStore.status).toBe('logged-in');

    await authenticationStore.handleLogout();

    expect(authenticationStore.status).toBe('logged-out');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally('wasReloaded')).toBe(false);
  });

  it('should throw an error on logout failure', async () => {
    nodeMockServer.use(
      http.post('/api/logout', () => new HttpResponse('', {status: 500}), {
        once: true,
      }),
    );

    expect(await authenticationStore.handleLogout()).not.toBeUndefined();
  });

  it('should disable session', async () => {
    const mockReload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      reload: mockReload,
    });
    window.clientConfig = {
      canLogout: true,
      isLoginDelegated: false,
    };

    authenticationStore.activateSession();

    expect(authenticationStore.status).toBe('logged-in');

    authenticationStore.disableSession();

    expect(authenticationStore.status).toBe('session-expired');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally('wasReloaded')).toBe(false);

    authenticationStore.activateSession();

    expect(authenticationStore.status).toBe('logged-in');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally('wasReloaded')).toBe(false);
  });

  [{canLogout: false}, {canLogout: true, isLoginDelegated: true}].forEach(
    (value) => {
      const {canLogout, isLoginDelegated} = value;

      describe(`when canLogout is ${canLogout} and isLoginDelegated is ${isLoginDelegated}`, () => {
        it('should disable session', async () => {
          const mockReload = vi.fn();
          vi.spyOn(window, 'location', 'get').mockReturnValue({
            ...window.location,
            reload: mockReload,
          });
          window.clientConfig = {
            canLogout,
            isLoginDelegated,
          };

          authenticationStore.activateSession();

          expect(authenticationStore.status).toBe('logged-in');

          authenticationStore.disableSession();

          expect(authenticationStore.status).toBe(
            'invalid-third-party-session',
          );

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(true);

          authenticationStore.activateSession();

          expect(authenticationStore.status).toBe('logged-in');

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(false);
        });
      });
    },
  );

  [{canLogout: false}, {canLogout: true, isLoginDelegated: true}].forEach(
    (value) => {
      const {canLogout, isLoginDelegated} = value;

      describe(`when canLogout is ${canLogout} and isLoginDelegated is ${isLoginDelegated}`, () => {
        it('should logout', async () => {
          const mockReload = vi.fn();
          vi.spyOn(window, 'location', 'get').mockReturnValue({
            ...window.location,
            reload: mockReload,
          });
          window.clientConfig = {
            canLogout,
            isLoginDelegated,
          };

          nodeMockServer.use(
            http.post('/api/login', () => new HttpResponse(''), {once: true}),
            http.post('/api/logout', () => new HttpResponse(''), {once: true}),
            http.post('/api/login', () => new HttpResponse(''), {once: true}),
          );

          await authenticationStore.handleLogin('demo', 'demo');

          expect(authenticationStore.status).toBe('logged-in');

          await authenticationStore.handleLogout();

          expect(authenticationStore.status).toBe(
            'invalid-third-party-session',
          );

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(true);

          await authenticationStore.handleLogin('demo', 'demo');

          expect(authenticationStore.status).toBe('logged-in');

          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally('wasReloaded')).toBe(false);
        });
      });
    },
  );
});
