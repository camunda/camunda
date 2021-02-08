/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const {createProxyMiddleware} = require('http-proxy-middleware');

module.exports = function (app) {
  app.use(['/api', '/external/api'], createProxyMiddleware({target: 'http://localhost:8090'}));
  app.use(
    '/ws',
    createProxyMiddleware({
      target: 'ws://localhost:8090',
      ws: true,
    })
  );
};
