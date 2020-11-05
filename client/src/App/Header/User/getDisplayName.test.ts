/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getDisplayName} from './getDisplayName';

describe('getDisplayName', () => {
  it('should get user name', async () => {
    expect(
      getDisplayName({firstname: null, lastname: null, username: 'username'})
    ).toBe('username');
    expect(
      getDisplayName({firstname: 'first', lastname: null, username: 'username'})
    ).toBe('first');
    expect(
      getDisplayName({firstname: null, lastname: 'last', username: 'username'})
    ).toBe('last');
    expect(
      getDisplayName({firstname: 'first', lastname: 'last', username: null})
    ).toBe('first last');
    expect(
      getDisplayName({
        firstname: 'first',
        lastname: 'last',
        username: 'username',
      })
    ).toBe('first last');
    expect(
      getDisplayName({firstname: null, lastname: null, username: null})
    ).toBe('');
  });
});
