/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mockResolvedAsyncFn, mockRejectedAsyncFn} from 'modules/testUtils';

import {request, setResponseInterceptor} from './request';

const successResponse = {
  status: 200,
  content: 'I have some content'
};

const failedResponse = {
  status: 401,
  content: 'FAILED'
};

global.fetch = mockResolvedAsyncFn(successResponse);

describe('request', () => {
  const url = '/some/url';

  beforeEach(() => {
    fetch.mockClear();
  });

  describe('url', () => {
    it('should call fetch with url', () => {
      // when (1)
      request({url});

      // then
      expect(fetch).toBeCalled();
      expect(fetch.mock.calls[0][0]).toEqual('/some/url');
    });

    it('should call fetch with url and query', () => {
      // given
      const query = {x: 10, y: 'foo'};

      // when
      request({url, query});

      // then
      expect(fetch).toBeCalled();
      expect(fetch.mock.calls[0][0]).toEqual('/some/url?x=10&y=foo');
    });
  });

  describe('options', () => {
    it('should call fetch with the right options', () => {
      // given
      const expectedOptions = {
        method: 'GET',
        credentials: 'include',
        body: 'body',
        headers: {'Content-Type': 'application/json'},
        mode: 'cors'
      };

      // when
      request({url, method: 'GET', body: 'body'});

      // then
      expect(fetch).toBeCalled();
      expect(fetch.mock.calls[0][1]).toEqual(expectedOptions);
    });

    it("should stringify body if it's not a string", () => {
      // given
      const body = {x: 20, y: 'foo'};

      // when
      request({url, method: 'GET', body});

      // then
      expect(fetch).toBeCalled();
      const fetchOptions = fetch.mock.calls[0][1];
      expect(fetchOptions.body).toBe('{"x":20,"y":"foo"}');
    });

    it('should be able to override headers', () => {
      // given
      const headers = {foo: 'bar', 'Content-Type': 'text/plain'};

      // when
      request({url, method: 'GET', body: 'body', headers});

      // then
      expect(fetch).toBeCalled();
      const fetchOptions = fetch.mock.calls[0][1];
      expect(fetchOptions.headers).toEqual(headers);
    });
  });

  describe('response', () => {
    it("should call responseInterceptor when it's provided", async () => {
      // given
      const responseInterceptor = mockResolvedAsyncFn();
      setResponseInterceptor(responseInterceptor);

      // when
      const response = await request({url});

      // then
      expect(responseInterceptor).toHaveBeenCalledWith(response);
    });

    it("should throw response if it's unsuccessful", async () => {
      // mock global.fetch
      const originalFetch = global.fetch;
      global.fetch = mockRejectedAsyncFn(failedResponse);

      // then
      let error = null;
      try {
        await request({url});
      } catch (e) {
        error = e;
      }
      expect(error).toEqual(failedResponse);

      // reset global.fetch
      global.fetch = originalFetch.bind(global);
    });
  });
});
