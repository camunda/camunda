/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {authenticationStore} from 'modules/stores/authentication';
import {getStateLocally} from 'modules/utils/localStorage';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';
import {mockLogin} from 'modules/mocks/api/login';
import {mockLogout} from 'modules/mocks/api/logout';

const mockUserResponse = createUser();

describe('authentication store', () => {
  beforeEach(() => {
    authenticationStore.reset();
  });

  it('should assume that there is an existing session', () => {
    expect(authenticationStore.status).toBe('initial');
  });

  it('should login', async () => {
    mockLogin().withSuccess({});
    mockMe().withSuccess(mockUserResponse);

    authenticationStore.disableSession();

    expect(authenticationStore.status).toBe('session-invalid');

    await authenticationStore.handleLogin('demo', 'demo');

    expect(authenticationStore.status).toBe('logged-in');
  });

  it('should handle login failure', async () => {
    mockLogin().withServerError(401);

    const result = await authenticationStore.handleLogin('demo', 'demo');
    expect(result).toBeDefined();
    expect(result).toMatchObject({
      status: 401,
    });
    expect(authenticationStore.status).toBe('initial');
  });

  it('should logout', async () => {
    const mockReload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      reload: mockReload,
    });

    mockLogin().withSuccess({});
    mockMe().withSuccess(mockUserResponse);

    await authenticationStore.handleLogin('demo', 'demo');

    expect(authenticationStore.status).toBe('logged-in');

    mockLogout().withSuccess({});
    await authenticationStore.handleLogout();

    expect(authenticationStore.status).toBe('logged-out');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally()).toEqual({
      wasReloaded: false,
    });
  });

  it('should throw an error on logout failure', async () => {
    mockLogout().withServerError(500);

    expect(await authenticationStore.handleLogout()).not.toBeUndefined();
  });

  it('should disable session', async () => {
    const mockReload = vi.fn();
    vi.spyOn(window, 'location', 'get').mockReturnValue({
      ...window.location,
      reload: mockReload,
    });

    vi.stubGlobal('clientConfig', {
      canLogout: true,
      isLoginDelegated: false,
    });

    authenticationStore.activateSession();

    expect(authenticationStore.status).toBe('logged-in');

    authenticationStore.disableSession();

    expect(authenticationStore.status).toBe('session-expired');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally()).toEqual({
      wasReloaded: false,
    });

    authenticationStore.activateSession();

    expect(authenticationStore.status).toBe('logged-in');

    expect(mockReload).toHaveBeenCalledTimes(0);
    expect(getStateLocally()).toEqual({
      wasReloaded: false,
    });
  });

  it.each([
    {canLogout: false, isLoginDelegated: false},
    {canLogout: true, isLoginDelegated: true},
  ])(
    'should disable session when canLogout is $canLogout and isLoginDelegated is $isLoginDelegated',
    async ({canLogout, isLoginDelegated}) => {
      const mockReload = vi.fn();
      vi.spyOn(window, 'location', 'get').mockReturnValue({
        ...window.location,
        reload: mockReload,
      });

      vi.stubGlobal('clientConfig', {
        canLogout,
        isLoginDelegated,
      });

      authenticationStore.activateSession();

      expect(authenticationStore.status).toBe('logged-in');

      authenticationStore.disableSession();

      expect(authenticationStore.status).toBe('invalid-third-party-session');

      expect(mockReload).toHaveBeenCalledTimes(1);
      expect(getStateLocally()).toEqual({
        wasReloaded: true,
      });

      authenticationStore.activateSession();

      expect(authenticationStore.status).toBe('logged-in');

      expect(mockReload).toHaveBeenCalledTimes(1);
      expect(getStateLocally()).toEqual({
        wasReloaded: false,
      });
    },
  );

  it.each([
    {canLogout: false, isLoginDelegated: false},
    {canLogout: true, isLoginDelegated: true},
  ])(
    'should logout when canLogout is $canLogout and isLoginDelegated is $isLoginDelegated',
    async ({canLogout, isLoginDelegated}) => {
      const mockReload = vi.fn();
      vi.spyOn(window, 'location', 'get').mockReturnValue({
        ...window.location,
        reload: mockReload,
      });

      vi.stubGlobal('clientConfig', {
        canLogout,
        isLoginDelegated,
      });

      mockLogin().withSuccess({});
      mockMe().withSuccess(mockUserResponse);
      mockLogout().withSuccess({});
      mockLogin().withSuccess({});
      mockMe().withSuccess(mockUserResponse);

      await authenticationStore.handleLogin('demo', 'demo');

      expect(authenticationStore.status).toBe('logged-in');

      await authenticationStore.handleLogout();

      expect(authenticationStore.status).toBe('invalid-third-party-session');

      expect(mockReload).toHaveBeenCalledTimes(1);
      expect(getStateLocally()).toEqual({
        wasReloaded: true,
      });

      await authenticationStore.handleLogin('demo', 'demo');

      expect(authenticationStore.status).toBe('logged-in');

      expect(mockReload).toHaveBeenCalledTimes(1);
      expect(getStateLocally()).toEqual({
        wasReloaded: false,
      });
    },
  );
});
