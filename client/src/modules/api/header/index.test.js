/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {logout, fetchUser} from './index';

import {mockResolvedAsyncFn} from 'modules/testUtils';

import * as wrappers from 'modules/request/wrappers';

wrappers.get = mockResolvedAsyncFn();

describe('header api', () => {
  describe('logout', () => {
    it('should call post with the right url', () => {
      // given
      wrappers.post = mockResolvedAsyncFn();

      // when
      logout();

      // then
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
      wrappers.get = mockResolvedAsyncFn(successResponse);

      // when
      const response = await fetchUser();

      // then
      expect(wrappers.get.mock.calls[0][0]).toBe('/api/authentications/user');
      expect(successResponse.json).toBeCalled();
      expect(response).toEqual(successMessage);
    });
  });
});
