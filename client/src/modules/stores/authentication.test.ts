/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {authenticationStore} from 'modules/stores/authentication';

describe('stores/authentication', () => {
  afterEach(() => {
    authenticationStore.reset();
  });

  it('should set user role', () => {
    expect(authenticationStore.state.permissions).toEqual(['read', 'write']);
    authenticationStore.enableUserSession({
      displayName: 'demo',
      permissions: undefined,
      canLogout: true,
    });
    expect(authenticationStore.state.permissions).toEqual(['read', 'write']);
    authenticationStore.enableUserSession({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
    });
    expect(authenticationStore.state.permissions).toEqual(['read']);
  });

  it('should see if user has permissions to specific scopes', () => {
    expect(authenticationStore.hasPermission(['write'])).toBe(true);
    expect(authenticationStore.hasPermission(['read'])).toBe(true);
    expect(authenticationStore.hasPermission(['write', 'read'])).toBe(true);
    authenticationStore.enableUserSession({
      displayName: 'demo',
      permissions: undefined,
      canLogout: true,
    });
    expect(authenticationStore.hasPermission(['write'])).toBe(true);
    expect(authenticationStore.hasPermission(['read'])).toBe(true);
    expect(authenticationStore.hasPermission(['write', 'read'])).toBe(true);
    authenticationStore.enableUserSession({
      displayName: 'demo',
      permissions: ['read'],
      canLogout: true,
    });
    expect(authenticationStore.hasPermission(['write'])).toBe(false);
    expect(authenticationStore.hasPermission(['read'])).toBe(true);
    expect(authenticationStore.hasPermission(['write', 'read'])).toBe(true);
  });
});
