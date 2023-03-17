/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {getStateLocally} from 'modules/utils/localStorage';
import {mockGetUser} from 'modules/mocks/api/getUser';
import {createUser} from 'modules/testUtils';

const mockUserResponse = createUser();

describe('stores/authentication', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should set user role', () => {
    expect(authenticationStore.state.permissions).toEqual(['read', 'write']);

    authenticationStore.setUser(createUser({permissions: undefined}));
    expect(authenticationStore.state.permissions).toEqual(['read', 'write']);

    authenticationStore.setUser(createUser({permissions: ['read']}));
    expect(authenticationStore.state.permissions).toEqual(['read']);
  });

  it('should see if user has permissions to specific scopes', () => {
    expect(authenticationStore.hasPermission(['write'])).toBe(true);
    expect(authenticationStore.hasPermission(['read'])).toBe(true);
    expect(authenticationStore.hasPermission(['write', 'read'])).toBe(true);

    authenticationStore.setUser(createUser({permissions: undefined}));
    expect(authenticationStore.hasPermission(['write'])).toBe(true);
    expect(authenticationStore.hasPermission(['read'])).toBe(true);
    expect(authenticationStore.hasPermission(['write', 'read'])).toBe(true);

    authenticationStore.setUser(createUser({permissions: ['read']}));
    expect(authenticationStore.hasPermission(['write'])).toBe(false);
    expect(authenticationStore.hasPermission(['read'])).toBe(true);
    expect(authenticationStore.hasPermission(['write', 'read'])).toBe(true);
  });

  it.each([{canLogout: false}, {canLogout: true, isLoginDelegated: true}])(
    'should handle third party authentication when client config is %s',
    async (config) => {
      Object.defineProperty(window, 'clientConfig', {
        ...config,
        writable: true,
      });
      const originalWindow = {...window};
      const windowSpy = jest.spyOn(global, 'window', 'get');
      const mockReload = jest.fn();
      // @ts-expect-error
      windowSpy.mockImplementation(() => ({
        ...originalWindow,
        location: {
          ...originalWindow.location,
          reload: mockReload,
        },
      }));

      mockGetUser().withSuccess(mockUserResponse);

      authenticationStore.authenticate();

      await waitFor(() =>
        expect(authenticationStore.state.status).toBe(
          'user-information-fetched'
        )
      );

      mockGetUser().withServerError(401);

      authenticationStore.authenticate();

      await waitFor(() =>
        expect(authenticationStore.state.status).toBe(
          'invalid-third-party-session'
        )
      );
      expect(mockReload).toHaveBeenCalledTimes(1);
      expect(getStateLocally()?.wasReloaded).toBe(true);

      mockGetUser().withSuccess(mockUserResponse);

      authenticationStore.authenticate();

      await waitFor(() =>
        expect(authenticationStore.state.status).toBe(
          'user-information-fetched'
        )
      );
      expect(mockReload).toHaveBeenCalledTimes(1);
      expect(getStateLocally()?.wasReloaded).toBe(false);

      windowSpy.mockRestore();
    }
  );
});
