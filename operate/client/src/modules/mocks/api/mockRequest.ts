/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockServer} from 'modules/mock-server/node';
import {DefaultBodyType, RestRequest, rest} from 'msw';

const checkPollingHeader = ({
  req,
  expectPolling,
}: {
  req: RestRequest;
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
      options?: {expectPolling?: boolean; mockResolverFn?: jest.Mock},
    ) => {
      mockServer.use(
        rest.post(url, (req, res, ctx) => {
          options?.mockResolverFn?.();
          checkPollingHeader({req, expectPolling: options?.expectPolling});
          return res.once(ctx.json(responseData));
        }),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.post(url, (_, res, ctx) =>
          res.once(
            ctx.status(statusCode),
            ctx.json({error: 'an error occurred'}),
          ),
        ),
      );
    },
    withDelayedServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.post(url, (_, res, ctx) =>
          res.once(
            ctx.delay(100),
            ctx.status(statusCode),
            ctx.json({error: 'an error occurred'}),
          ),
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(rest.post(url, (_, res) => res.networkError('')));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        rest.post(url, (_, res, ctx) =>
          res.once(ctx.delay(100), ctx.json(responseData)),
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
    withSuccess: (responseData: Type, options?: {expectPolling?: boolean}) => {
      mockServer.use(
        rest.get(url, (req, res, ctx) => {
          checkPollingHeader({req, expectPolling: options?.expectPolling});
          return res.once(ctx.json(responseData));
        }),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) =>
          res.once(
            ctx.status(statusCode),
            ctx.json({error: 'an error occurred'}),
          ),
        ),
      );
    },
    withDelayedServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) =>
          res.once(
            ctx.delay(100),
            ctx.status(statusCode),
            ctx.json({error: 'an error occurred'}),
          ),
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(rest.get(url, (_, res) => res.networkError('')));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) =>
          res.once(ctx.delay(100), ctx.json(responseData)),
        ),
      );
    },
  };
};

const mockDeleteRequest = function <Type extends DefaultBodyType>(url: string) {
  return {
    withSuccess: (responseData: Type) => {
      mockServer.use(
        rest.delete(url, (_, res, ctx) => res.once(ctx.json(responseData))),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.delete(url, (_, res, ctx) =>
          res.once(
            ctx.status(statusCode),
            ctx.json({error: 'an error occurred'}),
          ),
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(rest.delete(url, (_, res) => res.networkError('')));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        rest.delete(url, (_, res, ctx) =>
          res.once(ctx.delay(100), ctx.json(responseData)),
        ),
      );
    },
  };
};

const mockXmlGetRequest = (url: string) => {
  return {
    withSuccess: (initialValue: string) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) => res.once(ctx.text(initialValue))),
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) =>
          res.once(ctx.status(statusCode), ctx.text('an error occurred')),
        ),
      );
    },
    withNetworkError: () => {
      mockServer.use(rest.get(url, (_, res) => res.networkError('')));
    },
    withDelay: (initialValue: string) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) =>
          res.once(ctx.delay(100), ctx.text(initialValue)),
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
  checkPollingHeader,
};
