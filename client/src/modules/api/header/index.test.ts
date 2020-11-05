/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {logout, fetchUser} from './index';

import {mockResolvedAsyncFn} from 'modules/testUtils';

import * as wrappers from 'modules/request/wrappers';

// @ts-expect-error ts-migrate(2540) FIXME: Cannot assign to 'get' because it is a read-only p... Remove this comment to see the full error message
wrappers.get = mockResolvedAsyncFn();

describe('header api', () => {
  describe('logout', () => {
    it('should call post with the right url', () => {
      // given
      // @ts-expect-error ts-migrate(2540) FIXME: Cannot assign to 'post' because it is a read-only ... Remove this comment to see the full error message
      wrappers.post = mockResolvedAsyncFn();

      // when
      logout();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.post.mock.calls[0][0]).toBe('/api/logout');
    });
  });

  describe('fetchUser', () => {
    it('should call get with the right url', async () => {
      // given
      const successMessage = 'success';
      const successResponse = {
        json: mockResolvedAsyncFn(successMessage),
      };
      // @ts-expect-error ts-migrate(2540) FIXME: Cannot assign to 'get' because it is a read-only p... Remove this comment to see the full error message
      wrappers.get = mockResolvedAsyncFn(successResponse);

      // when
      const response = await fetchUser();

      // then
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'mock' does not exist on type '(url: any,... Remove this comment to see the full error message
      expect(wrappers.get.mock.calls[0][0]).toBe('/api/authentications/user');
      expect(successResponse.json).toBeCalled();
      expect(response).toEqual(successMessage);
    });
  });
});
