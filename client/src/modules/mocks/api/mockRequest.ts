/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mockServer} from 'modules/mock-server/node';
import {DefaultBodyType, rest} from 'msw';

const mockPostRequest = function <Type extends DefaultBodyType>(url: string) {
  return {
    withSuccess: (responseData: Type) => {
      mockServer.use(
        rest.post(url, (_, res, ctx) => res.once(ctx.json(responseData)))
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.post(url, (_, res, ctx) =>
          res.once(
            ctx.status(statusCode),
            ctx.json({error: 'an error occured'})
          )
        )
      );
    },
    withNetworkError: () => {
      mockServer.use(rest.post(url, (_, res) => res.networkError('')));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        rest.post(url, (_, res, ctx) =>
          res.once(ctx.delay(1000), ctx.json(responseData))
        )
      );
    },
  };
};

const mockGetRequest = function <Type extends DefaultBodyType>(url: string) {
  return {
    withSuccess: (responseData: Type) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) => res.once(ctx.json(responseData)))
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) =>
          res.once(
            ctx.status(statusCode),
            ctx.json({error: 'an error occured'})
          )
        )
      );
    },
    withNetworkError: () => {
      mockServer.use(rest.get(url, (_, res) => res.networkError('')));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) =>
          res.once(ctx.delay(1000), ctx.json(responseData))
        )
      );
    },
  };
};

const mockDeleteRequest = function <Type extends DefaultBodyType>(url: string) {
  return {
    withSuccess: (responseData: Type) => {
      mockServer.use(
        rest.delete(url, (_, res, ctx) => res.once(ctx.json(responseData)))
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.delete(url, (_, res, ctx) =>
          res.once(
            ctx.status(statusCode),
            ctx.json({error: 'an error occured'})
          )
        )
      );
    },
    withNetworkError: () => {
      mockServer.use(rest.delete(url, (_, res) => res.networkError('')));
    },
    withDelay: (responseData: Type) => {
      mockServer.use(
        rest.delete(url, (_, res, ctx) =>
          res.once(ctx.delay(1000), ctx.json(responseData))
        )
      );
    },
  };
};

const mockXmlGetRequest = (url: string) => {
  return {
    withSuccess: (initialValue: string) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) => res.once(ctx.text(initialValue)))
      );
    },
    withServerError: (statusCode: number = 500) => {
      mockServer.use(
        rest.get(url, (_, res, ctx) =>
          res.once(ctx.status(statusCode), ctx.text('an error occured'))
        )
      );
    },
    withNetworkError: () => {
      mockServer.use(rest.get(url, (_, res) => res.networkError('')));
    },
    withDelay: (initialValue: string) => {
      mockServer.use(
        rest.post(url, (_, res, ctx) =>
          res.once(ctx.delay(1000), ctx.text(initialValue))
        )
      );
    },
  };
};

export {mockGetRequest, mockPostRequest, mockXmlGetRequest, mockDeleteRequest};
