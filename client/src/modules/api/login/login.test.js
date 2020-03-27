/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {post} from 'modules/request';

import {login} from './login';

jest.mock('modules/request');

describe('login api', () => {
  beforeEach(() => {
    post.mockClear();
  });

  describe('login', () => {
    it('should call post with the right parameters', () => {
      // given
      const username = 'foo';
      const password = 'bar';

      // when
      login({username, password});

      // then
      expect(post.mock.calls[0][0]).toBe('/api/login');
      expect(post.mock.calls[0][1]).toBe('username=foo&password=bar');
      expect(post.mock.calls[0][2]).toEqual({
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      });
    });
  });
});
