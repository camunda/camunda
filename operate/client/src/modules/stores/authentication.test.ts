/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {waitFor} from 'modules/testing-library';
import {authenticationStore} from 'modules/stores/authentication';
import {getStateLocally} from 'modules/utils/localStorage';
import {mockMe} from 'modules/mocks/api/v2/me';
import {createUser} from 'modules/testUtils';

const mockUserResponse = createUser();

describe('stores/authentication', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it.each([{canLogout: false}, {canLogout: true, isLoginDelegated: true}])(
    'should handle third-party authentication when client config is %s',
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

      mockMe().withSuccess(mockUserResponse);
      authenticationStore.authenticate();

      await waitFor(() =>
        expect(authenticationStore.state.status).toBe(
          'user-information-fetched',
        ),
      );

      mockMe().withServerError(401);
      authenticationStore.authenticate();

      await waitFor(() =>
        expect(authenticationStore.state.status).toBe(
          'invalid-third-party-session',
        ),
      );

      expect(mockReload).toHaveBeenCalledTimes(1);
      expect(getStateLocally()?.wasReloaded).toBe(true);

      mockMe().withSuccess(mockUserResponse);
      authenticationStore.authenticate();

      await waitFor(() =>
        expect(authenticationStore.state.status).toBe(
          'user-information-fetched',
        ),
      );

      expect(mockReload).toHaveBeenCalledTimes(1);
      expect(getStateLocally()?.wasReloaded).toBe(false);

      windowSpy.mockRestore();
    },
  );

  describe('isForbidden', () => {
    it('should return true when authorizedApplications does not contain "operate" or "*"', () => {
      authenticationStore.state.authorizedApplications = ['tasklist'];
      expect(authenticationStore.isForbidden()).toBe(true);
    });

    it('should return false when authorizedApplications contains "operate"', () => {
      authenticationStore.state.authorizedApplications = [
        'tasklist',
        'operate',
      ];
      expect(authenticationStore.isForbidden()).toBe(false);
    });

    it('should return false when authorizedApplications contains "*"', () => {
      authenticationStore.state.authorizedApplications = ['*'];
      expect(authenticationStore.isForbidden()).toBe(false);
    });
  });
});
