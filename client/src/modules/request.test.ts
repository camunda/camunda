/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  RequestPayload,
  request,
  formatQuery,
  put,
  post,
  get,
  addHandler,
  removeHandler,
} from './request';

const successResponse = {
  status: 200,
  content: 'I have so much content',
};
const failedResponse = {
  status: 401,
  message: 'FAILED',
};
global.fetch = jest.fn();
const fetch = global.fetch as any;
fetch.mockReturnValue(Promise.resolve(successResponse));

const url = 'https://example.com';

describe('request', () => {
  const method = 'GET';

  beforeEach(() => {
    fetch.mockClear();
  });

  it('should open http request with given method and url', async () => {
    await request({
      url,
      method,
    });

    const {method: actualMethod} = fetch.mock.calls[0][1];

    expect(fetch.mock.calls[0][0]).toBe(url);
    expect(actualMethod).toBe(method);
  });

  it('should set headers', async () => {
    const headers = {
      g: 1,
    };

    await request({
      url,
      method,
      headers,
    } as RequestPayload);

    const {
      headers: {g},
    } = fetch.mock.calls[0][1];

    expect(g).toBe(headers.g);
  });

  it('should set default Content-Type to application/json', async () => {
    await request({
      url,
      method,
    });

    const {
      headers: {'Content-Type': contentType},
    } = fetch.mock.calls[0][1];

    expect(contentType).toBe('application/json');
  });

  it('should provide option to override Content-Type header', async () => {
    const contentType = 'text';

    await request({
      url,
      method,
      headers: {
        'Content-Type': contentType,
      },
    });

    const {
      headers: {'Content-Type': actualContentType},
    } = fetch.mock.calls[0][1];

    expect(actualContentType).toBe(contentType);
  });

  it('should stringify json body objects', async () => {
    const body = {
      d: 1,
    };

    await request({
      url,
      method,
      body,
    });

    const {body: actualBody} = fetch.mock.calls[0][1];

    expect(actualBody).toBe(JSON.stringify(body));
  });

  it('should return successful response when status is 200', async () => {
    const response = await request({
      url,
      method,
    });

    expect(response).toBe(successResponse);
  });

  it('should return rejected response when status is 401', async () => {
    fetch.mockReturnValueOnce(Promise.resolve(failedResponse));

    try {
      await request({
        url,
        method,
      });
    } catch (e) {
      expect(e).toEqual(failedResponse);
    }
  });
});

describe('handlers', () => {
  it('should call a registered handler with the response', async () => {
    const spy = jest.fn().mockReturnValue(successResponse);

    addHandler(spy);

    await request({url} as RequestPayload);

    expect(spy).toHaveBeenCalledWith(successResponse, {url});
  });

  it('should not call a handler after it has been unregistered', async () => {
    const spy = jest.fn().mockReturnValue(successResponse);

    addHandler(spy);
    removeHandler(spy);

    await request({url} as RequestPayload);

    expect(spy).not.toHaveBeenCalled();
  });

  it('should call handlers in order of their priority', async () => {
    const out: any = [];
    const handler = (i: any) => (r: any) => out.push(i) && r;

    addHandler(handler(0), 0);
    addHandler(handler(-4), -4);
    addHandler(handler(0));
    addHandler(handler(1), 1);
    addHandler(handler(3), 3);

    await request({url} as RequestPayload);

    expect(out).toEqual([3, 1, 0, 0, -4]);
  });
});

describe('formatQuery', () => {
  it('should format query object into proper query string', () => {
    const query = {
      a: 1,
      b: '5=5',
    };

    expect(formatQuery(query)).toBe('a=1&b=5%3D5');
  });
});

describe('methods shortcuts functions', () => {
  const url = 'http://example.com';
  const body = 'BODY';

  beforeEach(() => {
    fetch.mockClear();
  });

  describe('put', () => {
    it('should call request with correct options', async () => {
      await put(url, body);

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetch.mock.calls[0][0]).toBe(url);
      expect(fetchPayload.body).toBe(body);
      expect(fetchPayload.method).toBe('PUT');
    });

    it('should use custom options', async () => {
      await put(url, body, {
        headers: {d: 12},
      });

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetchPayload.headers.d).toBe(12);
    });

    it('should return request response', async () => {
      expect(await put(url, body)).toBe(successResponse);
    });
  });

  describe('post', () => {
    it('should call request with correct options', async () => {
      await post(url, body);

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetch.mock.calls[0][0]).toBe(url);
      expect(fetchPayload.body).toBe(body);
      expect(fetchPayload.method).toBe('POST');
    });

    it('should use custom options', async () => {
      await post(url, body, {
        headers: {d: 12},
      });

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetchPayload.headers.d).toBe(12);
    });

    it('should return request response', async () => {
      expect(await post(url, body)).toBe(successResponse);
    });
  });

  describe('get', () => {
    const query = {param: 'q'};

    it('should call request with correct options', async () => {
      await get(url, query);

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetch.mock.calls[0][0]).toBe(url + '?param=q');
      expect(fetchPayload.method).toBe('GET');
    });

    it('should call request with correct options for array value', async () => {
      await get(url, {param: ['a', 'b', 'c']});

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetch.mock.calls[0][0]).toBe(url + '?param=a&param=b&param=c');
      expect(fetchPayload.method).toBe('GET');
    });

    it('should use custom options', async () => {
      await get(
        url,
        {},
        {
          headers: {d: 12},
        }
      );

      const fetchPayload = fetch.mock.calls[0][1];

      expect(fetchPayload.headers.d).toBe(12);
    });

    it('should return request response', async () => {
      expect(await get(url, {})).toBe(successResponse);
    });
  });
});
