/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'modules/request';

import {login} from './index';

jest.mock('modules/request', () => ({
  post: jest.fn().mockImplementation(() => ({
    status: 200,
  })),
}));

describe('login api', () => {
  describe('login', () => {
    it('should call post with the right parameters', () => {
      const username = 'foo';
      const password = 'bar';

      login({username, password});

      expect(post).toHaveBeenCalledWith(
        '/api/login',
        'username=foo&password=bar',
        {headers: {'Content-Type': 'application/x-www-form-urlencoded'}}
      );
    });
  });
});
