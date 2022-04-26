/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const {createProxyMiddleware} = require('http-proxy-middleware');

module.exports = function (app) {
  app.use(['/api', '/external/api'], createProxyMiddleware({target: 'http://localhost:8090'}));
  app.use(
    createProxyMiddleware('/ws', {
      target: 'http://localhost:8090',
      ws: true,
    })
  );
};
