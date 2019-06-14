/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {mockResolvedAsyncFn, mockRejectedAsyncFn} from 'modules/testUtils';

import {request, setResponseInterceptor} from './request';

import Csrf from 'modules/Csrf';

const successResponse = {
  status: 200,
  content: 'I have some content'
};

const failedResponse = {
  status: 401,
  content: 'FAILED'
};

const csrfResponse = {
  status: 200,
  headers: new Map([['X-CSRF-TOKEN', 'awesome-token']])
};

describe('request', () => {
  const url = '/some/url';

  beforeEach(() => {
    jest
      .spyOn(global, 'fetch')
      .mockImplementation(mockResolvedAsyncFn(successResponse));
  });

  afterEach(() => {
    global.fetch.mockClear();
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

  describe('CSRF', () => {
    afterEach(() => {
      Csrf.getInstance().setToken(null);
    });

    it('should not include CSRF token in request header when no token is present', () => {
      // when
      request({url});

      // then
      const headers = fetch.mock.calls[0][1].headers;
      expect(headers).not.toContain('X-CSRF-TOKEN');
    });

    it('should include CSRF token in request header when token is present', () => {
      // given
      const token = 'my-csrf-token';
      Csrf.getInstance().setToken(token);

      // when
      request({url});

      // then
      const headers = fetch.mock.calls[0][1].headers;
      expect(headers['X-CSRF-TOKEN']).toBe(token);
    });

    it('should not store CSRF token in app when response does not contain token', async () => {
      // when
      await request({url});

      // then
      expect(Csrf.getInstance().getToken()).toBeNull();
    });

    it('should store CSRF token in app when response contains token', async () => {
      // given
      jest
        .spyOn(global, 'fetch')
        .mockImplementation(mockResolvedAsyncFn(csrfResponse));

      // when
      await request({url});

      // then
      expect(Csrf.getInstance().getToken()).toBe('awesome-token');
    });

    it('should contain retreived CSRF token in next request', async () => {
      // given
      jest
        .spyOn(global, 'fetch')
        .mockImplementation(mockResolvedAsyncFn(csrfResponse));

      // when
      // first request gets response containing token
      await request({url});

      // then
      // second request should contain token in header
      await request({url});
      const headers = fetch.mock.calls[1][1].headers;
      expect(headers['X-CSRF-TOKEN']).toBe('awesome-token');
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
      jest
        .spyOn(global, 'fetch')
        .mockImplementation(mockRejectedAsyncFn(failedResponse));

      // then
      let error = null;
      try {
        await request({url});
      } catch (e) {
        error = e;
      }
      expect(error).toEqual(failedResponse);
    });
  });
});
