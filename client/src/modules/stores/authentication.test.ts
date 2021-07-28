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
});
