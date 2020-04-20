/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const {createProxyMiddleware} = require('http-proxy-middleware');

module.exports = function (app) {
  app.use(
    ['/api', '/client-config.js'],
    createProxyMiddleware({
      target: `http://localhost:${process.env.IS_E2E ? '8081' : '8080'}`,
    })
  );
};
