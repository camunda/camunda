/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {waitFor} from '@testing-library/react';
import {mockServer} from 'modules/mock-server/node';
import {authenticationStore} from 'modules/stores/authentication';
import {getStateLocally} from 'modules/utils/localStorage';
import {rest} from 'msw';

const mockUserResponse = {
  userId: 'demo',
  displayName: 'demo',
  canLogout: true,
  permissions: ['read', 'write'],
  username: 'demo',
} as const;

describe('stores/authentication', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should set user role', () => {
    expect(authenticationStore.state.permissions).toEqual(['read', 'write']);
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: undefined,
      canLogout: true,
      userId: 'demo',
    });
    expect(authenticationStore.state.permissions).toEqual(['read', 'write']);
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
    });
    expect(authenticationStore.state.permissions).toEqual(['read']);
  });

  it('should see if user has permissions to specific scopes', () => {
    expect(authenticationStore.hasPermission(['write'])).toBe(true);
    expect(authenticationStore.hasPermission(['read'])).toBe(true);
    expect(authenticationStore.hasPermission(['write', 'read'])).toBe(true);
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: undefined,
      canLogout: true,
      userId: 'demo',
    });
    expect(authenticationStore.hasPermission(['write'])).toBe(true);
    expect(authenticationStore.hasPermission(['read'])).toBe(true);
    expect(authenticationStore.hasPermission(['write', 'read'])).toBe(true);
    authenticationStore.setUser({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
      userId: 'demo',
    });
    expect(authenticationStore.hasPermission(['write'])).toBe(false);
    expect(authenticationStore.hasPermission(['read'])).toBe(true);
    expect(authenticationStore.hasPermission(['write', 'read'])).toBe(true);
  });

  [{canLogout: false}, {canLogout: true, isLoginDelegated: true}].forEach(
    (value) => {
      const {canLogout, isLoginDelegated} = value;

      describe(`when canLogout is ${canLogout} and isLoginDelegated is ${isLoginDelegated}`, () => {
        it('should handle third-party authentication', async () => {
          Object.defineProperty(window, 'clientConfig', {
            value,
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

          mockServer.use(
            rest.get('/api/authentications/user', (_, res, ctx) =>
              res.once(ctx.json(mockUserResponse))
            ),
            rest.get('/api/authentications/user', (_, res, ctx) =>
              res.once(ctx.status(401))
            ),
            rest.get('/api/authentications/user', (_, res, ctx) =>
              res.once(ctx.json(mockUserResponse))
            )
          );

          authenticationStore.authenticate();

          await waitFor(() =>
            expect(authenticationStore.state.status).toBe(
              'user-information-fetched'
            )
          );

          authenticationStore.authenticate();

          await waitFor(() =>
            expect(authenticationStore.state.status).toBe(
              'invalid-third-party-session'
            )
          );
          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally()?.wasReloaded).toBe(true);

          authenticationStore.authenticate();

          await waitFor(() =>
            expect(authenticationStore.state.status).toBe(
              'user-information-fetched'
            )
          );
          expect(mockReload).toHaveBeenCalledTimes(1);
          expect(getStateLocally()?.wasReloaded).toBe(false);

          windowSpy.mockRestore();
        });
      });
    }
  );
});
