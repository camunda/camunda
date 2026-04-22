/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockServer} from 'modules/mock-server/node';
import {type DefaultBodyType, delay, http, HttpResponse} from 'msw';
import type {MockedFunction} from 'vitest';

const checkPollingHeader = ({
  req,
  expectPolling,
}: {
  req: Request;
  expectPolling?: boolean;
}) => {
  if (req.headers.get('x-is-polling') !== null && expectPolling === false) {
    console.error(
      'Assertion error: expected x-is-polling header not to be set',
    );
    throw new Error();
  }
  if (req.headers.get('x-is-polling') !== 'true' && expectPolling === true) {
    console.error('Assertion error: expected x-is-polling header to be true');
    throw new Error();
  }
};

const mockPostRequest = function <Type extends DefaultBodyType>(url: string) {
  return {
    /**
     * @param options.expectPolling - expect that
     * - x-is-polling header is set if expectPolling is true
     * - x-is-polling header is not set if expectPolling is false
     *
     * Otherwise an error will be thrown
     */
    withSuccess: (
      responseData: Type,
      options?: {
        expectPolling?: boolean;
        mockResolverFn?: MockedFunction<() => void>;
        statusCode?: number;
      },
    ) => {
      mockServer.use(
        http.post(
          url,
          ({request}) => {
            options?.mockResolverFn?.();
            checkPollingHeader({
              req: request,
              expectPolling: options?.expectPolling,
            });
            return HttpResponse.json(
              responseData,
              options?.statusCode ? {status: options.statusCode} : {},
            );
          },
          {once: true},
        ),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.post(
          url,
          () =>
            HttpResponse.json(
              {error: 'an error occurred'},
              {status: statusCode},
            ),
          {once: true},
        ),
      );
    },
    withDelayedServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.post(
          url,
          async () => {
            await delay(100);
            return HttpResponse.json(
              {error: 'an error occurred'},
              {status: statusCode},
            );
          },
          {once: true},
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(http.post(url, () => HttpResponse.error()));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        http.post(
          url,
          async () => {
            await delay(100);
            return HttpResponse.json(responseData);
          },
          {once: true},
        ),
      );
    },
  };
};

const mockPutRequest = function <Type extends DefaultBodyType>(url: string) {
  return {
    withSuccess: (
      responseData: Type,
      options?: {
        mockResolverFn?: MockedFunction<() => void>;
      },
    ) => {
      mockServer.use(
        http.put(
          url,
          () => {
            options?.mockResolverFn?.();
            return HttpResponse.json(responseData);
          },
          {once: true},
        ),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.put(
          url,
          () =>
            HttpResponse.json(
              {error: 'an error occurred'},
              {status: statusCode},
            ),
          {once: true},
        ),
      );
    },
    withDelayedServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.put(
          url,
          async () => {
            await delay(100);
            return HttpResponse.json(
              {error: 'an error occurred'},
              {status: statusCode},
            );
          },
          {once: true},
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(http.put(url, () => HttpResponse.error()));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        http.put(
          url,
          async () => {
            await delay(100);
            return HttpResponse.json(responseData);
          },
          {once: true},
        ),
      );
    },
  };
};

const mockPatchRequest = function <Type extends DefaultBodyType>(url: string) {
  return {
    withSuccess: (
      responseData: Type,
      options?: {
        mockResolverFn?: MockedFunction<() => void>;
      },
    ) => {
      mockServer.use(
        http.patch(
          url,
          () => {
            options?.mockResolverFn?.();
            return HttpResponse.json(responseData);
          },
          {once: true},
        ),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.patch(
          url,
          () =>
            HttpResponse.json(
              {error: 'an error occurred'},
              {status: statusCode},
            ),
          {once: true},
        ),
      );
    },
    withDelayedServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.patch(
          url,
          async () => {
            await delay(100);
            return HttpResponse.json(
              {error: 'an error occurred'},
              {status: statusCode},
            );
          },
          {once: true},
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(http.patch(url, () => HttpResponse.error()));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        http.patch(
          url,
          async () => {
            await delay(100);
            return HttpResponse.json(responseData);
          },
          {once: true},
        ),
      );
    },
  };
};

const mockGetRequest = function <Type extends DefaultBodyType>(url: string) {
  return {
    /**
     * @param options.expectPolling - expect that
     * - x-is-polling header is set if expectPolling is true
     * - x-is-polling header is not set if expectPolling is false
     *
     * Otherwise an error will be thrown
     */
    withSuccess: (
      responseData: Type,
      options?: {
        expectPolling?: boolean;
        mockResolverFn?: MockedFunction<() => void>;
      },
    ) => {
      mockServer.use(
        http.get(
          url,
          ({request}) => {
            options?.mockResolverFn?.();
            checkPollingHeader({
              req: request,
              expectPolling: options?.expectPolling,
            });
            return HttpResponse.json(responseData);
          },
          {once: true},
        ),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.get(
          url,
          () =>
            HttpResponse.json(
              {error: 'an error occurred'},
              {status: statusCode},
            ),
          {once: true},
        ),
      );
    },
    withDelayedServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.get(
          url,
          async () => {
            await delay(100);
            return HttpResponse.json(
              {error: 'an error occurred'},
              {status: statusCode},
            );
          },
          {once: true},
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(http.get(url, () => HttpResponse.error()));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        http.get(
          url,
          async () => {
            await delay(100);
            return HttpResponse.json(responseData);
          },
          {once: true},
        ),
      );
    },
  };
};

const mockDeleteRequest = function <Type extends DefaultBodyType>(url: string) {
  return {
    withSuccess: (responseData: Type) => {
      mockServer.use(
        http.delete(url, () => HttpResponse.json(responseData), {once: true}),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.delete(
          url,
          () =>
            HttpResponse.json(
              {error: 'an error occurred'},
              {status: statusCode},
            ),
          {once: true},
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(http.delete(url, () => HttpResponse.error()));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        http.delete(
          url,
          async () => {
            await delay(100);
            return HttpResponse.json(responseData);
          },
          {once: true},
        ),
      );
    },
  };
};

const mockXmlGetRequest = (url: string) => {
  return {
    withSuccess: (initialValue: string) => {
      mockServer.use(
        http.get(url, () => HttpResponse.text(initialValue), {once: true}),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        http.get(
          url,
          () => HttpResponse.text('an error occurred', {status: statusCode}),
          {once: true},
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(http.get(url, () => HttpResponse.error()));
    },
    withDelay: (initialValue: string) => {
      mockServer.use(
        http.get(
          url,
          async () => {
            await delay(100);
            return HttpResponse.text(initialValue);
          },
          {once: true},
        ),
      );
    },
  };
};

export {
  mockGetRequest,
  mockPostRequest,
  mockXmlGetRequest,
  mockDeleteRequest,
  mockPutRequest,
  mockPatchRequest,
};
