/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getUserDisplayName} from './getUserDisplayName';

describe('getUserDisplayName', () => {
  it('should get user display name', () => {
    expect(
      getUserDisplayName({
        firstname: 'firstname',
        lastname: 'lastname',
        username: 'username',
        __typename: 'User',
      }),
    ).toBe('firstname lastname');

    expect(
      getUserDisplayName({
        firstname: 'firstname',
        lastname: null,
        username: 'username',
        __typename: 'User',
      }),
    ).toBe('firstname');

    expect(
      getUserDisplayName({
        firstname: null,
        lastname: 'lastname',
        username: 'username',
        __typename: 'User',
      }),
    ).toBe('lastname');

    expect(
      getUserDisplayName({
        firstname: null,
        lastname: null,
        username: 'username',
        __typename: 'User',
      }),
    ).toBe('username');
  });
});
