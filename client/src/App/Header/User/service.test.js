/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getUserName} from './service';

describe('getUserName', () => {
  it('should get user name', async () => {
    expect(
      getUserName({firstname: null, lastname: null, username: 'username'})
    ).toBe('username');
    expect(
      getUserName({firstname: 'first', lastname: null, username: 'username'})
    ).toBe('first');
    expect(
      getUserName({firstname: null, lastname: 'last', username: 'username'})
    ).toBe('last');
    expect(
      getUserName({firstname: 'first', lastname: 'last', username: null})
    ).toBe('first last');
    expect(
      getUserName({firstname: 'first', lastname: 'last', username: 'username'})
    ).toBe('first last');
    expect(getUserName({firstname: null, lastname: null, username: null})).toBe(
      ''
    );
  });
});
