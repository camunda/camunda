/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const {createProxyMiddleware} = require('http-proxy-middleware');

const BASENAME = process.env.BASENAME === undefined ? '' : process.env.BASENAME;

module.exports = function (app) {
  app.use(
    BASENAME,
    createProxyMiddleware({
      target: `http://localhost:${process.env.IS_E2E ? '8081' : '8080'}`,
      pathFilter: ['/client-config.js', '/api'],
    }),
  );
};
