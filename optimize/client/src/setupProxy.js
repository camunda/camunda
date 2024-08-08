/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const {legacyCreateProxyMiddleware, responseInterceptor} = require('http-proxy-middleware');
const fetch = require('node-fetch');

module.exports = function (app) {
  app.use(
    ['/api', '/external/api', '/external/static'],
    legacyCreateProxyMiddleware({
      target: 'http://localhost:8090',
    })
  );

  const filter = function (path, req) {
    if (path.includes('/sso-callback')) {
      return true;
    }

    if (req.headers.cookie?.includes('X-Optimize-Authorization')) {
      return false;
    }

    return path === '/' || path.includes('/sso/auth0');
  };

  app.use(
    legacyCreateProxyMiddleware(filter, {
      target: 'http://localhost:8090',
      selfHandleResponse: true,
      onProxyRes: responseInterceptor(async (responseBuffer, proxyRes, req) => {
        const showPlatformLogin =
          req.url === '/' &&
          proxyRes.statusCode === 200 &&
          !proxyRes.headers.cookie?.includes('X-Optimize-Authorization');

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
    legacyCreateProxyMiddleware('/ws/status', {
      target: 'http://localhost:8090',
      ws: true,
    })
  );
};
