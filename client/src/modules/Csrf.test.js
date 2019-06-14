/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import Csrf from './Csrf';

describe('Csrf', () => {
  it('should set and get token', () => {
    // given
    const token = 'my-token-123123123';

    // when
    Csrf.getInstance().setToken(token);

    //then
    expect(Csrf.getInstance().getToken()).toBe(token);
  });
});
