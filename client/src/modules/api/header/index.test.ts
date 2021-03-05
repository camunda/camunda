/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {logout, fetchUser} from './index';

import {mockResolvedAsyncFn} from 'modules/testUtils';

import * as wrappers from 'modules/request/wrappers';

describe('header api', () => {
  describe('logout', () => {
    it('should call post with the right url', () => {
      Object.defineProperty(wrappers, 'post', {value: mockResolvedAsyncFn('')});

      logout();

      expect(wrappers.post).toHaveBeenCalledWith('/api/logout');
    });
  });

  describe('fetchUser', () => {
    it('should call get with the right url', async () => {
      const successMessage = 'success';
      const successResponse = {
        json: mockResolvedAsyncFn(successMessage),
      };

      Object.defineProperty(wrappers, 'get', {
        value: mockResolvedAsyncFn(successResponse),
      });

      const response = await fetchUser();

      expect(wrappers.get).toHaveBeenCalledWith('/api/authentications/user');
      expect(successResponse.json).toBeCalled();
      expect(response).toEqual(successMessage);
    });
  });
});
