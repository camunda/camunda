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
  beforeEach(() => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockClear' does not exist on type '(url:... Remove this comment to see the full error message
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
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(post.mock.calls[0][0]).toBe('/api/login');
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(post.mock.calls[0][1]).toBe('username=foo&password=bar');
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(post.mock.calls[0][2]).toEqual({
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      });
    });
  });
});
