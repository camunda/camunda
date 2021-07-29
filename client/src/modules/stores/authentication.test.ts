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
    expect(authenticationStore.state.roles).toEqual(['view', 'edit']);
    authenticationStore.setRoles(undefined);
    expect(authenticationStore.state.roles).toEqual(['view', 'edit']);
    authenticationStore.setRoles(['view']);
    expect(authenticationStore.state.roles).toEqual(['view']);
  });

  it('should see if user has permissions to specific scopes', () => {
    expect(authenticationStore.hasPermission(['edit'])).toBe(true);
    expect(authenticationStore.hasPermission(['view'])).toBe(true);
    expect(authenticationStore.hasPermission(['edit', 'view'])).toBe(true);
    authenticationStore.setRoles(undefined);
    expect(authenticationStore.hasPermission(['edit'])).toBe(true);
    expect(authenticationStore.hasPermission(['view'])).toBe(true);
    expect(authenticationStore.hasPermission(['edit', 'view'])).toBe(true);
    authenticationStore.setRoles(['view']);
    expect(authenticationStore.hasPermission(['edit'])).toBe(false);
    expect(authenticationStore.hasPermission(['view'])).toBe(true);
    expect(authenticationStore.hasPermission(['edit', 'view'])).toBe(true);
  });
});
