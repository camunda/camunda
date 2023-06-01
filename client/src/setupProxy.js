/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const {createProxyMiddleware, responseInterceptor} = require('http-proxy-middleware');
const fetch = require('node-fetch');

module.exports = function (app) {
  app.use(
    ['/api', '/external/api', '/external/static'],
    createProxyMiddleware({
      target: 'http://localhost:8090',
    })
  );

  const filter = function (path, req) {
    if (path.includes('/sso-callback')) {
      return true;
    }

    if (req.headers.cookie) {
      return false;
    }

    return path === '/' || path.includes('/sso/auth0');
  };

  app.use(
    createProxyMiddleware(filter, {
      target: 'http://localhost:8090',
      selfHandleResponse: true,
      onProxyRes: responseInterceptor(async (responseBuffer, proxyRes, req) => {
        const showPlatformLogin =
          req.url === '/' && proxyRes.statusCode === 200 && !proxyRes.headers.cookie;

        if (showPlatformLogin) {
          // return original login page html file if the homepage does not redirect
          // 'random' string is used to avoid infinite proxying loop
          const response = await fetch('http://localhost:3000/random');
          return await response.text();
        }

        return responseBuffer;
      }),
    })
  );

  app.use(
    createProxyMiddleware('/ws/status', {
      target: 'http://localhost:8090',
      ws: true,
    })
  );
};
