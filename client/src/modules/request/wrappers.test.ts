/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {request} from './request';
import {get, post, put, del} from './wrappers';

jest.mock('./request');

describe('request wrappers', () => {
  beforeEach(() => {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'mockClear' does not exist on type '({ ur... Remove this comment to see the full error message
    request.mockClear();
  });

  const data = {url: 'some/url', x: 10, y: 'foo'};

  const dataWithQuery = {...data, query: 'some query'};

  const dataWithBody = {...data, body: 'some body'};

  describe('get', () => {
    it('should call request with the right parameters', () => {
      // given
      const options = {x: 10, y: 'foo'};
      const expectedCallArgs = {...dataWithQuery, method: 'GET'};

      // when
      get(expectedCallArgs.url, expectedCallArgs.query, options);

      // then
      expect(request).toBeCalledWith(expectedCallArgs);
    });
  });

  describe('post', () => {
    it('should call request with the right parameters', () => {
      // given
      const options = {x: 10, y: 'foo'};
      const expectedCallArgs = {...dataWithBody, method: 'POST'};

      // when
      post(expectedCallArgs.url, expectedCallArgs.body, options);

      // then
      expect(request).toBeCalledWith(expectedCallArgs);
    });
  });

  describe('put', () => {
    it('should call request with the right parameters', () => {
      // given
      const options = {x: 10, y: 'foo'};
      const expectedCallArgs = {...dataWithBody, method: 'PUT'};

      // when
      put(expectedCallArgs.url, expectedCallArgs.body, options);

      // then
      expect(request).toBeCalledWith(expectedCallArgs);
    });
  });

  describe('del', () => {
    it('should call request with the right parameters', () => {
      // given
      const options = {x: 10, y: 'foo'};
      const expectedCallArgs = {...dataWithQuery, method: 'DELETE'};

      // when
      del(expectedCallArgs.url, expectedCallArgs.query, options);

      // then
      expect(request).toBeCalledWith(expectedCallArgs);
    });
  });
});
