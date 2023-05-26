/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

// CRA does not support TS here, so this file needs to be written in pure JS

const {createProxyMiddleware} = require('http-proxy-middleware');

const BASENAME = process.env.BASENAME === undefined ? '' : process.env.BASENAME;

module.exports = function (app) {
  app.use(
    [`${BASENAME}/api`, `${BASENAME}/v1`, `${BASENAME}/client-config.js`],
    createProxyMiddleware({
      target: `http://localhost:${process.env.IS_E2E ? '8081' : '8080'}`,
    }),
  );
};
